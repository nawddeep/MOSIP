# spec.md — What we are building and why

Last updated: 2026-10-02

## 1. Goal

Understand ONE model deeply — **MiniFASNet** (passive face liveness / presentation attack detection) —
and prove it can be exported to ONNX and run offline, so it can serve as the **passive-liveness stage**
of the MOSIP Registration Client (Desktop: Java; Android: Flutter + Java/Kotlin, via ONNX Runtime).

Parent problem: MOSIP Decode problem 04 — Face Liveness & PAD for Registration/Authentication
(hybrid passive + active, offline-capable, vendor-independent, configurable threshold, ISO/IEC 30107 alignment).

## 2. Scope

IN scope (this study):
- Passive liveness/PAD with MiniFASNet only (V1, V2, V1SE, V2SE variants as shipped in the repo).
- Source repo: github.com/minivision-ai/Silent-Face-Anti-Spoofing (cloned into ./minifasnet-study).
- ONNX export + parity check vs PyTorch; Python onnxruntime test script; latency estimate.
- Exact preprocessing spec so Java/Android can reproduce it bit-for-bit (golden test vectors).
- License audit (code, pretrained weights, training data). OSI-approved only.
- ISO/IEC 30107-3 style measurement plan (APCER/BPCER) + threshold tuning approach.

OUT of scope (for now):
- Any other liveness/PAD model; comparing models.
- Active liveness (blink/smile/head-turn) — MiniFASNet cannot do it. Deferred; separate decision.
- Writing the Java/Android integration itself (we only specify what must be reproduced).
- MOSIP Registration Client code changes, UI/UX, config sync, audit logging.
- Formal ISO/IEC 30107-3 certification (we only produce a measurement plan).

## 3. Constraints
- Offline, on-device inference. CPU is the baseline (low-end Android must be considered).
- OSI-approved licenses only. Unclear license = blocker, flagged — not assumed.
- Face detector will be **MediaPipe** in production (not the repo's own detector) — creates a crop-parity risk (see §6).
- Honesty: UNVERIFIED label on anything not checked against code/running output.

## 4. Deliverables (from the study prompt)
A. Big picture (what it solves / misses; passive vs active; fit in hybrid design)
B. Model (V1/V2/V1SE/V2SE architecture, params, file sizes; input spec; output meaning; two-model fusion)
C. Training ideas (Fourier-spectrum auxiliary supervision; data used; stated limits)
D. Pipeline (detector used in repo; what replaces it; step-by-step flow with file+function names)
E. Deployment (ONNX export + parity; Python test script; CPU latency; Java/Android preprocessing list)
F. Risks (licenses; weaknesses; APCER/BPCER plan; threshold tuning with own real/attack videos)
Plus: NOTES.md cheat sheet in ./minifasnet-study, ONNX files + script run on a sample image, 10 quiz questions (answers at bottom).

## 5. Hypotheses to VERIFY after cloning (from model memory — NOT findings)
- H1: Two pretrained models ship: `2.7_80x80_MiniFASNetV2` and `4_0_0_80x80_MiniFASNetV1SE`; names encode crop scale (2.7 / 4.0) and input size (80x80).
- H2: Output has 3 classes; class index 1 = real; others = spoof types; softmax then average across both models.
- H3: Input is a BGR (OpenCV) image crop, 80x80, and the repo's ToTensor may NOT divide by 255 — must check.
- H4: Repo's face detector is a RetinaFace Caffe model (not MediaPipe).
- H5: Auxiliary Fourier-spectrum supervision is used at training only; inference uses the classifier head only.
- H6: Repo code is Apache-2.0; pretrained-weight license and training-data availability are unclear → likely a blocker for the "OSI-only" rule.
- H7: Model is small (~0.4–0.5M params, ~1–2 MB) and fast on CPU.
- H8: Covers 2D print and screen/replay attacks; weak vs 3D masks, injected video, other cameras (domain shift).

Resolution status (2026-10-02, details in report.md Findings): H1 verified, H2 partly (3 classes, index 1 = real verified; spoof indices
unexplained), H3 verified (BGR, no /255), H4 verified (RetinaFace Caffe), H7 verified (~0.43M params, ~1.7MB ONNX), H6 code part verified
(Apache-2.0), weights/data license still UNCLEAR. H5, H8 still UNVERIFIED.
NOTE: scope has since moved to the BUILD brief (Steps 1-6 in report.md); checkpoints C1-C9 in architecture.md are superseded by those steps.

## 6. Key risks identified at analysis time
1. **License gate**: weights/training-data license may fail "OSI-approved only". Decide go/no-go BEFORE heavy investment (checkpoint C3).
2. **Crop parity**: model is trained with crops derived from the repo detector's box × scale factor. MediaPipe's box definition
   differs (tighter/looser, different aspect) → scores can shift. Must define a box-conversion rule and test it.
3. **Preprocessing mismatch** (color order, normalization, resize interpolation) is the #1 integration bug — golden vectors required.
4. **Latency numbers measured on a desktop are not Android numbers.** Report them as desktop-only; give a qualitative estimate for Android.
5. **Passive-only gap**: no active challenge, weak vs video injection (virtual camera) especially on L0 devices.
6. **Single-frame vs multi-frame**: decide whether the decision uses one frame or an aggregate over N frames (affects robustness & latency).

## 7. Open decisions (need the user)
- D1: Confirm model = MiniFASNet (assumed from "manifestnet").
- D2: Variants: both V2 + V1SE ensemble, or one? (repo ships two; study all four architectures regardless)
- D3: If license gate fails: stop, or continue as research-only? (do not assume)
- D4: Is MediaPipe allowed as the only non-MiniFASNet component (detector, not a liveness model)? (assumed yes)
- D5: `git init` the project dir so checkpoints can be committed? (currently not a git repo)
- D6: Active liveness: deferred, or a later rule-based add-on?

## 8. Definition of done (study)
All of A–F answered with code-backed evidence; ONNX parity verified (max abs diff recorded); script runs on a sample
image; NOTES.md written; license verdict stated; quiz delivered; UNVERIFIED items listed honestly.
