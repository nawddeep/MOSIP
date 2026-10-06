# Phase 7: Performance and Accuracy

> **Read this first.** No physical phone was available in this environment, so the low-end and mid-range phone rows are **not measured** (marked "pending"). Everything below comes from an Android 14 arm64 **emulator on an Apple-silicon Mac**, from JVM simulations, or from offline model checks, as labelled. Emulator numbers flatter low-end phones: expect roughly 2–4× slower inference on a 2019-era Android Go device.

## 1. Performance

### Measured (emulator, Android 14 arm64, 3 GB RAM, `swiftshader` GPU, CPU inference)

| Metric | Value | How |
|---|---|---|
| Per-frame engine time (MediaPipe landmarks + OpenCV quality + MiniFASNet PAD), mean | **35.4 ms** | PERFORMANCE audit, 382 frames, all with PAD |
| … p95 / max | **70.1 ms / 154.7 ms** | same |
| Sustainable analysis rate | ~28 fps compute-bound; 15 fps delivered (mock source rate) | 1000 / 35.4 |
| Engine cold init (OpenCV load, SHA-256 of 5.5 MB models, MediaPipe + ORT sessions) | under 1 s (start tap to first state) | screenshots / logcat |
| Native heap during session | **+60 MB** (40 → 100 MB) | `dumpsys meminfo` |
| Java heap during session | **+12 MB** (13.5 → 25.8 MB) | same |
| CPU during session | ~1.5 cores (148% in `top`) | `top -n 1` |
| Time to decision, live-looking face, passive path | ~0.7 s positioning + ~0.4 s passive (5 + 5 frames at 15 fps) | screenshots |
| APK size added (arm64 native + models, uncompressed) | OpenCV 20.9 MB, ONNX Runtime 18.4 MB, MediaPipe 14.0 MB, models 5.5 MB, about **59 MB per ABI** | `unzip -l` |
| APK size, debug, all 4 ABIs | 279 MB → 548 MB | build output |

### Device matrix (to fill on real hardware)

| Device class | Example | fps | mean / p95 ms | Native heap Δ | Battery per 100 sessions | Status |
|---|---|---|---|---|---|---|
| Low-end | Android 9–11, 2–3 GB RAM, Cortex-A53 | | | | | **pending** (no device) |
| Mid-range | Android 12+, 4–6 GB, Cortex-A76 class | | | | | **pending** (no device) |
| Emulator (this run) | Android 14 arm64 on Apple M-series | 15 (source-capped) | 35.4 / 70.1 | +60 MB | n/a | measured |

**Protocol for the pending rows:** install a debug build, push a 30 s video to `liveness_mock/sample.mp4`, then run *Liveness debug → Real engine → Mock video* 10 times and *Camera* 10 times. Read the `FaceLivenessAudit … type=PERFORMANCE` lines from logcat, take `dumpsys meminfo` mid-session, and use `dumpsys batterystats --reset` before / `dumpsys batterystats` after for battery.

### Optimisation knobs already in place

| Knob | Effect | Default |
|---|---|---|
| `performance.frameSkip` | analyse every Nth frame | 1 |
| `performance.padEveryNthFrame` | run PAD every Nth analysed frame (landmarks still every frame) | 1 |
| CameraX keep-only-latest | busy analysis means dropped frames, never a backlog | on |
| `MAX_ANALYSIS_SIDE` 640 | frames downscaled before inference | 640 |
| NNAPI execution provider | `AndroidLivenessFactory.createEngine(ctx, useNnapi=true)` | off (NNAPI quality varies a lot by vendor; enable per device after testing) |
| Buffer reuse | RGBA byte[], OpenCV Mats, ORT input buffer reused per frame | on |

**Recommended starting values for low-end phones** (to confirm with the protocol above): `frameSkip: 2`, `padEveryNthFrame: 2`, `passiveFrames: 4`. That cuts engine work about 3× while keeping more than 7 analysed fps.

**Size options** (not done, listed for decision): ship arm64-v8a only plus armeabi-v7a via ABI splits (removes about 120 MB from a universal APK). Drop OpenCV (−21 MB/ABI) by moving its two uses, crop/resize and Laplacian, to pure Java; `LumaQualityAnalyzer` already exists. The brief asked for OpenCV, so it stays for now. TFLite instead of ONNX Runtime would save about 15 MB/ABI by reusing MediaPipe's TFLite, but MiniFASNet would need a TFLite conversion and re-validation.

## 2. Accuracy

### What was measured

**a) Model and preprocessing correctness (offline, real images)**

| Image | Truth | Live score |
|---|---|---|
| Upstream sample `image_T1` | bona fide | 0.996 |
| Upstream sample `image_F1` | attack | 0.001 |
| Upstream sample `image_F2` | attack | 0.001 |
| NASA public-domain studio portrait, digital (injected) | attack (injection) | **0.999** |

The pipeline reproduces upstream behaviour. The model **cannot** see a pristine digital photo as an attack, which is expected because it looks for recapture artifacts.

**b) End to end on device, real engine, injected still photo**

| Run | Result |
|---|---|
| Before fix | **Accepted** (passive pass in ~2 s) |
| After `minPassiveMotion` fix | Rejected: CHALLENGE → CHALLENGE_TIMEOUT ×3 → MAX_RETRIES |

**c) Decision logic over synthetic populations** (`AttackScenarioTest`, 200 sessions per species, assumed score distributions; bona fide ~N(0.88, 0.08) with natural motion and reactions)

| Policy | BPCER | APCER print | APCER screen (static) | APCER video replay w/ blinks | APCER injected still | APCER (max) |
|---|---|---|---|---|---|---|
| challengeCount = 1 | 0.00 | 0.00 | 0.00 | **0.32** | 0.00 | **0.32** |
| challengeCount = 2 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 |

**This is not an accuracy claim for the model.** It shows the state machine does what it should for given score distributions, and it found a real design weakness (a single blink challenge can be met by a natural blink in a replayed video).

### What could not be measured, and what is needed

Real APCER/BPCER need a labelled set captured on target devices: for example ≥ 100 bona fide subjects across skin tones, ages, glasses and lighting, plus ≥ 3 PAI species (print, screen, video replay; ideally also paper mask). Then run `PadMetrics.addScore(species, meanLiveScore)` per session, `equalErrorThreshold()` and `thresholdForBpcer(0.05)`, and report per ISO/IEC 30107-3 (APCER per species, max reported). Public datasets (OULU-NPU, SiW, CelebA-Spoof) carry **non-commercial / research licences**, so check their terms before using them for tuning a product.

### Final default config values (and why)

| Field | Resident | Operator | Supervisor | Basis |
|---|---|---|---|---|
| enabled | true | false | false | product decision needed for login flows (D3) |
| passiveThreshold | 0.90 | 0.90 | 0.92 | upstream T1 = 0.996 vs attacks 0.001; leaves margin for camera variation; **tune on real data** |
| padRejectThreshold | 0.30 | 0.30 | 0.30 | below this, a challenge is not offered (clear attack) |
| challengeCount | **2** | **2** | 2 | synthetic replay study (0.32 → 0.00) |
| challengeTimeoutSeconds | 8 | 8 | 8 | reaction ≤ 1 s + action ≤ 2 s, with margin for elderly users |
| maxRetries | 3 | 3 | 3 | brief |
| minFaceSize | 0.25 | 0.25 | 0.25 | 80×80 crop at 2.7× needs ≥ ~100 px faces at 640 px analysis |
| minPassiveMotion | 0.002 | 0.002 | 0.002 | 0 for frozen frames; real jitter is usually ≥ 0.003. **Calibrate on devices**: if too high, still users get an extra challenge (UX cost, not a security cost). |

### Where it performs poorly (known and expected)

- **Low light / backlight**: the quality gate blocks it (LIGHTING), which protects accuracy but stops some users. MiniFASNet scores also drift under strong colour casts.
- **Low-end CPUs**: p95 > 150 ms likely. Use the frame-skip knobs; challenges still work at ≥ 7 fps.
- **High-quality video replay on a good screen**: PAD may score it in the grey zone. Two random challenges are the main defence, and a sophisticated attacker with a reactive (deepfake) feed is out of scope for this PAD level.
- **Digital injection** (virtual camera, rooted device): passive PAD cannot see it; only the motion + challenge rules help. True mitigation needs camera attestation (L1 device / TEE), which is future work.
- **Faces with glasses / heavy makeup / masks**: blink and smile detection are less reliable; yaw is robust.
