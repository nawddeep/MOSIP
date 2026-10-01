"""Step 4 -- three-way parity: upstream PyTorch  vs  Python ONNX (MiniFASNetPAD)  vs  Java wrapper.

All three get the SAME pixels and the SAME face box. Edge-case frames are generated in memory from the
upstream sample images (nothing image-like is written into the repo; raw frames go to a temp dir).

Run:  .venv/bin/python -m pytest                          (all tests)
      .venv/bin/python minifasnet/tests/test_parity.py     (also writes tests/out/parity_report.md)
Needs: the upstream clone in minifasnet-study/ and a built Java module (cd minifasnet/java && mvn package).
If either is missing, these tests are SKIPPED with the reason (pytest -ra prints it).
"""
import os
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple

import cv2
import numpy as np
import pytest

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(MOD, ".."))
REPO = os.path.join(ROOT, "minifasnet-study", "Silent-Face-Anti-Spoofing")
JAVA_DIR = os.path.join(MOD, "java")
TARGET = 1e-3  # max |real_score diff| vs upstream PyTorch (upstream uses platform-dependent cv2.resize)
EXACT = 1e-6  # Python ONNX vs Java: same canonical integer preprocessing -> must be (near) identical
sys.path.insert(0, os.path.join(MOD, "python"))
from minifasnet_pad import MiniFASNetPAD, PADError, resize_bilinear_u8  # noqa: E402

Box = Tuple[float, float, float, float]


def find_java() -> Optional[str]:
    """JAVA_BIN env > JAVA_HOME/bin/java > Homebrew openjdk@17 > java on PATH."""
    candidates = [os.environ.get("JAVA_BIN"),
                  os.path.join(os.environ["JAVA_HOME"], "bin", "java") if os.environ.get("JAVA_HOME") else None,
                  "/opt/homebrew/opt/openjdk@17/bin/java", shutil.which("java")]
    for c in candidates:
        if c and os.path.isfile(c) and os.access(c, os.X_OK):
            return c
    return None


def java_classpath() -> Optional[str]:
    cpf = os.path.join(JAVA_DIR, "target", "cp.txt")
    classes = os.path.join(JAVA_DIR, "target", "classes")
    if not (os.path.isfile(cpf) and os.path.isdir(classes)):
        return None
    with open(cpf) as fh:
        return classes + os.pathsep + fh.read().strip()


def missing_prerequisites() -> Optional[str]:
    if not os.path.isdir(os.path.join(REPO, "src")):
        return f"upstream clone missing: {REPO}"
    if find_java() is None:
        return "no Java runtime found (set JAVA_BIN or JAVA_HOME)"
    if java_classpath() is None:
        return "Java module not built (cd minifasnet/java && mvn package)"
    return None


def java_cmd(*args: str) -> List[str]:
    java, cp = find_java(), java_classpath()
    assert java is not None and cp is not None, missing_prerequisites()
    return [java, "-cp", cp, "org.minifasnet.Cli", *args]


# ---------------- reference: the original upstream PyTorch code, untouched ----------------
class Upstream:
    def __init__(self) -> None:
        cwd = os.getcwd()
        os.chdir(REPO)  # upstream loads its detector with relative paths
        try:
            sys.path.insert(0, REPO)
            from src.anti_spoof_predict import AntiSpoofPredict
            from src.generate_patches import CropImage
            from src.utility import parse_model_name

            self.pred, self.crop, self.parse = AntiSpoofPredict(0), CropImage(), parse_model_name
        finally:
            os.chdir(cwd)
        self.mdir = os.path.join(REPO, "resources", "anti_spoof_models")

    def bbox(self, img: np.ndarray) -> Box:
        x, y, w, h = self.pred.get_bbox(img)  # upstream RetinaFace detector -- only used to pick natural boxes
        return float(x), float(y), float(w), float(h)

    def real_score(self, img: np.ndarray, box: Box) -> float:
        total = np.zeros((1, 3))
        for m in sorted(os.listdir(self.mdir)):
            h, w, _, scale = self.parse(m)
            patch = self.crop.crop(org_img=img, bbox=box, scale=scale, out_w=w, out_h=h, crop=True)
            total += self.pred.predict(patch, os.path.join(self.mdir, m))
        return float(total[0][1] / 2)


# ---------------- test set ----------------
@dataclass
class Case:
    id: str
    frame: np.ndarray
    box: Box
    expect_error: bool


@dataclass
class Row:
    id: str
    box: Box
    expect_error: bool
    pytorch: Optional[float]
    onnx: Optional[float]
    onnx_error: Optional[str]
    java: Optional[float]
    java_error: Optional[str]


def load_sample(name: str) -> np.ndarray:
    img = cv2.imread(os.path.join(REPO, "images", "sample", name))
    if img is None:
        raise FileNotFoundError(f"upstream sample image missing: {name}")
    return img


def build_cases(up: Upstream) -> List[Case]:
    s = {n: load_sample(n) for n in ("image_F1.jpg", "image_F2.jpg", "image_T1.jpg")}
    cases = [Case(n, img, up.bbox(img), False) for n, img in s.items()]
    t1, f1 = s["image_T1.jpg"], s["image_F1.jpg"]
    bt = up.bbox(t1)
    rng = np.random.default_rng(0)
    half: Box = (bt[0] / 2, bt[1] / 2, bt[2] / 2, bt[3] / 2)
    cases += [
        Case("T1_dark", (t1 * 0.25).astype(np.uint8), bt, False),
        Case("T1_overexposed", cv2.add(t1, np.full_like(t1, 110)), bt, False),
        Case("T1_noise", np.clip(t1 + rng.normal(0, 12, t1.shape), 0, 255).astype(np.uint8), bt, False),
        Case("T1_blur", cv2.GaussianBlur(t1, (9, 9), 3), bt, False),
        Case("T1_half_res", cv2.resize(t1, (240, 320)), half, False),
        Case("T1_square_crop", np.ascontiguousarray(t1[80:560]), (bt[0], bt[1] - 80, bt[2], bt[3]), False),
        Case("T1_odd_size", np.ascontiguousarray(t1[3:280, 5:406]), (100, 141, 180, 130), False),
        Case("T1_corner_box", t1, (0, 0, 100, 120), False),
        Case("T1_bottom_right", t1, (400, 500, 100, 120), False),
        Case("T1_partly_outside", t1, (-20, -20, 120, 120), False),
        Case("F1_huge_box", f1, (10, 10, 460, 620), False),
        Case("F1_fractional", f1, (150.5, 200.25, 180.5, 190.75), False),
        Case("T1_min_face_33", t1, (180, 200, 33, 33), False),
        Case("gray_frame", np.full((640, 480, 3), 128, np.uint8), (150, 200, 150, 180), False),
        Case("err_tiny_face", t1, (10, 10, 10, 10), True),
        Case("err_outside", t1, (5000, 5000, 60, 60), True),
        Case("err_zero_size", t1, (10, 10, 0, 50), True),
    ]
    return cases


def run_java(cases: List[Case], tmp: str) -> Dict[str, Tuple[str, Optional[float]]]:
    lines = []
    for i, c in enumerate(cases):
        fn = f"f{i}.bgr"
        np.ascontiguousarray(c.frame).tofile(os.path.join(tmp, fn))
        h, w = c.frame.shape[:2]
        lines.append("\t".join([c.id, fn, str(w), str(h)] + [repr(float(v)) for v in c.box]))
    manifest, out = os.path.join(tmp, "manifest.tsv"), os.path.join(tmp, "java_out.tsv")
    with open(manifest, "w") as fh:
        fh.write("\n".join(lines) + "\n")
    subprocess.run(java_cmd("batch", os.path.join(MOD, "models"), manifest, out), check=True)
    res: Dict[str, Tuple[str, Optional[float]]] = {}
    with open(out) as fh:
        for line in fh:
            f = line.rstrip("\n").split("\t")
            res[f[0]] = (f[1], float(f[2]) if f[1] == "OK" else None)
    return res


def compute() -> List[Row]:
    up, pad = Upstream(), MiniFASNetPAD()
    cases = build_cases(up)
    with tempfile.TemporaryDirectory() as tmp:
        java = run_java(cases, tmp)
    rows = []
    for c in cases:
        onnx_score: Optional[float] = None
        onnx_error: Optional[str] = None
        try:
            onnx_score = pad.predict(c.frame, c.box).real_score
        except PADError as e:
            onnx_error = type(e).__name__
        pytorch = None if c.expect_error else up.real_score(c.frame, c.box)
        jstat, jscore = java[c.id]
        rows.append(Row(c.id, c.box, c.expect_error, pytorch, onnx_score, onnx_error, jscore,
                        None if jstat == "OK" else jstat))
    return rows


RESIZE_SIZES = [(240, 248), (91, 91), (133, 133), (160, 160), (100, 120), (81, 79), (60, 70), (271, 325),
                (480, 496), (80, 80), (33, 40), (700, 699), (2, 2), (1000, 90)]


def resize_pixel_parity() -> List[Tuple[Tuple[int, int], int, int, float]]:
    """Canonical resize: Python vs Java must be bit-identical. Also measures diff vs this machine's cv2.resize."""
    rng = np.random.default_rng(3)
    out = []
    with tempfile.TemporaryDirectory() as tmp:
        src, dst = os.path.join(tmp, "in.bgr"), os.path.join(tmp, "out.bgr")
        for w, h in RESIZE_SIZES:
            p = rng.integers(0, 256, (h, w, 3), dtype=np.uint8)
            p.tofile(src)
            subprocess.run(java_cmd("resize", src, str(w), str(h), "80", "80", dst), check=True)
            j = np.fromfile(dst, np.uint8).reshape(80, 80, 3)
            py = resize_bilinear_u8(p, 80, 80)
            cvd = np.abs(cv2.resize(p, (80, 80)).astype(int) - py.astype(int))
            out.append(((w, h), int(np.abs(j.astype(int) - py.astype(int)).max()), int(cvd.max()),
                        float((cvd > 0).mean())))
    return out


# ---------------- pytest ----------------
pytestmark = pytest.mark.skipif(missing_prerequisites() is not None, reason=str(missing_prerequisites()))


@pytest.fixture(scope="module")
def rows() -> List[Row]:
    return compute()


def test_valid_cases_match(rows: List[Row]) -> None:
    for r in (r for r in rows if not r.expect_error):
        assert r.onnx_error is None and r.java_error is None, r
        assert r.pytorch is not None and r.onnx is not None and r.java is not None, r
        assert abs(r.pytorch - r.onnx) < TARGET, r
        assert abs(r.onnx - r.java) < EXACT, r
        assert abs(r.pytorch - r.java) < TARGET, r


def test_labels_agree(rows: List[Row]) -> None:
    for r in (r for r in rows if not r.expect_error):
        assert r.pytorch is not None and r.onnx is not None and r.java is not None, r
        same = (r.pytorch > 0.5) == (r.onnx > 0.5) == (r.java > 0.5)
        assert same or abs(r.pytorch - 0.5) < TARGET, r


def test_error_cases_rejected_by_both(rows: List[Row]) -> None:
    mapping = {"InvalidBoxError": "INVALID_BOX", "FaceTooSmallError": "FACE_TOO_SMALL",
               "InvalidFrameError": "INVALID_FRAME"}
    for r in (r for r in rows if r.expect_error):
        assert r.onnx_error is not None and r.java_error == mapping[r.onnx_error], r


def test_resize_bit_identical_python_java() -> None:
    for size, java_vs_py, _, _ in resize_pixel_parity():
        assert java_vs_py == 0, size


# ---------------- report ----------------
def write_report(rows: List[Row], path: str) -> List[str]:
    valid = [(r.pytorch, r.onnx, r.java) for r in rows
             if not r.expect_error and r.pytorch is not None and r.onnx is not None and r.java is not None]
    pt_onnx = max(abs(a - b) for a, b, _ in valid)
    onnx_java = max(abs(b - c) for _, b, c in valid)
    pt_java = max(abs(a - c) for a, _, c in valid)
    lines = ["# Parity report (Step 4)", "",
             f"Targets: |PyTorch - ours| < {TARGET}; |Python ONNX - Java| < {EXACT}. "
             f"Cases: {len(valid)} valid + {len(rows) - len(valid)} error.", "",
             f"- PyTorch vs Python ONNX: **{pt_onnx:.3e}**",
             f"- Python ONNX vs Java: **{onnx_java:.3e}**",
             f"- PyTorch vs Java: **{pt_java:.3e}**", "",
             "Why PyTorch-vs-ours is not ~0: upstream uses cv2.resize, whose output depends on the OpenCV build",
             "(this machine: arm64 build with Carotene/KleidiCV HAL). We use one canonical integer resize in both",
             "Python and Java, so Python-vs-Java is exact on every platform.", "",
             "## Canonical resize, pixel level (random 8-bit patches -> 80x80)", "",
             "| src size | Java vs Python max px diff | cv2 vs canonical max px diff | cv2 differing px fraction |",
             "|---|---|---|---|"]
    lines += [f"| {s} | {a} | {b} | {c:.3f} |" for s, a, b, c in resize_pixel_parity()]
    lines += ["", "## Scores", "",
              "| case | box | PyTorch | Python ONNX | Java | max diff |", "|---|---|---|---|---|---|"]
    for r in rows:
        box = tuple(round(float(v), 2) for v in r.box)
        if r.expect_error or r.pytorch is None or r.onnx is None or r.java is None:
            lines.append(f"| {r.id} | {box} | (no validation) | {r.onnx_error} | {r.java_error} | - |")
        else:
            md = max(abs(r.pytorch - r.onnx), abs(r.onnx - r.java), abs(r.pytorch - r.java))
            lines.append(f"| {r.id} | {box} | {r.pytorch:.6f} | {r.onnx:.6f} | {r.java:.6f} | {md:.2e} |")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        fh.write("\n".join(lines) + "\n")
    return lines


if __name__ == "__main__":
    reason = missing_prerequisites()
    if reason:
        sys.exit(f"cannot run parity: {reason}")
    print("\n".join(write_report(compute(), os.path.join(HERE, "out", "parity_report.md"))))
