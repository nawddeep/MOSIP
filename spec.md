# spec.md — What we are building and why

Last updated: 2026-10-02

## 1. Goal
A **self-contained MiniFASNet passive-liveness module** (presentation attack detection, PAD) that can be dropped into the
MOSIP Registration Client as the passive stage of its hybrid liveness design:
- Python reference implementation (source of truth) and a Java (11+) ONNX Runtime wrapper with the same API and numerics.
- Runs fully offline on CPU. Input: a BGR frame + a face box supplied by the caller. Output: `real_score` (0..1) + label.

Parent problem: MOSIP Decode problem 04 — Face Liveness & PAD for Registration/Authentication (hybrid passive + active,
offline-capable, vendor-independent, configurable threshold, ISO/IEC 30107 alignment). This repo covers only the passive model part.

## 2. Scope
IN scope (built):
- MiniFASNet only: the two shipped models (V2 scale 2.7, V1SE scale 4.0), exported to ONNX, fused as upstream does.
- Exact, cross-platform preprocessing spec (crop, canonical resize, tensor) implemented identically in Python and Java.
- Three-way parity tests (upstream PyTorch / Python ONNX / Java), unit tests, static checks.
- ISO/IEC 30107-3 style evaluation script (APCER per species, BPCER, threshold sweep) for the user's own recordings.
- Latency benchmark and notes on low-end Android risks.
- Dev-only local dashboard (self-check + try-it page) added at the user's request (2026-10-02); not part of the shipped module.
- Model card with provenance, SHA-256, I/O spec, license verdict.

OUT of scope (unless the user explicitly widens it):
- Other liveness/PAD models; face detection (production detector = MediaPipe on the caller side); L0/L1 device code;
  active challenges (blink/smile/head turn); Registration Client, Android/Flutter, product UI, config system, audit logging; training.
  (The dev dashboard is the one explicit UI exception.)

## 3. Constraints
- Offline, on-device inference; CPU baseline; low-end Android must stay plausible.
- OSI-approved licenses only. Unclear license = blocker, flagged — not assumed.
- Honesty: anything not checked against code or a real run is labelled UNVERIFIED.
- Face images/videos never enter git.

## 4. Definition of done (module) — status 2026-10-02
| Item | Status |
|---|---|
| ONNX export + model card (SHA-256, I/O, license) | done |
| Python `MiniFASNetPAD.predict(image_bgr, face_box)` with typed errors | done |
| Java `MiniFasNetPad.predict(bgr, w, h, box)` with typed errors, configurable model dir + threads | done |
| Parity < 1e-3 vs upstream PyTorch, Python == Java | done (5.5e-4 / 4.3e-13) |
| Evaluation script with documented APCER/BPCER computation | done |
| Latency mean/p95 (Python + Java) | done (desktop only) |
| Error-free codebase: lint, type check, `-Xlint:all`, tests, warnings-as-errors | done |
| Evaluation on the user's real recordings + threshold choice | **pending (needs data)** |
| License of weights/training data | **blocker (needs decision)** |

## 5. Decisions (resolved)
- D1 Model = MiniFASNet (user said "manifestnet"; confirmed by building on it).
- D2 Both shipped models, fused as upstream (sum of softmax; class 1 = real); API exposes `real_score` = mean class-1 probability.
- D4 Face boxes come from the caller (MediaPipe in production); module never detects faces. A Haar helper exists for dev only.
- D5 Git initialised; one commit per verified checkpoint.
- D6 Active liveness deferred (MiniFASNet cannot do it).
- D7 Canonical integer bilinear resize instead of cv2.resize (cv2 output is platform-dependent).

## 6. Open decisions (need the user)
- D3 Weights/training-data license is unclear under the OSI-only rule: research-only use, ask Minivision, or retrain on licensed data?
- D8 Real evaluation: user to record `real_*` / `attack_<species>_*` videos and export MediaPipe boxes to CSV.
- D9 Retraining pipeline (only route that fixes D3): build it or not.

## 7. Key risks
1. License (D3) — blocks production use.
2. Box definition — scores shift with the detector's box (0.075 Haar vs 0.015 RetinaFace on the same print attack): tune with MediaPipe boxes.
3. Domain shift — trained on unknown data/cameras; must be measured on L0/L1 device footage.
4. Passive-only gap — no defence against video injection / virtual cameras on its own; active challenges are a separate component.
5. Phone performance — only desktop numbers measured.
