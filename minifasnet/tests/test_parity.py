"""Step 4 -- three-way parity: upstream PyTorch  vs  Python ONNX (MiniFASNetPAD)  vs  Java wrapper.

All three get the SAME pixels and the SAME face box. Edge-case frames are generated in memory from the
upstream sample images (nothing image-like is written into the repo; raw frames go to a temp dir).

Run:  .venv/bin/python -m pytest minifasnet/tests -q          (pytest)
      .venv/bin/python minifasnet/tests/test_parity.py         (also writes tests/out/parity_report.md)
Requires: the upstream clone in minifasnet-study/ and a built Java module (mvn package + target/cp.txt).
"""
import os
import subprocess
import sys
import tempfile
import warnings

import cv2
import numpy as np
import pytest

warnings.filterwarnings("ignore")
HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(MOD, ".."))
REPO = os.path.join(ROOT, "minifasnet-study", "Silent-Face-Anti-Spoofing")
JAVA_DIR = os.path.join(MOD, "java")
JAVA_BIN = os.environ.get("JAVA_BIN", "/opt/homebrew/opt/openjdk@17/bin/java")
TARGET = 1e-3  # max |real_score diff| vs upstream PyTorch (upstream uses platform-dependent cv2.resize)
EXACT = 1e-6   # Python ONNX vs Java: same canonical integer preprocessing -> must be (near) identical
sys.path.insert(0, os.path.join(MOD, "python"))
from minifasnet_pad import MiniFASNetPAD, PADError, resize_bilinear_u8  # noqa: E402


# ---------------- reference: the original upstream PyTorch code, untouched ----------------
class Upstream:
    def __init__(self):
        cwd = os.getcwd()
        os.chdir(REPO)  # upstream loads its detector with relative paths
        sys.path.insert(0, REPO)
        from src.anti_spoof_predict import AntiSpoofPredict
        from src.generate_patches import CropImage
        from src.utility import parse_model_name
        self.pred, self.crop, self.parse = AntiSpoofPredict(0), CropImage(), parse_model_name
        os.chdir(cwd)
        self.mdir = os.path.join(REPO, "resources", "anti_spoof_models")

    def bbox(self, img):
        return self.pred.get_bbox(img)  # upstream RetinaFace detector -- only used to pick natural boxes

    def real_score(self, img, box):
        total = np.zeros((1, 3))
        for m in sorted(os.listdir(self.mdir)):
            h, w, _, scale = self.parse(m)
            patch = self.crop.crop(org_img=img, bbox=box, scale=scale, out_w=w, out_h=h, crop=True)
            total += self.pred.predict(patch, os.path.join(self.mdir, m))
        return float(total[0][1] / 2)


# ---------------- test set ----------------
def build_cases(up):
    s = {n: cv2.imread(os.path.join(REPO, "images", "sample", n)) for n in
         ("image_F1.jpg", "image_F2.jpg", "image_T1.jpg")}
    cases = []  # (id, frame, box, expect_error)
    for n, img in s.items():
        cases.append((n, img, tuple(up.bbox(img)), False))
    t1, f1 = s["image_T1.jpg"], s["image_F1.jpg"]
    bt = tuple(up.bbox(t1))
    rng = np.random.default_rng(0)
    cases += [
        ("T1_dark", (t1 * 0.25).astype(np.uint8), bt, False),
        ("T1_overexposed", cv2.add(t1, np.full_like(t1, 110)), bt, False),
        ("T1_noise", np.clip(t1 + rng.normal(0, 12, t1.shape), 0, 255).astype(np.uint8), bt, False),
        ("T1_blur", cv2.GaussianBlur(t1, (9, 9), 3), bt, False),
        ("T1_half_res", cv2.resize(t1, (240, 320)), tuple(v / 2 for v in bt), False),
        ("T1_square_crop", np.ascontiguousarray(t1[80:560]), (bt[0], bt[1] - 80, bt[2], bt[3]), False),
        ("T1_odd_size", np.ascontiguousarray(t1[3:280, 5:406]), (100, 141, 180, 130), False),
        ("T1_corner_box", t1, (0, 0, 100, 120), False),
        ("T1_bottom_right", t1, (400, 500, 100, 120), False),
        ("T1_partly_outside", t1, (-20, -20, 120, 120), False),
        ("F1_huge_box", f1, (10, 10, 460, 620), False),
        ("F1_fractional", f1, (150.5, 200.25, 180.5, 190.75), False),
        ("T1_min_face_33", t1, (180, 200, 33, 33), False),
        ("gray_frame", np.full((640, 480, 3), 128, np.uint8), (150, 200, 150, 180), False),
        ("err_tiny_face", t1, (10, 10, 10, 10), True),
        ("err_outside", t1, (5000, 5000, 60, 60), True),
        ("err_zero_size", t1, (10, 10, 0, 50), True),
    ]
    return cases


def java_cp():
    return os.path.join(JAVA_DIR, "target", "classes") + ":" + \
        open(os.path.join(JAVA_DIR, "target", "cp.txt")).read().strip()


def run_java(cases, tmp):
    lines = []
    for i, (cid, img, box, _) in enumerate(cases):
        fn = f"f{i}.bgr"
        np.ascontiguousarray(img).tofile(os.path.join(tmp, fn))
        h, w = img.shape[:2]
        lines.append("\t".join([cid, fn, str(w), str(h)] + [repr(float(v)) for v in box]))
    with open(os.path.join(tmp, "manifest.tsv"), "w") as f:
        f.write("\n".join(lines) + "\n")
    out = os.path.join(tmp, "java_out.tsv")
    subprocess.run([JAVA_BIN, "-cp", java_cp(), "org.minifasnet.Cli", "batch", os.path.join(MOD, "models"),
                    os.path.join(tmp, "manifest.tsv"), out], check=True)
    res = {}
    for line in open(out):
        f = line.rstrip("\n").split("\t")
        res[f[0]] = (f[1], float(f[2]) if f[1] == "OK" else None)
    return res


def compute():
    up, pad = Upstream(), MiniFASNetPAD()
    cases = build_cases(up)
    with tempfile.TemporaryDirectory() as tmp:
        java = run_java(cases, tmp)
    rows = []
    for cid, img, box, expect_err in cases:
        try:
            py = pad.predict(img, box).real_score
            py_err = None
        except PADError as e:
            py, py_err = None, type(e).__name__
        pt = None if expect_err else up.real_score(img, box)
        jstat, jv = java[cid]
        rows.append(dict(id=cid, box=box, expect_err=expect_err, pytorch=pt, onnx=py, onnx_err=py_err,
                         java=jv, java_err=None if jstat == "OK" else jstat))
    return rows


@pytest.fixture(scope="module")
def rows():
    return compute()


def test_valid_cases_match(rows):
    for r in (r for r in rows if not r["expect_err"]):
        assert r["onnx_err"] is None and r["java_err"] is None, r
        assert abs(r["pytorch"] - r["onnx"]) < TARGET, r
        assert abs(r["onnx"] - r["java"]) < EXACT, r
        assert abs(r["pytorch"] - r["java"]) < TARGET, r


def test_labels_agree(rows):
    for r in (r for r in rows if not r["expect_err"]):
        assert (r["pytorch"] > 0.5) == (r["onnx"] > 0.5) == (r["java"] > 0.5) or \
            abs(r["pytorch"] - 0.5) < TARGET, r


def test_error_cases_rejected_by_both(rows):
    mapping = {"InvalidBoxError": "INVALID_BOX", "FaceTooSmallError": "FACE_TOO_SMALL",
               "InvalidFrameError": "INVALID_FRAME"}
    for r in (r for r in rows if r["expect_err"]):
        assert r["onnx_err"] is not None and r["java_err"] == mapping[r["onnx_err"]], r


RESIZE_SIZES = [(240, 248), (91, 91), (133, 133), (160, 160), (100, 120), (81, 79), (60, 70), (271, 325),
                (480, 496), (80, 80), (33, 40), (700, 699), (2, 2), (1000, 90)]


def resize_pixel_parity():
    """Canonical resize: Python vs Java must be bit-identical. Also measures diff vs this machine's cv2.resize."""
    rng = np.random.default_rng(3)
    out = []
    with tempfile.TemporaryDirectory() as tmp:
        for w, h in RESIZE_SIZES:
            p = rng.integers(0, 256, (h, w, 3), dtype=np.uint8)
            p.tofile(os.path.join(tmp, "in.bgr"))
            subprocess.run([JAVA_BIN, "-cp", java_cp(), "org.minifasnet.Cli", "resize", os.path.join(tmp, "in.bgr"),
                            str(w), str(h), "80", "80", os.path.join(tmp, "out.bgr")], check=True)
            j = np.fromfile(os.path.join(tmp, "out.bgr"), np.uint8).reshape(80, 80, 3)
            py = resize_bilinear_u8(p, 80, 80)
            cvd = np.abs(cv2.resize(p, (80, 80)).astype(int) - py.astype(int))
            out.append(((w, h), int(np.abs(j.astype(int) - py.astype(int)).max()), int(cvd.max()),
                        float((cvd > 0).mean())))
    return out


def test_resize_bit_identical_python_java():
    for size, java_vs_py, _, _ in resize_pixel_parity():
        assert java_vs_py == 0, size


def write_report(rows, path):
    valid = [r for r in rows if not r["expect_err"]]
    d = lambda a, b: max(abs(r[a] - r[b]) for r in valid)
    lines = ["# Parity report (Step 4)", "",
             f"Target: max |real_score diff| < {TARGET}. Cases: {len(valid)} valid + {len(rows) - len(valid)} error.", "",
             f"- PyTorch vs Python ONNX: **{d('pytorch', 'onnx'):.3e}**",
             f"- Python ONNX vs Java: **{d('onnx', 'java'):.3e}**",
             f"- PyTorch vs Java: **{d('pytorch', 'java'):.3e}**", "",
             "Why PyTorch-vs-ours is not ~0: upstream uses cv2.resize, whose output depends on the OpenCV build",
             "(this machine: arm64 build with Carotene/KleidiCV HAL). We use one canonical integer resize in both",
             "Python and Java, so Python-vs-Java is exact on every platform.", "",
             "## Canonical resize, pixel level (random 8-bit patches -> 80x80)", "",
             "| src size | Java vs Python max px diff | cv2 vs canonical max px diff | cv2 differing px fraction |",
             "|---|---|---|---|"] + [f"| {s} | {a} | {b} | {c:.3f} |" for s, a, b, c in resize_pixel_parity()] + [
             "", "## Scores", "",
             "| case | box | PyTorch | Python ONNX | Java | max diff |", "|---|---|---|---|---|---|"]
    for r in rows:
        if r["expect_err"]:
            lines.append(f"| {r['id']} | {r['box']} | (no validation) | {r['onnx_err']} | {r['java_err']} | - |")
        else:
            md = max(abs(r["pytorch"] - r["onnx"]), abs(r["onnx"] - r["java"]), abs(r["pytorch"] - r["java"]))
            lines.append(f"| {r['id']} | {tuple(round(float(v), 2) for v in r['box'])} | {r['pytorch']:.6f} | "
                         f"{r['onnx']:.6f} | {r['java']:.6f} | {md:.2e} |")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w").write("\n".join(lines) + "\n")
    return lines


if __name__ == "__main__":
    print("\n".join(write_report(compute(), os.path.join(HERE, "out", "parity_report.md"))))
