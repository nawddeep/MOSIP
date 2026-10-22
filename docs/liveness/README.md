# Face liveness + PAD in the MOSIP Desktop Registration Client

Branch `release-1.2.0.x` (based on tag `v1.2.0.2`). This fork adds passive presentation-attack detection (PAD), random
active challenges, and gates for resident face capture, operator login and supervisor approval. It uses the same engine
as the Android client ([nawddeep/MOSIP](https://github.com/nawddeep/MOSIP)), is configured through the existing global
parameter and audited through the existing audit service, with **no new tables and no schema change**.

## Status: what is and is not verified

This code builds, passes its tests and renders. It has **not been run inside the real client with an SBI device or Mock
SBI**, so it is not claimed to be "integrated" end to end.

| Area | Verified | How |
|---|---|---|
| Builds on JDK 11 | yes | `mvn clean install` with JDK 11 |
| Shared core is plain Java 11 | yes | main sources compile with `--release 11`, no Java 12+ syntax, nothing rewritten |
| Existing tests | no regression | per-class comparison against an unmodified clone (see "Tests") |
| Engine on real models | yes | BlazeFace + Face Mesh landmarks + MiniFASNet ensemble on ONNX Runtime 1.19.2 (`DesktopFaceDetectorTest`, `DesktopLivenessServiceTest`) |
| Audit rows | yes | real `AuditManagerSerivceImpl` + kernel `AuditHandlerImpl` + embedded Derby with the `AUDIT.APP_AUDIT_LOG` DDL from `initial.sql`; rows read back with SQL (`AuditRowsIntegrationTest`) |
| Config fits `global_param` | yes | the real `REG.GLOBAL_PARAM` DDL accepts a signed compact config and rejects the full default (`GlobalParamColumnTest`); `GlobalParamService` itself is mocked in unit tests |
| Fail closed | yes | modified/missing model, bad config, engine failure: no pass is issued (tests) |
| JavaFX screen | rendered only | all 16 states in en / fr / ar rendered off-screen from the real FXML, CSS, bundles and controller (`tools/liveness-screenshots`) |
| Injected still photo | blocked | by the random challenge stage, not by passive PAD alone (see "Attack results") |
| **Inside the real client with SBI / Mock SBI** | **no** | needs a MOSIP environment (config server, machine registration, sync) and a device or Mock SBI; none was available |
| **Resident / operator / supervisor gates in the running UI** | **no** | compiled and reviewed; no UI automation exists in the client |
| **Print, phone-screen, video-replay attacks with real media** | **no** | only an injected still photo was tested |
| **Timings on a registration PC / demo machine** | **no** | measured on an Apple-silicon Mac only |
| **Screen recording** | **no** | not made |
| Fr / Ar wording of the new alert | draft | needs native review |

## What was added

```
registration/registration-liveness-core/   new Maven module: the shared engine (same sources as Android, minus android/)
registration/registration-services/        io.mosip.registration.liveness.*  adapters, engine wiring, models, UI model
registration/registration-client/          io.mosip.registration.controller.liveness.*  JavaFX screen, stream tap
tools/liveness-screenshots/                renders every screen state off-screen
tools/liveness-config-signer/              signs and validates a config for global_param
```

| Piece | Where | Does |
|---|---|---|
| `SbiStreamFrameSource` | services | latest-frame-only frames from the SBI preview, scaled to at most 640 px, `mirrored=false`; reports a silent stream |
| `Streamer.setFrameListener` | client | the only hook: the JPEG of each frame, right after it is read. **No second stream is opened** |
| `DesktopFaceDetector` | services | BlazeFace finds the face and eyes, a rotated crop goes through Face Mesh, landmarks are mapped to frame pixels |
| `DesktopModelStore` | services | every model pinned by SHA-256 in `liveness/desktop-models/manifest.json`; a mismatch is an integrity error (`ENGINE_ERROR`) |
| `DesktopLivenessService` | services | one long-lived `LivenessController` with `DefaultLivenessEngine(detector, MiniFASNet ensemble, LumaQualityAnalyzer)` |
| `MosipDesktopAuditManagerService` | services | maps liveness records to `REG-LIV-001..006` through the real `AuditManagerService` |
| `MosipDesktopGlobalParamService` | services | reads `mosip.registration.face.liveness.config` through `GlobalParamService` (`is_deleted` counts as absent) |
| `DesktopLivenessRuntime` | services | config adapter with last-valid cache in `<user.dir>/.liveness/config`, audit logger, the single-use gate |
| `FaceLivenessController`, `FaceLiveness.fxml`, `liveness.css` | client | the popup: dark, oval over the mirrored live preview, one instruction at a time, the Android button sets |

### Gates

| Workflow | Where | Rule |
|---|---|---|
| Resident | `GenericBiometricsController.scan()` and just before `rCaptureTaskService()` | FACE only. The screen runs first; capture starts only after `consumePass(RESIDENT)`. Exception photos are not gated (same as Android) |
| Operator | `LoginController.loadNextScreen()` | after all existing factors, before Home. No pass: session destroyed, login screen shown again |
| Supervisor | `AuthenticationController.loadNextScreen()` (reviewer branch) | after the supervisor's existing authentication, before the registration is submitted |

Each gate decides in Java: it passes only if liveness is disabled for that workflow or a fresh single-use pass exists,
and otherwise audits `REG-LIV-006`. Hiding a button is not the control. With liveness disabled for a workflow the flow
behaves as before. The bundled default enables **resident only**.

## Modified upstream files

15 existing files, all small; everything else is new.

| File | Change | Why |
|---|---|---|
| `registration/pom.xml` | +1 | register the new module |
| `registration-services/pom.xml` | +17 | depend on the core module; copy `liveness/desktop-models/**` **unfiltered** (resource filtering re-encodes binaries and breaks the hash check) |
| `constants/AuditEvent.java` | +16 | six `REG-LIV` events |
| `audit/AuditManagerService.java` | +23 | overload `audit(event, component, refId, refIdType, description)` |
| `audit/AuditManagerSerivceImpl.java` | +25 -3 | old method delegates with `null`; a supplied description is cut at 2048 and the caller keeps `refId`/`refIdType` |
| `controller/device/Streamer.java` | +25 | frame-listener hook |
| `controller/device/GenericBiometricsController.java` | +25 | resident gate |
| `controller/auth/LoginController.java` | +14 | operator gate |
| `controller/auth/AuthenticationController.java` | +12 -1 | supervisor gate |
| `labels_en/fr/ar.properties` | +55 each | 53 liveness strings |
| `messages_en/fr/ar.properties` | +1 each | `LIVENESS_REQUIRED` alert |

## Configuration

Global parameter `mosip.registration.face.liveness.config`; schema, ranges and defaults are those of the Android client.
Two points are specific to this client:

1. `global_param.val` is `VARCHAR(512)` and schema changes are not allowed. The full default config (about 1,100 characters
   signed) does not fit; a config listing only what differs from the defaults is about 270. All three workflows
   (`resident`, `operator`, `supervisor`) must be present.
2. The value needs a SHA-256 integrity block. `tools/liveness-config-signer/SignLivenessConfig.java` adds it, validates with the
   client's own parser, prints the character count and exits 1 (rejected) or 2 (over 512):

```sh
echo '{"schemaVersion":1,"configVersion":"demo-1","policies":{"resident":{"enabled":true,"challengeCount":2},
 "operator":{"enabled":true},"supervisor":{"enabled":true}}}' \
 | java -cp registration-liveness-core.jar:gson-2.10.1.jar:. SignLivenessConfig
```

A bad config (unknown key, out-of-range value, wrong digest) is rejected: the previous valid config stays and `REG-LIV-005`
records it. With nothing synced, the last valid config in `<user.dir>/.liveness/config` is used, then the bundled default.

### Demo config (all three workflows)

`demo-global-param.json` is the signed value that turns liveness on for `resident`, `operator` and `supervisor`
(2 challenges for the resident, defaults elsewhere). It is 273 of 512 characters and was produced with the signer
(exit code 0), which validates it with the client's own parser:

```sh
echo '{"schemaVersion":1,"configVersion":"demo-1","policies":{"resident":{"enabled":true,"challengeCount":2},"operator":{"enabled":true},"supervisor":{"enabled":true}}}' \
 | java -cp registration-liveness-core.jar:gson-2.10.1.jar:<dir with SignLivenessConfig.class> SignLivenessConfig
```

To use it, set the content of the file, unchanged, as the value of the global parameter
`mosip.registration.face.liveness.config` (in a MOSIP environment this is a property of the registration config
served by the config server, which the client copies into `REG.GLOBAL_PARAM` on sync). If the value is edited
by hand the digest no longer matches and the client rejects it (`REG-LIV-005`). The sync path itself was **not**
run here; only the value, its length and its validation were checked.

## Audit

Rows go to **`AUDIT.APP_AUDIT_LOG`** in the client's embedded Derby, through `AuditManagerService`. Notes for reading the table:

* `event_type` holds MOSIP's `USER` / `SYSTEM`.
* The existing four-argument `audit()` replaces `ref_id` with the logged-in user or registration id. The new overload keeps the
  liveness **session id** as `ref_id`, which is why it exists.
* Scores appear only in audit descriptions, never on screen. No image, crop, thumbnail or embedding is stored anywhere; the adapter also
  replaces any description that looks like image data.
* No new `Components` entry: resident rows use Registration: Biometrics, operator Login, supervisor Registration: OS Authentication.

Real rows produced by `AuditRowsIntegrationTest` are in that test's output (`mvn -pl registration-services test -Dtest=AuditRowsIntegrationTest`).

## Models and licences

See [DEPENDENCIES.md](DEPENDENCIES.md). The MiniFASNet **weights have an unclear upstream licence**; until that is resolved the
"every dependency OSI-licensed" rule is not fully met.

## Tests

```sh
cd registration
export JAVA_HOME=<JDK 11>
mvn clean install
```

Unmodified clone vs this branch (tests / failures / errors / skipped), JDK 11:

| Module | Baseline | This branch |
|---|---|---|
| registration-api | 19 / 0 / 1 / 0 | 19 / 0 / 1 / 0 |
| registration-services | 751 / 1 / 183 / 17 | 821 / 0 / 183 / 17 |
| registration-liveness-core | n/a | 103 / 0 / 0 / 3 |

The 183 errors also exist upstream (environment-dependent: application context / JavaFX toolkit). The baseline failure did not recur on this branch; it was not investigated. Per-class results are the same or better for every class.

## Attack results and timings

* **Injected still photo** streamed by a fake device: `POSITIONING > PASSIVE_CHECK > CHALLENGE > MAX_RETRIES`, no pass. The passive
  stage did **not** reject this photo by itself; the random head/eye challenge, which a still image cannot perform, stopped it.
  Print, phone-screen and video replay with real media were not tested.
* **Timings** (`DesktopFaceDetectorTest`) come from an Apple-silicon Mac, not a registration PC. Run the test on the demo machine for its numbers.

## Limits and decisions

* The engine needs MediaPipe 468-point topology; the available Face Mesh ONNX outputs 478 points, the first 468 are used.
* The preview is mirrored on screen; frames reach the engine unmirrored (`mirrored=false`).
* Resident capture opens two sequential streams (liveness screen, then the existing capture), never at once. The pass lives 60 s by default.
* Only the `en`, `fr`, `ar` bundles that the client ships are covered; Android's hi / ta / kn strings are not added.
* No network call in any liveness decision path.

## To finish on the demo machine

1. Start the real client against a MOSIP environment with Mock SBI (face).
2. Sign and set the config (enable operator and supervisor too).
3. Resident pass; blocked capture; print / phone / replay; unplug device; offline restart.
4. Operator login and supervisor approval, with and without a pass.
5. Check `AUDIT.APP_AUDIT_LOG` for `REG-LIV-001..006` and record timings.
