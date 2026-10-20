# minifasnet — self-contained MiniFASNet passive-liveness module

**Scope: one thing.** Given a BGR frame and a face box supplied by the caller, return a passive real/spoof score.
No face detection (except a dev-only helper), no device code, no UI, no active challenges, no Registration Client code.

## Layout
```
minifasnet/
  models/   2 ONNX files + MODEL_CARD.md (source, license, I/O spec, SHA-256)
  python/   minifasnet_pad.py   reference implementation (SOURCE OF TRUTH)
            dev_box.py          DEV-ONLY Haar face box helper (not for production)
            export_onnx.py      regenerates models/*.onnx from the upstream .pth (deterministic)
  java/     Maven module org.minifasnet (MiniFasNetPad, ImageOps, Cli) + JUnit tests
  tests/    test_unit.py, test_eval.py, test_parity.py (3-way parity), out/parity_report.md
  eval/     evaluate.py (APCER/BPCER), bench_latency.py
  dashboard/  server.py + index.html  DEV-ONLY local page: model self-check + try images/webcam
  requirements.txt / requirements-dev.txt   pinned, verified Python versions
../pyproject.toml   pytest + ruff + pyright configuration (also used by VS Code/Pylance)
```

## Status
| Step | What | Status | Evidence |
|---|---|---|---|
| 1 | Layout, ONNX export, model card | done | ONNX vs PyTorch logits <= 2.6e-6; re-export reproduces the same SHA-256 |
| 2 | Python reference `MiniFASNetPAD` | done | unit tests |
| 3 | Java wrapper | done | JUnit tests |
| 4 | Parity tests | done | PyTorch vs ours 5.5e-4, Python vs Java 4.3e-13 — `tests/out/parity_report.md` |
| 5 | Evaluation script | done | metric unit tests; run on a synthetic set (real-data evaluation pending) |
| 6 | Latency | done | table below |
| Dash | Dev dashboard (model self-check + try-it page) | done | 8 self-checks pass; `tests/test_dashboard.py`; tamper detection verified |
| QA | Error-free codebase | done | ruff clean, pyright 0 errors, `javac -Xlint:all -Werror` clean, 43 pytest + 8 JUnit passing, warnings fail the build |

## Setup
```
python3.11 -m venv .venv
.venv/bin/pip install -r minifasnet/requirements-dev.txt     # runtime only: -r minifasnet/requirements.txt
git clone https://github.com/minivision-ai/Silent-Face-Anti-Spoofing.git minifasnet-study/Silent-Face-Anti-Spoofing
cd minifasnet/java && mvn -q package                          # JDK 11+ and Maven; also writes target/cp.txt
```
- Runtime needs only **numpy + onnxruntime** (verified in a fresh venv: OpenCV is never imported by `minifasnet_pad`).
- The upstream clone and torch are needed only for export and PyTorch parity.
- Java on this Mac: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` (the macOS `java` stub
  on PATH has no runtime). Python scripts find Java via `JAVA_BIN`, then `JAVA_HOME`, then Homebrew, then `PATH`.

## API
Python:
```python
from minifasnet_pad import MiniFASNetPAD, PADError
pad = MiniFASNetPAD(model_dir="minifasnet/models", min_face_size=32, intra_op_threads=0)
r = pad.predict(frame_bgr_uint8_HxWx3, (x, y, w, h))
r.real_score        # 0..1, mean class-1 probability of both models  -> threshold this
r.label             # "real"/"fake" = argmax of summed softmax (upstream rule)
r.label_confidence  # upstream's printed score (confidence of the winning class)
r.per_model_scores  # per-model softmax
```
Java:
```java
try (MiniFasNetPad pad = new MiniFasNetPad("/path/to/models", 32, 1)) {   // (modelDir, minFaceSize, intraOpThreads)
    PadResult r = pad.predict(bgrBytes, width, height, new double[] {x, y, w, h});
}
```
- Errors are typed. Python: `InvalidFrameError`, `InvalidBoxError`, `FaceTooSmallError`, `ModelLoadError`, `InferenceError`
  (all subclass `PADError`); bad constructor arguments raise `ValueError`.
  Java: `PadException.getCode()` = `INVALID_FRAME | INVALID_BOX | FACE_TOO_SMALL | MODEL_LOAD | INFERENCE`;
  bad constructor arguments raise `IllegalArgumentException`; calls after `close()` raise `IllegalStateException`.
- On load, each model's graph is checked against its file name (input `input` [N,3,H,W], output `logits` [N,3]), so a renamed
  or wrong file fails with `MODEL_LOAD` instead of producing silent garbage.
- Both implementations are safe for concurrent `predict` calls (ONNX Runtime sessions are thread-safe; no shared mutable state).
- `intra_op_threads = 1` was faster *and* steadier than the ORT default on this machine (see latency).

## Preprocessing spec (must be reproduced exactly by any port)
1. Box `(x, y, w, h)` from the caller. Validation: frame uint8 HxWx3 and at least 2x2; box = 4 finite numbers, w,h > 0,
   center inside the frame, min(w,h) >= min_face_size.
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
Known deviation: for an *exact* 2x downscale (crop exactly 160x160) generic OpenCV switches to INTER_AREA; we do not (rare;
covered by the parity table). Do NOT substitute Android `Bitmap.createScaledBitmap` or any other resizer without re-running
`tests/test_parity.py`.

## Tests and quality checks (run from the repo root)
```
.venv/bin/python -m pytest                      # 43 tests; warnings are errors (pyproject.toml)
.venv/bin/ruff check minifasnet                 # lint
.venv/bin/pyright                               # type check (0 errors expected)
cd minifasnet/java && mvn -q package            # compiles with -Xlint:all + failOnWarning, runs 8 JUnit tests
.venv/bin/python minifasnet/tests/test_parity.py   # regenerates tests/out/parity_report.md
```
If the upstream clone or the Java build is missing, the 4 parity tests are **skipped** with the reason (`-ra` prints it).

## Demo dashboard (dev-only) — live camera
```
minifasnet/dashboard/run.sh              # starts the server with the venv Python and opens http://127.0.0.1:8765
minifasnet/dashboard/e2e/run_e2e.sh      # pre-demo check: real page in headless Chrome with a fake camera (9 checks)
```
- **Keep the terminal open** while demoing; Ctrl+C stops it. Do not open `index.html` directly (file://) and do not start it with
  plain `python3` (silent no-op on this Mac) — both give "Failed to fetch". The page now says exactly that if it happens.
- **Live camera:** continuous scoring (~12–15 checks/s, model ~3 ms), face box over the mirrored video (green = live, red = spoof),
  big verdict, score smoothed over N frames, score-over-time chart, per-model bars, frames/s. Camera picker for external webcams.
- **Face box:** RetinaFace (the upstream detector the model was used with; default when the `minifasnet-study/` clone exists) or
  Haar. Both are dev-only; production uses MediaPipe on the caller side.
- **Model health:** 8 self-checks (model SHA-256 vs MODEL_CARD, golden score, determinism, invalid box rejected, 3 known samples).
- **Proof panel:** model files + hashes, verified parity numbers (from `tests/out/parity_report.md`), runtime versions.
- **Robustness:** server never drops a request (unexpected errors -> JSON 500 + traceback in the terminal); page shows
  "Server not reachable" with instructions and reconnects by itself (tested by killing and restarting the server mid-demo).
- **Privacy:** frames are processed in memory and never saved; localhost only.
- **Verified in headless Chrome (fake camera from the upstream samples):** live person -> LIVE PERSON (0.99), printed photo ->
  SPOOF (0.05), uploads T1/F1/F2 -> 0.995 / 0.084 / 0.181 (identical to the parity reference), file:// -> instructions.
  NOT verified: a physical webcam, real phone-screen / print attacks in your room — rehearse once before the demo.
- A green self-check means the code and model files behave as verified; it does **not** prove accuracy on your cameras (use
  `eval/evaluate.py` with real recordings for that).

## Evaluation (`eval/evaluate.py`)
```
.venv/bin/python minifasnet/eval/evaluate.py --data my_videos/ --out results/ \
    --box-source csv --box-csv boxes.csv --threshold 0.5 --target-bpcer 0.05
```
- Files: `real_*.mp4` (bona fide), `attack_<species>_*.mp4` (species e.g. `print`, `screen`, `replay`), `attack_*.mp4`
  (species `unknown`). Images (`.jpg/.png`) are accepted as single-frame presentations. Other names are ignored with a warning;
  unreadable files are reported and counted as failures to process.
- Boxes: `--box-source csv` with `file,frame,x,y,w,h` rows from **your** detector (e.g. MediaPipe; only listed frames are scored),
  or `--box-source haar` (DEV ONLY).
- Output: `frames.csv`, `videos.csv`, `sweep.csv`, `summary.md`.

**How the metrics are computed (ISO/IEC 30107-3 terminology):**
- Presentation = one file. Presentation score = mean (or `--aggregate median|min`) of its frame `real_score`s.
- Decision: **bona fide if score >= threshold**, else attack.
- `APCER(species) = (# attack presentations of that species classified bona fide) / (# attack presentations of that species)`.
- Reported **APCER = max over species** (worst-case PAI species). This is the commonly cited ISO/IEC 30107-3 definition;
  the standard's text was not available here, so treat the exact clause as **UNVERIFIED**. Pooled APCER is shown for information.
- `BPCER = (# bona fide presentations classified attack) / (# bona fide presentations)`.
- Files with no scorable frame (no face / invalid box / unreadable) are **failures to process**: excluded from APCER/BPCER and
  counted separately. How a formal 30107-3 evaluation must treat them is **UNVERIFIED**.
- Threshold for target BPCER: the **highest** threshold whose BPCER <= target (BPCER only rises with the threshold, so this gives the
  lowest APCER under the constraint). Candidate thresholds = 0.00..1.00 step 0.01 plus every observed presentation score.
- Frame-level decisions in `frames.csv` are diagnostics, not ISO presentation-level metrics.

Threshold-tuning advice: record many subjects/sessions/lighting/devices; keep a held-out split (tune on one, report on the other);
choose the threshold on the BPCER target, report APCER per species with counts; re-tune whenever the camera, detector, or resize changes.
Box definition matters: the same print-attack video scored 0.075 with a Haar box and 0.015 with the RetinaFace box.

## Latency (`eval/bench_latency.py`) — this machine only
Apple M4, macOS 26.1, onnxruntime 1.30.0, warm-up 30 excluded, 300 iterations, both models + preprocessing per frame:

| frame | variant | mean ms | p95 ms |
|---|---|---|---|
| 480x640 | Python predict, ORT default threads | 2.96 | 4.35 |
| 480x640 | Python predict, ORT 1 thread | 2.43 | 2.64 |
| 480x640 | Python preprocessing only | 0.58 | 0.62 |
| 480x640 | Java predict, ORT default threads | 2.03 | 2.88 |
| 480x640 | Java predict, ORT 1 thread | 1.90 | 1.95 |
| 1080x1440 | Python predict, ORT default threads | 3.03 | 4.49 |
| 1080x1440 | Python predict, ORT 1 thread | 2.40 | 2.46 |
| 1080x1440 | Python preprocessing only | 0.65 | 0.66 |
| 1080x1440 | Java predict, ORT default threads | 2.04 | 2.88 |
| 1080x1440 | Java predict, ORT 1 thread | 1.91 | 1.98 |

These are desktop numbers; they say nothing precise about phones. Upstream README reports 19–90 ms per frame on phone SoCs for its own
APK runtime (Kirin990 19 ms … RK3288 90 ms) — a different runtime, so only a rough hint (**UNVERIFIED** for ONNX Runtime on Android).

What could be slow on a low-end Android phone (not built here):
- Camera frames arrive as YUV (NV21/YUV_420_888): converting the **whole** frame to BGR every frame is expensive — convert only the crop window.
- Two models per frame = 2x inference. Score at a sampled rate (e.g. 5 fps), not every camera frame.
- Thread oversubscription on big.LITTLE CPUs: set intra-op threads to 1–2.
- Per-frame allocations (`float[]`, `byte[]`) cause GC pauses: reuse buffers; JIT warm-up makes the first frames slow.
- Hardware EPs (NNAPI/XNNPACK) or fp16/int8 quantisation can speed things up but change numerics: re-run parity and re-tune the threshold.
- Thermal throttling during long sessions; camera rotation handling (box must be in the rotated frame's coordinates).

## Training (not done — status)
The module uses Minivision's pretrained weights unchanged. Upstream ships a training script (`train.py`, SGD, 25 epochs, batch 1024,
loss = 0.5·CrossEntropy + 0.5·MSE on a 10x10 Fourier-spectrum map predicted from intermediate features), but it always builds
**MiniFASNetV2SE**, while the shipped weights are V2 and V1SE — so the exact recipe of the shipped models is unknown. Training data is
not released. Retraining from scratch on consented, licensed data is the only route that fixes the weights-license blocker.

## Dependencies and licenses
| Package | Why | License |
|---|---|---|
| numpy | arrays | BSD-3 |
| onnxruntime (py 1.30.0, java 1.30.0) | inference | MIT |
| opencv-python (<5) | eval video reading, upstream reference, dev helper (5.x removed Caffe + Haar APIs) | Apache-2.0 (wheel bundles FFmpeg, LGPL — only relevant if redistributed) |
| onnx | export validation | Apache-2.0 |
| torch | export + PyTorch parity only | BSD-3 |
| pillow | imported by upstream transform module | HPND (OSI-approved) |
| pytest, ruff, pyright | tests, lint, type check | MIT |
| JUnit 5 | Java tests | EPL-2.0 |
| OpenJDK 17, Maven (+ compiler/dependency/surefire plugins) | Java build | GPL-2.0 + Classpath Exception, Apache-2.0 |

Model weights / training data license: **UNCLEAR** — see `models/MODEL_CARD.md`. This is a blocker under an OSI-only policy.

## Privacy
Face images/videos and raw frames (`*.bgr`) are never committed (`.gitignore` blocks image/video extensions and eval data/output folders).
