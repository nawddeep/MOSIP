# Phase 6: Error-Handling Matrix

Evidence key: **U** = JVM unit test (`livenessmanager`), **W** = Flutter widget test (`test/face_liveness`), **D** = run on Android 14 arm64 emulator with the real engine (2026-10-05).

| # | Case | State → UI message | Recovery action | Attempt used? | Evidence | Result |
|---|---|---|---|---|---|---|
| 1 | Device unavailable (no camera, permission denied, mock media missing, vendor not attached) | DEVICE_UNAVAILABLE → "Camera device not available. Check the device connection and try again." | Retry connection (reconnects), Cancel | no | U `missingDeviceShowsUnavailableThenRetryReconnects`, W, **D** (missing mock media) | ✅ pass |
| 2 | Device connection failure (present but fails to open) | CAMERA_ERROR → same copy | Retry connection | no | U `failingDeviceIsCameraError`, W | ✅ pass |
| 3 | Camera unavailable while streaming (CameraX critical/in-use error) | CAMERA_ERROR | Retry connection | no | U `streamErrorIsCameraError`, W. CameraState mapping code-reviewed (not forced on device). | ✅ pass (logic) |
| 4 | No face | NO_FACE → "We cannot see your face. Move into the oval." | auto-resolves | no | U `guidanceStatesDoNotConsumeAttempts`, W, **D** (emulator scene) | ✅ pass |
| 5 | Multiple faces | MULTIPLE_FACES → "Only one person should be in view." | auto-resolves | no | U (session + engine `multipleFacesReportCountWithoutFace`), W | ✅ pass |
| 6 | Poor quality: dark/bright, far, blurry/moving | POOR_QUALITY + hint → "Improve the lighting" / "Move closer" / "Hold still." | auto-resolves | no | U `LumaQualityAnalyzerTest` (5), W (3 hints), **D** ("Move closer") | ✅ pass |
| 7 | Liveness below threshold | CHALLENGE (random) | perform action | no (unless it fails) | U `lowPassiveScoreTriggersChallengeThenSuccess` | ✅ pass |
| 8 | PAD failure (clear attack score, passive or during challenge) | PAD_FAILED → "Face verification could not be completed. Please try again." (generic) | Try again, Cancel | **yes** | U `veryLowScoreIsPadFailure…`, `lowScoresDuringChallengeAreRejectedAsAttack`, W | ✅ pass |
| 9 | Challenge failure (face lost, face swapped, wrong direction) | LIVENESS_FAILED → "We could not verify face liveness. Please try again." | Try again, Cancel | **yes** | U `faceLostDuringChallenge…`, `faceSwapDuringChallenge…`, `turningTheWrongWayFails`, W | ✅ pass |
| 10 | Challenge timeout | CHALLENGE_TIMEOUT → "Time ran out. Please try again." | Try again, Cancel | **yes** | U (session + controller ticker), W, **D** (still photo) | ✅ pass |
| 11 | Max retries | MAX_RETRIES → "We could not complete face verification. All 3 attempts have been used. Please follow the recovery process." | Recovery options | n/a | U `exhaustingAttemptsEndsInMaxRetries`, W, **D** (3 × still photo) | ✅ pass |
| 12 | Invalid / incomplete frames (bad buffer size, null, timestamps going back) | INVALID_FRAMES → device-error copy | Retry connection | no | U `invalidFramesAreDetected`, `outOfOrderTimestampsAreInvalid`, session streak test | ✅ pass |
| 13 | Engine error (model missing / hash mismatch, OpenCV load failure, NaN score, runtime exception) | ENGINE_ERROR → "Face verification could not start. Please try again." | Retry connection | no | U `engineInitFailureFailsClosed`, `inferenceFailureIsEngineError`, `outOfRangeScore…`, `sessionLogicErrorFailsClosedToEngineError` | ✅ pass |
| 14 | Audit sink throws (DB full, etc.) | session continues normally | none | no | U `throwingAuditSinkDoesNotBreakSession` | ✅ pass (fixed in Phase 6) |
| 15 | Cancel / back at any point | IDLE; camera released; gate revoked; screen pops `false` | none | no | U `cancelReleasesCameraAndRevokesGate`, W | ✅ pass |
| 16 | Guarded step without a pass (UI bypass, replayed or expired pass) | native refusal `FACE_LIVENESS_REQUIRED`, audit REG-LIV-006 | none | n/a | U `successIssuesSingleUseGate…`, `gateExpires`; native check code-reviewed | ✅ pass (logic) |
| 17 | Config offline / online / tampered / corrupt cache | cached config used offline; bad update rejected | none | n/a | U `LivenessConfigTest` (10) | ✅ pass |
| 18 | Injected/frozen still photo with high PAD score | CHALLENGE → CHALLENGE_TIMEOUT | Try again | **yes** | **D**: first run **failed (accepted)**, fixed by the `minPassiveMotion` rule, re-run ✅; U regression test | ✅ pass after fix |

## Automated attack scenarios (Phase 6 task 4)

`AttackScenarioTest` replays scripted presentations through the real state machine and challenge detectors. PAD score distributions are assumed; see 07 for what this does and does not prove.

| Species | Behaviour | Expected | Result |
|---|---|---|---|
| Printed photo | PAD ≈ 0.25, cannot act | PAD_FAILED or timeout | ✅ never accepted |
| Phone screen (static) | PAD ≈ 0.45, cannot act | CHALLENGE_TIMEOUT | ✅ never accepted |
| Video replay with natural blinks | PAD ≈ 0.6, blinks every 2.5 s | rejected | ⚠️ accepted 32% with 1 challenge, **0% with 2**, hence the default `challengeCount: 2` |
| Injected pristine still | PAD 0.99, no motion | CHALLENGE_TIMEOUT | ✅ never accepted |

## Fixes made in this phase

1. **Still-photo / frame-injection acceptance** (found on device): added the natural micro-motion requirement (`minPassiveMotion`), with audit fields `motion` and `staticScene`.
2. **Crash-proofing the analysis thread**: audit-sink exceptions are swallowed, and session/listener exceptions become ENGINE_ERROR instead of propagating into CameraX.
3. **Retry-connection feedback**: a repeated system error re-emits its event, so the UI's busy state clears.
4. **UI**: preview hidden on error states (stale black texture); content width capped at 560 px for tablets.
5. **Build**: Gradle heap raised to 4 GB; a protobuf conflict between MediaPipe (javalite 4.26) and an unused transitive `protobuf-java` 3.16 from `biometrics-util` resolved by excluding the unused one (verified: no class in `biometrics-util-1.3.0.jar` references protobuf).
