# minifasnet — self-contained MiniFASNet passive-liveness module

**Scope: one thing.** Given a BGR frame and a face box supplied by the caller, return a passive real/spoof score.
No face detection (except a dev-only helper), no device code, no UI, no active challenges, no Registration Client code.

## Layout
```
minifasnet/
  models/   2 ONNX files + MODEL_CARD.md (source, license, I/O spec, SHA-256)
  python/   minifasnet_pad.py   reference implementation (SOURCE OF TRUTH)
            dev_box.py          DEV-ONLY Haar face box helper (not for production)
            export_onnx.py      regenerates models/*.onnx from the upstream .pth
  java/     Maven module org.minifasnet (MiniFasNetPad, ImageOps, Cli) + JUnit tests
  tests/    test_unit.py, test_eval.py, test_parity.py (3-way parity), out/parity_report.md
  eval/     evaluate.py (APCER/BPCER), bench_latency.py
```

## Status
| Step | What | Status | Evidence |
|---|---|---|---|
| 1 | Layout, ONNX export, model card | done | ONNX vs PyTorch logits <= 2.6e-6 |
| 2 | Python reference `MiniFASNetPAD` | done | vs upstream pipeline 4.7e-7 (same box) |
| 3 | Java wrapper | done | vs Python 4.3e-13 |
| 4 | Parity tests | done | 17 valid + 3 error cases; `tests/out/parity_report.md` |
| 5 | Evaluation script | done | tested on synthetic set; metric unit tests |
| 6 | Latency | done | table below |

## Setup
```
python3.11 -m venv .venv
.venv/bin/pip install numpy "opencv-python<5" onnxruntime onnx torch pillow pytest
cd minifasnet/java && mvn -q package dependency:build-classpath -Dmdep.outputFile=target/cp.txt   # JDK 11+, Maven
```
`torch` and the upstream clone (`../minifasnet-study/Silent-Face-Anti-Spoofing`) are needed only for export and PyTorch
parity, not at runtime. Runtime needs: numpy + onnxruntime (+ opencv only for the dev helper and eval video reading).

## API
Python:
```python
from minifasnet_pad import MiniFASNetPAD
pad = MiniFASNetPAD(model_dir="minifasnet/models", min_face_size=32, intra_op_threads=0)
r = pad.predict(frame_bgr_uint8_HxWx3, (x, y, w, h))
r.real_score        # 0..1, mean class-1 probability of both models  -> threshold this
r.label             # "real"/"fake" = argmax of summed softmax (upstream rule)
r.label_confidence  # upstream's printed score (confidence of the winning class)
r.per_model_scores  # per-model softmax
```
Java:
```java
try (MiniFasNetPad pad = new MiniFasNetPad("/path/to/models", 32)) {
    PadResult r = pad.predict(bgrBytes, width, height, new double[] {x, y, w, h});
}
```
Errors are typed: Python `InvalidFrameError / InvalidBoxError / FaceTooSmallError / ModelLoadError` (all `PADError`);
Java `PadException.getCode()` = `INVALID_FRAME | INVALID_BOX | FACE_TOO_SMALL | MODEL_LOAD | INFERENCE`.

## Preprocessing spec (must be reproduced exactly by any port)
1. Box `(x, y, w, h)` from the caller. Validation: finite, w,h > 0, center inside the frame, min(w,h) >= min_face_size.
2. Per model, crop window = box scaled by the model's scale (2.7 or 4.0, parsed from the file name) around its center;
   scale shrunk to fit the frame; window **shifted** (never padded) inside the frame; coordinates truncated with `int()`;
   end coordinates inclusive. (Port of upstream `CropImage._get_new_box`.)
3. Resize crop to 80x80 with the **canonical** bilinear: OpenCV generic fixed-point INTER_LINEAR (half-pixel centers,
   11-bit weights, SIMD vertical rounding). Implemented identically in `resize_bilinear_u8` (Python) and
   `ImageOps.cropResize` (Java).
4. Tensor: BGR order kept, float32, values 0..255 (**no /255, no mean/std**), NCHW `[1,3,80,80]`, input name `input`.
5. Output `logits [1,3]` -> softmax per model -> sum over models. Class 1 = real.

**Why a canonical resize instead of cv2.resize?** `cv2.resize` is not the same on every machine: this Mac's arm64
OpenCV build routes it through vendor HALs (Carotene/KleidiCV) and differs from OpenCV's generic code by +-1 pixel on up to
~38% of pixels for some crop sizes. Matching "cv2" on Android, x86 and ARM at once is impossible, so the spec fixes one
integer algorithm. Cost: vs upstream PyTorch (which uses cv2.resize) scores differ by up to 5.5e-4 on our test set.
Do NOT substitute Android `Bitmap.createScaledBitmap` or any other resizer without re-running `tests/test_parity.py`.

## Tests
```
.venv/bin/python -m pytest minifasnet/tests -q        # 23 tests: unit, eval metrics, 3-way parity
.venv/bin/python minifasnet/tests/test_parity.py      # regenerates tests/out/parity_report.md
cd minifasnet/java && mvn test                        # 5 JUnit tests
```

## Evaluation (`eval/evaluate.py`)
```
.venv/bin/python minifasnet/eval/evaluate.py --data my_videos/ --out results/ \
    --box-source csv --box-csv boxes.csv --threshold 0.5 --target-bpcer 0.05
```
- Files: `real_*.mp4` (bona fide), `attack_<species>_*.mp4` (species e.g. `print`, `screen`, `replay`), `attack_*.mp4`
  (species `unknown`). Images (`.jpg/.png`) are accepted as single-frame presentations. Other names are ignored with a warning.
- Boxes: `--box-source csv` with `file,frame,x,y,w,h` rows from **your** detector (e.g. MediaPipe; only listed frames are scored),
  or `--box-source haar` (DEV ONLY).
- Output: `frames.csv`, `videos.csv`, `sweep.csv`, `summary.md`.

**How the metrics are computed (ISO/IEC 30107-3 terminology):**
- Presentation = one file. Presentation score = mean (or `--aggregate median|min`) of its frame `real_score`s.
- Decision: **bona fide if score >= threshold**, else attack.
- `APCER(species) = (# attack presentations of that species classified bona fide) / (# attack presentations of that species)`.
- Reported **APCER = max over species** (worst-case PAI species, as ISO/IEC 30107-3 asks); pooled APCER is shown for information only.
- `BPCER = (# bona fide presentations classified attack) / (# bona fide presentations)`.
- Files with no scorable frame (no face / invalid box) are **failures to process**: excluded from APCER/BPCER and counted separately.
  How a formal 30107-3 evaluation must treat them (e.g. as part of a non-response rate) is **UNVERIFIED** here — I did not have the
  standard's text; check the clause before reporting official numbers.
- Threshold for target BPCER: the **highest** threshold whose BPCER <= target (BPCER only rises with the threshold, so this gives the
  lowest APCER under the constraint). Candidate thresholds = 0.00..1.00 step 0.01 plus every observed presentation score.
- Frame-level decisions in `frames.csv` are diagnostics, not ISO presentation-level metrics.

Threshold-tuning advice: record many subjects/sessions/lighting/devices; keep a held-out split (tune on one, report on the other);
choose the threshold on BPCER target, report APCER per species with counts; re-tune whenever the camera, detector, or resize changes.

## Latency (`eval/bench_latency.py`) — this machine only
Apple M4, macOS 26.1, onnxruntime 1.30.0, warm-up 30 excluded, 300 iterations, both models + preprocessing per frame:

| frame | variant | mean ms | p95 ms |
|---|---|---|---|
| 480x640 | Python predict, ORT default threads | 2.85 | 4.11 |
| 480x640 | Python predict, ORT 1 thread | 2.31 | 2.36 |
| 480x640 | Python preprocessing only | 0.56 | 0.57 |
| 480x640 | Java predict, ORT default | 1.99 | 2.88 |
| 1080x1440 | Python predict, ORT default threads | 3.00 | 4.38 |
| 1080x1440 | Python predict, ORT 1 thread | 2.42 | 2.57 |
| 1080x1440 | Python preprocessing only | 0.70 | 0.67 |
| 1080x1440 | Java predict, ORT default | 2.02 | 2.92 |

These are desktop numbers; they say nothing precise about phones. Upstream README reports 19–90 ms per frame on phone SoCs for its own
APK runtime (Kirin990 19 ms … RK3288 90 ms) — a different runtime, so only a rough hint (**UNVERIFIED** for ONNX Runtime on Android).

What could be slow on a low-end Android phone (not built here):
- Camera frames arrive as YUV (NV21/YUV_420_888): converting the **whole** frame to BGR every frame is expensive — convert only the crop window.
- Two models per frame = 2x inference. Score at a sampled rate (e.g. 5 fps), not every camera frame.
- Thread oversubscription on big.LITTLE CPUs: set ORT intra-op threads to 1–2 (1 thread was faster *and* steadier here).
- Per-frame allocations (`float[]`, `byte[]`) cause GC pauses: reuse buffers; JIT warm-up makes the first frames slow.
- Hardware EPs (NNAPI/XNNPACK) or fp16/int8 quantisation can speed things up but change numerics: re-run parity and re-tune the threshold.
- Thermal throttling during long sessions; camera rotation handling (box must be in the rotated frame's coordinates).

## Dependencies and licenses
| Package | Why | License |
|---|---|---|
| numpy | arrays | BSD-3 |
| opencv-python (<5) | eval video reading, upstream reference, dev helper (5.x removed Caffe + Haar APIs) | Apache-2.0 (wheel bundles FFmpeg, LGPL — only relevant if redistributed) |
| onnxruntime (py 1.30.0, java 1.30.0) | inference | MIT |
| onnx | export validation | Apache-2.0 |
| torch | export + PyTorch parity only | BSD-3 |
| pillow | imported by upstream transform module | HPND (OSI-approved) |
| pytest, JUnit 5 | tests | MIT, EPL-2.0 |
| OpenJDK 17, Maven | Java build | GPL-2.0 + Classpath Exception, Apache-2.0 |

Model weights / training data license: **UNCLEAR** — see `models/MODEL_CARD.md`. This is a blocker under an OSI-only policy.

## Privacy
Face images/videos are never committed (`.gitignore` blocks image/video extensions and eval data/output folders).
