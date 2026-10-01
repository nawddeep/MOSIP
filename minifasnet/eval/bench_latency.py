"""Step 6 -- per-frame latency of predict() (crop + resize + tensor + both ONNX models + fusion).

Warm-up iterations are excluded. Reports mean and p95 in ms, for:
  * Python, ONNX Runtime default threads and intra_op_num_threads=1 (closer to a low-end phone budget)
  * Python preprocessing alone (crop + canonical resize + to_tensor, both models)
  * Java (via `Cli bench`, default ONNX Runtime session options), if the Java module is built
Frames: upstream sample image_T1 (480x640) and a 1440x1080-ish upscaled copy (bigger crop to resize).

Usage: .venv/bin/python minifasnet/eval/bench_latency.py [--iters 300] [--warmup 30]
"""
import argparse
import os
import platform
import subprocess
import sys
import tempfile
import time

import cv2
import numpy as np
import onnxruntime as ort

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(MOD, ".."))
sys.path.insert(0, os.path.join(MOD, "python"))
from minifasnet_pad import MiniFASNetPAD, crop_patch, to_tensor  # noqa: E402

SAMPLE = os.path.join(ROOT, "minifasnet-study", "Silent-Face-Anti-Spoofing", "images", "sample", "image_T1.jpg")
JAVA_BIN = os.environ.get("JAVA_BIN", "/opt/homebrew/opt/openjdk@17/bin/java")


def stats(ms):
    s = np.sort(np.asarray(ms))
    return s.mean(), s[int(np.ceil(0.95 * len(s))) - 1]


def bench(fn, warmup, iters):
    for _ in range(warmup):
        fn()
    out = []
    for _ in range(iters):
        t0 = time.perf_counter()
        fn()
        out.append((time.perf_counter() - t0) * 1e3)
    return stats(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--iters", type=int, default=300)
    ap.add_argument("--warmup", type=int, default=30)
    a = ap.parse_args()

    t1 = cv2.imread(SAMPLE)
    frames = {"480x640": (t1, (105, 147, 207, 214)),
              "1080x1440": (cv2.resize(t1, (1080, 1440)), tuple(v * 2.25 for v in (105, 147, 207, 214)))}
    pad_default = MiniFASNetPAD()
    pad_1t = MiniFASNetPAD(intra_op_threads=1)

    print(f"machine: {platform.machine()} {platform.processor()} | {platform.platform()} | "
          f"onnxruntime {ort.__version__} | warmup={a.warmup} iters={a.iters}")
    print("| frame | variant | mean ms | p95 ms |\n|---|---|---|---|")
    for name, (img, box) in frames.items():
        rows = [("Python predict, ORT default threads", lambda: pad_default.predict(img, box)),
                ("Python predict, ORT 1 thread", lambda: pad_1t.predict(img, box)),
                ("Python preprocessing only (2 crops)",
                 lambda: [to_tensor(crop_patch(img, box, s, w, h)) for _, s, w, h, _ in pad_default.models])]
        for label, fn in rows:
            mean, p95 = bench(fn, a.warmup, a.iters)
            print(f"| {name} | {label} | {mean:.3f} | {p95:.3f} |")
        cpf = os.path.join(MOD, "java", "target", "cp.txt")
        if os.path.exists(cpf):
            cp = os.path.join(MOD, "java", "target", "classes") + ":" + open(cpf).read().strip()
            with tempfile.TemporaryDirectory() as tmp:
                p = os.path.join(tmp, "f.bgr")
                np.ascontiguousarray(img).tofile(p)
                h, w = img.shape[:2]
                out = subprocess.run([JAVA_BIN, "-cp", cp, "org.minifasnet.Cli", "bench", os.path.join(MOD, "models"),
                                      p, str(w), str(h)] + [str(v) for v in box] + [str(a.warmup), str(a.iters)],
                                     capture_output=True, text=True, check=True).stdout
            kv = dict(x.split("=") for x in out.split() if "=" in x)
            print(f"| {name} | Java predict, ORT default | {float(kv['mean_ms']):.3f} | {float(kv['p95_ms']):.3f} |")


if __name__ == "__main__":
    main()
