# minifasnet — self-contained MiniFASNet passive-liveness module

Scope: ONE thing — given a BGR frame and a face box supplied by the caller, return a passive real/spoof score.
No face detection (except a dev-only helper), no device code, no UI, no active challenges, no Registration Client code.

## Layout
```
minifasnet/
  models/   ONNX files + MODEL_CARD.md (source, license, I/O spec, SHA-256)
  python/   reference implementation (source of truth) + export_onnx.py
  java/     ONNX Runtime wrapper                      (Step 3 — not built yet)
  tests/    unit + parity tests                       (Step 4)
  eval/     APCER/BPCER evaluation scripts            (Step 5)
```

## Status
| Step | What | Status |
|---|---|---|
| 1 | Layout, ONNX export, model card | done |
| 2 | Python reference `MiniFASNetPAD` | not started |
| 3 | Java wrapper | not started (no JDK/Maven on this machine yet) |
| 4 | Parity tests | not started |
| 5 | Evaluation script | not started |
| 6 | Latency | not started |

## Setup (Python)
```
python3.11 -m venv .venv            # python 3.12 venv was broken on this machine (ensurepip failure)
.venv/bin/pip install numpy opencv-python onnxruntime onnx torch pillow pytest
.venv/bin/python minifasnet/python/export_onnx.py     # regenerates models/*.onnx
```
Upstream repo (needed only for export + PyTorch parity) lives in `../minifasnet-study/Silent-Face-Anti-Spoofing` (read-only clone).

## Dependencies and licenses
| Package | Why | License |
|---|---|---|
| numpy | arrays | BSD-3 |
| opencv-python | image crop/resize (same ops as upstream) | Apache-2.0 (wheel bundles FFmpeg, LGPL — relevant only if redistributed) |
| onnxruntime | inference | MIT |
| onnx | export validation | Apache-2.0 |
| torch | run upstream PyTorch reference + export (dev only, not needed at runtime) | BSD-3 |
| pillow | imported by upstream transform module | HPND (OSI-approved) |
| pytest | tests | MIT |

Deliberately NOT installed: `easydict` (upstream training-only dependency; license not checked).

## Privacy
Face images/videos are never committed (see `.gitignore`).
