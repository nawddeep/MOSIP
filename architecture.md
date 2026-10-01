# architecture.md — Target design + build plan

Last updated: 2026-10-02
Status: the passive pipeline below is BUILT as `minifasnet/` (Steps 1–6; see report.md and minifasnet/README.md, which hold the
verified details — preprocessing items marked UNVERIFIED below were resolved there). The C1–C9 table is superseded by build Steps 1–6.
Resize is a canonical integer bilinear (not cv2.resize) — see README "Preprocessing spec".

## 1. Target runtime pipeline (what we are studying toward)

```
L0/L1 device frame (BGR/RGB, WxH)
        │
        ▼
Face detector  (production: MediaPipe;  repo: UNVERIFIED — see spec H4)
        │ face box
        ▼
Box → square crop with SCALE factor around the box   (scale per model: UNVERIFIED, spec H1)
        │ crop resized to 80x80 (interpolation: UNVERIFIED)
        ▼
Preprocess: color order, dtype, normalization          (UNVERIFIED, spec H3)  ← parity-critical
        │ tensor [1,3,80,80]
        ▼
MiniFASNet (ONNX Runtime) × 1..2 models
        │ logits (3 classes — UNVERIFIED H2)
        ▼
softmax → fuse models (sum/avg — UNVERIFIED) → real score
        │
        ▼
score vs configurable threshold → { real | spoof | needs-active-challenge }
```

Position in the hybrid design: this is the **passive stage only**. "Below threshold" → hand off to the (separate, deferred) active challenge stage.

## 2. Study workspace layout (planned)

```
face detection mosip/
├── CLAUDE.md  spec.md  architecture.md  report.md      ← living docs (this dir)
└── minifasnet-study/                                    ← created at checkpoint C1
    ├── Silent-Face-Anti-Spoofing/   (cloned repo, read-only)   [exact layout UNVERIFIED]
    ├── NOTES.md                     (cheat sheet, C8)
    ├── onnx/                        (exported models, C4)
    └── scripts/                     (onnx test script, C5)
```

## 3. Checkpoints (do in order; each = verify → record in report.md → sync docs → commit)

| ID | Checkpoint | Type | Output / proof of done |
|----|-----------|------|------------------------|
| C0 | Bootstrap docs (CLAUDE/spec/architecture/report) | docs | files exist, user reviewed |
| C1 | Clone repo into ./minifasnet-study; map files; read-only | explore | file map + entry points recorded in report.md |
| C2 | License & data audit (code, weights, training data) | analysis | verdict table; **GO / NO-GO gate** with user |
| C3 | Theory A–B: big picture, architectures (V1/V2/V1SE/V2SE), I/O spec, fusion | explain | code-excerpted explanation; H1–H3,H7 resolved |
| C4 | Theory C–D: training ideas, data, repo pipeline, detector replacement | explain | H4,H5 resolved; step-by-step flow w/ file+function names |
| C5 | ONNX export + parity vs PyTorch on sample images | build | onnx files; input/output names+shapes; max abs diff recorded |
| C6 | Python onnxruntime+OpenCV script on image/frame; CPU latency | build | script run output shown; latency (desktop-only) noted |
| C7 | Java/Android preprocessing spec + golden test vectors; MediaPipe box→crop rule | spec | exact reproducible steps; vectors saved |
| C8 | Risks + ISO/IEC 30107-3 measurement plan (APCER/BPCER, threshold tuning) | analysis | plan doc section |
| C9 | NOTES.md cheat sheet + 10-question quiz (answers at bottom) | docs | file written, UNVERIFIED list honest |

Rationale for order: license gate (C2) runs BEFORE export work, so we don't invest in a model we can't legally use.
Theory-only phase = C0–C4 (+ C8 as pure analysis). Build phase = C5–C7. C1 is read-only exploration but needs network (clone) — only on user's go.

## 4. Cross-platform parity design (applies to C7)
- ONE written preprocessing spec is the source of truth; Python script is the reference implementation.
- Golden vectors: (input image) → (crop) → (tensor checksum) → (logits) stored; Java/Android must match within tolerance.
- Parity-critical items: color order, resize interpolation, crop rounding/padding at borders, dtype, normalization, layout NCHW.
- MediaPipe box ≠ repo detector box: define a conversion rule and measure score drift on the same images.

## 5. Integration contract (future, informational)
Liveness engine interface sketch (not implemented): `assess(frame, faceBox) → {realScore, attackFlag, modelVersion}`;
threshold comparison and active-challenge decision live OUTSIDE the model wrapper (policy layer).
