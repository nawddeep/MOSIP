# report.md — Living status

## CURRENT STATE  (keep <= 25 lines; always true)
- Date: 2026-10-02
- Phase: **BUILD** (user said "start the build"). Scope = MiniFASNet passive-liveness module ONLY (see build brief in session; nothing else).
- Build plan: Step1 layout+model card -> Step2 Python ref -> Step3 Java wrapper -> Step4 parity -> Step5 eval -> Step6 latency.
  Rule: finish one step, show evidence, WAIT for user "next".
- Last completed: **Step 2** (python/minifasnet_pad.py MiniFASNetPAD + dev_box.py). Step 1 = layout/ONNX/model card. Awaiting user "next" for Step 3 (needs JDK+Maven decision).
- Step 2 verified: ours vs upstream pipeline (same RetinaFace box), 3 sample images: max real_score diff 4.7e-7. 9 error/edge cases raise typed PADError.
- opencv-python PINNED <5 (5.x lacks Caffe + CascadeClassifier); numpy<2.3.
- Study checkpoints C1-C9 were NEVER done before the build brief claimed they were. Now done only as far as Step 1 needed:
  upstream cloned (commit b6d5f04), code read, ONNX exported. NOTES.md does NOT exist yet (not required by build steps).
- Verified: preprocessing (BGR, no /255, NCHW float32, scaled crop 2.7 & 4.0, 80x80), 3-class softmax, class 1 = real,
  fusion = sum of softmaxes, argmax, /2. ONNX vs PyTorch logits diff <= 2.6e-6 on random inputs.
- Env: .venv (python3.11; 3.12 venv broken). numpy, opencv-python, onnxruntime, onnx, torch, pillow, pytest installed.
- NOT available on this machine: Java runtime, Maven. Step 3 needs a JDK + Maven/Gradle install (ask user first).
- Git: still NOT a repo (D5 open). Nothing committed.
- BLOCKERS/open: weights + training-data license UNCLEAR (OSI-only rule) -> user decision D3; MediaPipe box vs upstream RetinaFace
  box mismatch (out of scope to build, but caller's box definition affects scores).

## Checkpoint log
| Step | Status | Evidence |
|------|--------|----------|
| C0 docs | done | 4 living docs |
| Step 1 | done | minifasnet/{models,python,java,tests,eval}; 2 ONNX files; MODEL_CARD.md w/ SHA-256 |
| Step 2 | done | parity vs upstream 4.7e-7 on 3 samples; error cases shown |
| Step 3-6 | not started | |

## Decisions made
- Scope limited to MiniFASNet only; no detector/device/UI/active-challenge/Android/config/audit work.
- Export keeps raw logits; softmax + fusion live in the caller code (mirrors upstream).
- Output API will expose real_score = mean class-1 probability (upstream's printed score is the winning-class confidence).

## Findings (code-backed; upstream paths relative to minifasnet-study/Silent-Face-Anti-Spoofing)
- src/data_io/functional.py: to_tensor -> `img.float()` (div(255) commented out) => raw 0..255, CHW.
- src/generate_patches.py: _get_new_box clamps scale to min((H-1)/bh,(W-1)/bw,scale), shifts box inside frame, int() truncation,
  slice end inclusive (+1), cv2.resize default (bilinear).
- src/anti_spoof_predict.py: F.softmax over 3 logits; detector = RetinaFace Caffe via cv2.dnn (conf 0.6, picks max-conf box).
- test.py: sum softmax of both models, label=argmax, label==1 real, score=sum[label]/2. Demo requires 3:4 images (demo-only check).
- src/utility.py: model name '4_0_0_80x80_...' parses to scale 4.0; '2.7_...' to 2.7.
- LICENSE = Apache-2.0. README_EN.md: no weight license, no training-data statement; "high precision model" is private.

## UNVERIFIED
- Meaning of the two spoof class indices (0 and 2). Weight/training-data licensing. Fourier-aux details (training only, MultiFTNet).
- Real-image ONNX parity (Step 4). Latency (Step 6). Behaviour on webcam/other cameras, bias, 3D masks, injection (Step 5 data).

## Context history (newest first)
- 2026-10-02: BUILD started. Found study work missing; cloned upstream, built venv, exported ONNX, wrote model card. Step 1 done.
- 2026-10-02: Analysed MOSIP problem 04; narrowed to MiniFASNet; created living docs.
