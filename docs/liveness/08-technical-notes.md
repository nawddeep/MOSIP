# Technical Notes: Architecture, Privacy and Security, ISO/IEC 30107, Limits

## Architecture in one paragraph

Flutter renders; Java owns camera, engine and device. `FaceLivenessScreen` sends `start / retry / cancel` over a MethodChannel and renders whatever `LivenessEvent` arrives on the EventChannel; the preview is a Flutter `Texture` fed by CameraX. Natively, `LivenessController` connects a `FaceCaptureDevice`, streams `FaceFrame`s on one analysis thread into a `LivenessEngine` (MediaPipe + MiniFASNet + OpenCV), and feeds `FrameAnalysis` results to the pure-Java `LivenessSession` state machine (16 states, see 01-architecture.md). On SUCCESS the controller releases the camera and issues a short-lived, single-use `LivenessGate` pass that the guarded step (SBI face capture) checks natively.

## Privacy

| Rule | How it is enforced |
|---|---|
| No scores, thresholds, model names or attack types on screen | `LivenessEvent` has no field that can carry them (reflection test `PrivacyContractTest`); widget tests assert that no score-like text is rendered; PAD failure copy is generic |
| Never log raw face frames | Frames exist only in memory on the analysis thread. `FaceFrame.toString()` omits pixels. `LivenessAuditRecord` holds only strings and enums (reflection test). Nothing is written to disk. Logcat gets only PERFORMANCE timings (debug builds). |
| Detailed PAD info only in the audit log | PASSIVE_DECISION records (mean/min/max live score, thresholds, motion) go to the standard client audit table via `AuditManagerService` (REG-LIV-00x) |
| Offline-first | no network call in any decision path; the config refresh is skipped offline |
| Data minimisation | no frame or crop leaves the analysis thread; the SBI/MDS capture remains the only stored biometric |

## Security

| Threat | Control |
|---|---|
| UI bypass (skip the liveness screen) | native `LivenessGate` check in `BiometricsDetailsApi.invokeDiscoverSbi`; the pass is never exposed to Dart |
| Replaying an old pass | single use, TTL 60 s, revoked on cancel and on a new session |
| Predictable challenges | `SecureRandom` shuffle, a new draw per attempt, 2 chained challenges by default |
| Printed photo / screen | MiniFASNet passive PAD + PAD re-checked during challenges |
| Injected or frozen frames that look live | micro-motion requirement (`minPassiveMotion`) forces a challenge; timestamp monotonicity check; invalid-frame streak detection |
| Face swap mid-challenge | continuity check (centre jump > 0.6 face widths, or face lost for 3 frames, fails the attempt) |
| Model tampering | SHA-256 pinned in `manifest.json`, verified on load; mismatch gives ENGINE_ERROR (fail closed) |
| Config tampering / corruption | strict schema (unknown keys rejected), SHA-256 integrity over canonical JSON, last-known-good fallback, rejections audited |
| Mock input in production | `setSource` ignored unless `BuildConfig.DEBUG` |
| Crash-induced bypass | every engine, session or audit exception maps to ENGINE_ERROR; there is no path to SUCCESS without a full decision |

Honest gaps: config integrity is a **digest**, not a signature, so authenticity relies on the authenticated MOSIP sync channel. On a rooted device, a hooking attacker can bypass any app-side control; that needs device integrity attestation (Play Integrity / key attestation), which is listed as future work.

## Alignment with ISO/IEC 30107

| Clause / concept | This implementation |
|---|---|
| 30107-1 framework: PAD subsystem detecting presentation attacks at the capture subsystem | passive PAD (MiniFASNet) + active challenge-response, both on the capture device |
| PAD data capture vs. decision | engine returns per-frame scores; `LivenessSession` makes the session-level decision |
| Attack instrument species (PAIS) | print, screen replay, video replay addressed; masks/3D not targeted |
| 30107-3 metrics: APCER per PAIS, worst case reported; BPCER | `eval/PadMetrics` implements both, threshold sweep, and "threshold for BPCER ≤ x" |
| 30107-3 reporting | 07-performance-accuracy.md separates measured from synthetic results and states test conditions |
| Injection attacks (not presentation; see CEN/TS 18099) | partially mitigated (motion + challenge); full mitigation needs a trusted camera path |
| Level / certification | **Not certified.** No iBeta/FIDO-style evaluation has been done. Do not claim conformance until an accredited lab tests on the target hardware. |

## Known limits

1. Real APCER/BPCER are **unmeasured**: no labelled bona fide or attack dataset was captured on target devices.
2. Phone performance rows are pending (emulator only).
3. A single BLINK challenge is beatable by a natural-blink replay; mitigated by `challengeCount: 2`.
4. `minPassiveMotion` (0.002) is uncalibrated on real sensors; too high means extra challenges for very still users.
5. Liveness (CameraX) and the stored biometric (SBI/MDS) are two captures; the 60 s single-use pass narrows but does not close the gap (risk R1).
6. Operator/supervisor "face login" is a liveness gate on top of the password; there is no face matching (no MOSIP API for it), and it is disabled by default.
7. The MiniFASNet crop uses the MediaPipe landmark box, which is tighter than upstream's detector box. Offline check: 0.991 vs 0.996 on the real sample, which is acceptable but not identical.
8. UI copy is English constants (not yet in `lib/l10n` ARB files).
9. The APK grows about 59 MB per ABI.
10. End-to-end SBI capture after a pass, and the operator/supervisor flows, are not exercised on device (they need MOSIP sandbox sync, an onboarded machine and Mock MDS).

## Future work

- Capture a labelled dataset on 2+ device classes; tune `passiveThreshold`, `padRejectThreshold` and `minPassiveMotion` with `PadMetrics`; fill in the device matrix.
- Bind liveness to the stored biometric: face-match the SBI image against the last liveness frame (needs a MOSIP bio-SDK), or an MDS with built-in PAD.
- Config signature verification with the MOSIP sign certificate (keymanager).
- Play Integrity / key attestation for injection resistance; an L1 vendor adapter demo.
- Move copy into ARB localisation; screen-reader announcements for countdowns.
- ABI splits; optional removal of OpenCV in favour of the pure-Java analyzer.
- Optional extras from the brief: configurable challenge pools (supported already via `challengeTypes`), diagnostic mode, anonymised metrics export (PERFORMANCE records exist), model version management (manifest-based, extendable), multi-vendor adapter demo.
