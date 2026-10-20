"""Step 6 -- per-frame latency of predict() (crop + resize + tensor + both ONNX models + fusion).

Warm-up iterations are excluded. Reports mean and p95 in ms, for:
  * Python, ONNX Runtime default threads and intra_op_threads=1 (closer to a low-end phone budget)
  * Python preprocessing alone (crop + canonical resize + to_tensor, both models)
  * Java (via `Cli bench`, default ONNX Runtime threads), if the Java module is built
Frames: upstream sample image_T1 (480x640) and a 1080x1440 upscaled copy (bigger crop to resize).

Usage: .venv/bin/python minifasnet/eval/bench_latency.py [--iters 300] [--warmup 30]
"""
import argparse
import functools
import os
import platform
import shutil
import subprocess
import sys
import tempfile
import time
from typing import Callable, List, Optional, Tuple

import cv2
import numpy as np
import onnxruntime as ort

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(MOD, ".."))
sys.path.insert(0, os.path.join(MOD, "python"))
from minifasnet_pad import MiniFASNetPAD, crop_patch, to_tensor  # noqa: E402

SAMPLE = os.path.join(ROOT, "minifasnet-study", "Silent-Face-Anti-Spoofing", "images", "sample", "image_T1.jpg")
SAMPLE_BOX = (105.0, 147.0, 207.0, 214.0)  # upstream RetinaFace box for image_T1
JAVA_DIR = os.path.join(MOD, "java")


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


def stats(ms: List[float]) -> Tuple[float, float]:
    s = np.sort(np.asarray(ms))
    return float(s.mean()), float(s[int(np.ceil(0.95 * len(s))) - 1])


def bench(fn: Callable[[], object], warmup: int, iters: int) -> Tuple[float, float]:
    for _ in range(warmup):
        fn()
    out = []
    for _ in range(iters):
        t0 = time.perf_counter()
        fn()
        out.append((time.perf_counter() - t0) * 1e3)
    return stats(out)


def preprocess_only(pad: MiniFASNetPAD, img: np.ndarray, box: Tuple[float, ...]) -> list:
    return [to_tensor(crop_patch(img, box, m.scale, m.width, m.height)) for m in pad.models]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--iters", type=int, default=300)
    ap.add_argument("--warmup", type=int, default=30)
    a = ap.parse_args()
    if a.iters < 1 or a.warmup < 0:
        ap.error("--iters must be >= 1 and --warmup >= 0")

    t1 = cv2.imread(SAMPLE)
    if t1 is None:
        print(f"sample image not found: {SAMPLE} (clone the upstream repo into minifasnet-study/)", file=sys.stderr)
        return 2
    frames = {"480x640": (t1, SAMPLE_BOX),
              "1080x1440": (cv2.resize(t1, (1080, 1440)), tuple(v * 2.25 for v in SAMPLE_BOX))}
    pad_default = MiniFASNetPAD()
    pad_1t = MiniFASNetPAD(intra_op_threads=1)
    java, cp = find_java(), java_classpath()

    print(f"machine: {platform.machine()} | {platform.platform()} | onnxruntime {ort.__version__} | "
          f"warmup={a.warmup} iters={a.iters}")
    print("| frame | variant | mean ms | p95 ms |\n|---|---|---|---|")
    for name, (img, box) in frames.items():
        rows = [("Python predict, ORT default threads", functools.partial(pad_default.predict, img, box)),
                ("Python predict, ORT 1 thread", functools.partial(pad_1t.predict, img, box)),
                ("Python preprocessing only (2 crops)", functools.partial(preprocess_only, pad_default, img, box))]
        for label, fn in rows:
            mean, p95 = bench(fn, a.warmup, a.iters)
            print(f"| {name} | {label} | {mean:.3f} | {p95:.3f} |")
        if java is None or cp is None:
            print(f"| {name} | Java | skipped: build minifasnet/java first (see README) | |")
            continue
        for threads in (0, 1):
            with tempfile.TemporaryDirectory() as tmp:
                p = os.path.join(tmp, "f.bgr")
                np.ascontiguousarray(img).tofile(p)
                h, w = img.shape[:2]
                out = subprocess.run([java, "-cp", cp, "org.minifasnet.Cli", "bench", os.path.join(MOD, "models"),
                                      p, str(w), str(h)] + [str(v) for v in box]
                                     + [str(a.warmup), str(a.iters), str(threads)],
                                     capture_output=True, text=True, check=True).stdout
            kv = dict(x.split("=", 1) for x in out.split() if "=" in x)
            label = "Java predict, ORT default threads" if threads == 0 else "Java predict, ORT 1 thread"
            print(f"| {name} | {label} | {float(kv['mean_ms']):.3f} | {float(kv['p95_ms']):.3f} |")
    return 0


if __name__ == "__main__":
    sys.exit(main())
