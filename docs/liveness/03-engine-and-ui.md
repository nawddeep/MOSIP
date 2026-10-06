# Phases 3 and 4: Liveness Engine and Flutter UI

## Phase 3: engine (standalone Java library `:livenessmanager`)

| Task | Implementation |
|---|---|
| Detection, landmarks, no/multiple faces | MediaPipe Face Landmarker (VIDEO mode, `numFaces=2`, so a second person is seen) → 478 landmarks in pixels. `faceCount==0` gives NO_FACE; `> maxFaces` gives MULTIPLE_FACES. |
| Anti-spoofing model | MiniFASNetV2 (2.7_80x80) on ONNX Runtime Android. OpenCV preprocessing: crop the face box expanded 2.7× (clamped, as in upstream `CropImage`), resize to 80×80, RGBA→BGR, CHW float **0–255**. Softmax over 3 logits; live = p[1]. |
| Preprocessing validated against ground truth | Upstream labelled samples run through the same pipeline: real `image_T1` **0.996**, fake `image_F1`/`F2` **0.001**. A third-party model card's "/255" claim is wrong (it gives 0.005 for everything). |
| Passive score + threshold | Mean live score over `passiveFrames` good-quality frames. ≥ `passiveThreshold` **and** natural micro-motion ≥ `minPassiveMotion` → pass. < `padRejectThreshold` → PAD_FAILED. Otherwise → challenge. |
| Challenges | Blink: eye aspect ratio below 70% of the user's own baseline, then recovers. Smile: mouth width / inter-ocular distance ≥ 1.12× neutral for 3 frames. Head turn: yaw = asin((d(nose,234) − d(nose,454)) / d(234,454)), ≥ 20° from neutral for 2 frames; a 15° turn the wrong way fails. All are relative to a per-user baseline taken in the passive stage. |
| ChallengeManager | `SecureRandom` shuffle of the policy pool, `challengeCount` distinct challenges, a new draw per attempt, timeout, attempt counting |
| Quality | OpenCV on the face ROI: mean gray (LIGHTING), variance of Laplacian at 128 px wide (BLURRY), face width / short side (FAR), centre motion between frames (BLURRY "Hold still"). Pure-Java twin `LumaQualityAnalyzer` uses identical definitions for JVM tests. |
| Off main thread, buffer reuse, frame skip | Single analysis thread, CameraX keep-only-latest (busy means drop), reused RGBA buffer / OpenCV Mats / ONNX input FloatBuffer, `frameSkip` and `padEveryNthFrame` |
| Fail closed | Model SHA-256 mismatch, missing model, OpenCV load failure, NaN or out-of-range score, or any runtime exception → ENGINE_ERROR. Never a pass. |

### Tests (JVM, `./gradlew :livenessmanager:testDebugUnitTest`)

74 tests: state machine (20), controller and threading contracts (17), detectors and geometry (9), challenge randomness and attempts (3), config, integrity and offline (10), quality (5), engine composition (3), privacy contract (3), architecture doc ↔ code (1), attack scenarios and metrics (3).

### Harness on saved video / frames

- **Device harness:** Liveness debug menu → *Real engine → Mock video / Mock frames folder*. Push media with
  `adb push sample.mp4 /sdcard/Android/data/io.mosip.registration_client/files/liveness_mock/` (or a `frames/` folder of JPEGs). The real engine runs on it, and the outcome plus timing are audited (PERFORMANCE lines also go to logcat in debug builds, tag `FaceLivenessAudit`).
- **Offline harness:** `eval/SessionReplay` drives the real state machine and detectors from scripted or recorded per-frame analyses; `eval/PadMetrics` computes APCER/BPCER.

### "Rejects the sample printed-photo and screen-replay videos"

No real attack videos were available, and creating recapture media needs a person and physical props. What was done instead, labelled honestly:

1. **Real engine, on device, still-photo attack** (public-domain NASA portrait fed as frames): first run **ACCEPTED** (live 0.999, passive pass in about 2 s). A pristine digital photo has no recapture artifacts, so the PAD model cannot flag it. **Fix:** passive pass now needs natural micro-motion (`minPassiveMotion`). Frozen or injected frames go to a challenge they cannot perform, ending in CHALLENGE_TIMEOUT. See the on-device re-run in `07-performance-accuracy.md`.
2. **Synthetic attack populations** through the real state machine and detectors (`AttackScenarioTest`, 200 sessions per species): print, static screen replay, blinking video replay, injected still. PAD score distributions are **assumed**, so this proves decision logic, not model accuracy.

## Phase 4: Flutter UI (`lib/ui/face_liveness/`)

| Item | File |
|---|---|
| Shared theme (exact colours, IBM Plex Sans, spacing, 52 px pills, max column width 560 for tablets) | `liveness_theme.dart` |
| Exact copy for every state | `liveness_copy.dart` |
| Screen (driven only by events; intro is the only local state) | `face_liveness_screen.dart` |
| Oval and animations | `widgets/liveness_oval.dart`: breathing ring + floating silhouette, orbiting arc + scan line, draining ring, green fill + self-drawing check, red ring + single shake |
| Challenge line icons (eye closes, mouth curves, arrow slides) | `widgets/challenge_icon.dart` |
| Attempt dots + "Attempt n of N", Offline pill, pill buttons | `widgets/liveness_controls.dart` |
| Mock event stream (5 scripted scenarios, or manual `emit`) | `mock_face_liveness_service.dart` |
| Debug menu (jump to every state, scenarios, real engine) | `liveness_debug_menu.dart` |
| Workflow gate helper | `face_liveness_gate.dart` |

Reduce motion (`MediaQuery.disableAnimations`): looping animations stop, success is drawn complete, there is no shake, and transitions are instant (tested). Contrast: text 17:1, secondary 8.4:1, failure red 6.2:1 on the background, all ≥ 4.5:1.

Copy for two states the brief did not specify (extrapolated, generic):
- `ENGINE_ERROR`: "Face verification could not start. Please try again."
- `TURN_RIGHT`: "Please turn your head to the right."

`INVALID_FRAMES` and `CAMERA_ERROR` reuse the device-error copy.

**Widget tests** (`flutter test test/face_liveness`): 26 tests. Every one of the 15 states is checked for copy, ring colour, oval mode, exact buttons and the absence of any score-like text. Also covered: the intro → start → challenge → success → Continue flow (pops `true`), Try again / Cancel, other-workflow events ignored, reduce motion, quality hints, offline pill, 52 px buttons and a ≥ 44 px back target, and event parsing (unknown state → engineError, fail closed).

Device screenshots were taken on the emulator (challenge state, device error, NO_FACE with live camera, Move closer, success).
