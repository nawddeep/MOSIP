# report.md — Living status

## CURRENT STATE  (keep <= 25 lines; always true)
- Date: 2026-10-02. Phase: **BUILD complete (Steps 1–6 done, verified, committed)**. Scope = MiniFASNet passive-liveness module only.
- Code: `minifasnet/` (models, python, java, tests, eval). Usage + metric definitions + latency: `minifasnet/README.md`.
- Git: repo initialised (user granted all permissions). One commit per verified step. `minifasnet-study/` (upstream clone) is gitignored.
- Tests: pytest 23 passed (`.venv/bin/python -m pytest minifasnet/tests -q`), JUnit 5 passed (`cd minifasnet/java && mvn test`).
- Parity: upstream PyTorch vs ours 5.5e-4 (target 1e-3); Python ONNX vs Java 4.3e-13; canonical resize bit-identical Py/Java.
- Key design decision: canonical integer bilinear resize (not cv2.resize) — cv2.resize is platform dependent (arm64 HAL +-1 px).
- Latency (Apple M4): ~2–3 ms/frame both models (Python 1-thread 2.3 ms, Java 2.0 ms). Phone numbers UNVERIFIED.
- Env: `.venv` = python3.11 (3.12 venv broken; system `python3` 3.13 is silently broken in this shell — use .venv/bin/python).
  opencv-python pinned <5. JDK17 + Maven via brew (JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home).
- OPEN DECISIONS for user: (D3) weights/training-data license UNCLEAR under OSI-only rule; real-data evaluation needs user recordings;
  MediaPipe box vs upstream RetinaFace box mismatch (scores shift with box definition — must evaluate with the real detector's boxes).
- Next checkpoint (only if user asks): run eval/evaluate.py on user's real/attack recordings with MediaPipe boxes; tune threshold.
- NOTES.md (study cheat sheet) and quiz from the earlier study prompt: NOT written (not part of build brief).

## Checkpoint log
| Step | Status | Commit | Evidence |
|------|--------|--------|----------|
| C0 docs | done | ada63a6 | living docs |
| 1 layout/ONNX/model card | done | ada63a6 | ONNX vs PyTorch logits <= 2.6e-6 |
| 2 Python reference | done | ada63a6 | vs upstream 4.7e-7 (3 samples, cv2 resize at the time) |
| 3 Java wrapper | done | ada63a6 | vs Python 4.8e-10 |
| 4 parity tests | done | 7ce8b16 | tests/out/parity_report.md; 17 valid + 3 error cases |
| 5 evaluation | done | 369b363 | synthetic set (haar + csv modes); metric unit tests |
| 6 latency | done | (step 6 commit) | README latency table |

## Decisions made
- Scope limited to MiniFASNet only; no detector/device/UI/active-challenge/Android/config/audit work.
- Export keeps raw logits; softmax + fusion in caller code (mirrors upstream). `real_score` = mean class-1 prob (threshold this).
- Canonical resize = OpenCV generic fixed-point INTER_LINEAR with SIMD vertical rounding, identical in Python & Java.
  Exact-2x downscale: generic OpenCV switches to INTER_AREA; we do NOT (rare case, measured within tolerance) — documented.
- Eval: presentation = file; APCER reported = max over species; failures to process excluded and counted separately.
- Added `intra_op_threads` option (1 thread was faster and steadier on M4).

## Findings (code-backed; upstream paths relative to minifasnet-study/Silent-Face-Anti-Spoofing)
- src/data_io/functional.py: to_tensor -> `img.float()` (div(255) commented out) => raw 0..255, CHW, BGR.
- src/generate_patches.py: scale clamp, shift-inside, int() truncation, inclusive slice, cv2.resize bilinear.
- src/anti_spoof_predict.py: softmax over 3 logits; detector = RetinaFace Caffe via cv2.dnn.
- test.py: sum softmax of both models, label=argmax, 1 = real, score = sum[label]/2.
- LICENSE Apache-2.0 (code). README: no weight license, no training-data statement.
- This machine's opencv-python 4.14 arm64 uses Carotene + KleidiCV HAL; cv2.resize differs +-1 px from generic algorithm.
- Box definition matters: same video scored 0.075 (Haar box) vs 0.015 (RetinaFace box) for a print attack.

## UNVERIFIED
- Meaning of spoof class indices 0 and 2. Weight/training-data licensing.
- Whether the upstream training used cv2 builds matching our canonical resize (x86 generic path assumed).
- ISO/IEC 30107-3 exact treatment of failures-to-process (standard text not available here).
- Behaviour on real webcams/phones, lighting, demographics, 3D masks, injection attacks; Android latency.

## Context history (newest first)
- 2026-10-02 (night): user granted full permissions; finished Steps 4–6, git init + commits; canonical resize decision.
- 2026-10-02: BUILD started; cloned upstream, venv, ONNX export, Python ref, Java wrapper.
- 2026-10-02: Analysed MOSIP problem 04; narrowed to MiniFASNet; created living docs.
