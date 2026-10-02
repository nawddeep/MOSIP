# architecture.md — How the module is built

Last updated: 2026-10-02. Status: BUILT and verified (details and numbers: minifasnet/README.md, report.md).

## 1. Runtime pipeline (passive stage only)
```
caller: frame (BGR uint8 HxWx3) + face box (x,y,w,h) from its own detector (MediaPipe in production)
        │
        ▼
validate ── frame uint8 HxWx3 >= 2x2; box 4 finite numbers, w,h > 0, center in frame, min(w,h) >= min_face_size
        │   (typed errors: INVALID_FRAME / INVALID_BOX / FACE_TOO_SMALL)
        ▼
for each model (sorted by file name):   2.7_80x80_MiniFASNetV2.onnx   4_0_0_80x80_MiniFASNetV1SE.onnx
   crop window = box x scale (2.7 | 4.0) around its center, shrunk to fit, shifted inside frame, int() truncation
   canonical bilinear resize -> 80x80   (integer algorithm, identical in Python and Java)
   tensor = BGR, float32 0..255, NCHW [1,3,80,80]  (no /255, no mean/std)
   ONNX Runtime -> logits [1,3] -> softmax          (INFERENCE error on failure / bad output)
        │
        ▼
fuse: sum softmax over models -> real_score = sum[1]/n ; label = argmax (1 = real) ; label_confidence = sum[label]/n
        │
        ▼
caller's policy layer (NOT in this module): real_score >= threshold ? accept : trigger active challenge / retry
```

## 2. Code map
```
pyproject.toml                    pytest (warnings=error), ruff, pyright/Pylance config
minifasnet/
  models/      *.onnx, MODEL_CARD.md
  python/      minifasnet_pad.py  MiniFASNetPAD, PADError subclasses, get_new_box, resize_bilinear_u8, to_tensor
               dev_box.py         DEV-ONLY Haar box (also used by eval --box-source haar)
               export_onnx.py     upstream .pth -> ONNX (deterministic), sanity check vs PyTorch
  java/        org.minifasnet.MiniFasNetPad (API), ImageOps (crop/resize/tensor), PadResult, ModelScore,
               PadException(Code), Cli (predict | batch | bench | resize); src/test = JUnit
  tests/       test_unit.py, test_eval.py, test_dashboard.py, test_parity.py (+ out/parity_report.md)
  eval/        evaluate.py (APCER/BPCER/sweep), bench_latency.py
  dashboard/   server.py (stdlib http.server, localhost) + index.html: self-check, upload/webcam try-it (DEV-ONLY)
minifasnet-study/Silent-Face-Anti-Spoofing   upstream clone (read-only reference, gitignored)
```

## 3. Cross-language parity design
- The Python module is the reference; Java ports each step line by line (`ImageOps` mirrors `get_new_box`, `_axis_coefs`,
  `resize_bilinear_u8`, `to_tensor`).
- Resize uses only integer math + float32 coefficient steps reproduced exactly in both languages -> bit-identical patches.
- Java CLI reads raw `.bgr` frames so both sides see identical pixels (no JPEG-decoder differences).
- `tests/test_parity.py` runs 17 valid + 3 error cases through upstream PyTorch, Python ONNX and Java (batch mode, one JVM),
  plus a pixel-level resize comparison on 14 patch sizes. Targets: < 1e-3 vs upstream, < 1e-6 Python vs Java.

## 4. Error model
| Condition | Python | Java |
|---|---|---|
| bad frame | InvalidFrameError | PadException(INVALID_FRAME) |
| bad box | InvalidBoxError | PadException(INVALID_BOX) |
| face too small | FaceTooSmallError | PadException(FACE_TOO_SMALL) |
| model dir/file/name/graph wrong | ModelLoadError | PadException(MODEL_LOAD) |
| ORT failure / bad output | InferenceError | PadException(INFERENCE) |
| bad constructor argument | ValueError | IllegalArgumentException |
| use after close | — | IllegalStateException |

## 5. Evaluation design (eval/evaluate.py)
files (`real_*`, `attack_<species>_*`) -> sample frames (rate or CSV frame list) -> boxes (CSV from caller's detector | dev Haar)
-> `predict` per frame -> presentation score = mean/median/min -> APCER per species (reported = max), BPCER, sweep, threshold for
target BPCER. Failures to process are excluded and counted separately.

## 6. Quality gates
pytest (warnings = errors) · ruff · pyright standard (0 errors) · javac `-Xlint:all` + failOnWarning · JUnit · parity report.
Pinned versions: minifasnet/requirements*.txt, pom.xml.

## 7. Integration contract for the Registration Client (future, informational)
`assess(frame, faceBox) -> {realScore, label, modelVersion}`. Threshold comparison, retries, active challenges, audit logging and
configuration belong to the caller's policy layer, not to this module.
