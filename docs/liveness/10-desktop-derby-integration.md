# Desktop Registration Client: liveness integration

The Desktop integration lives in a fork of the real client: **[nawddeep/registration-client](https://github.com/nawddeep/registration-client), branch `release-1.2.0.x`** (based on `v1.2.0.2`). Its own documentation, with the list of modified upstream files, is `docs/liveness/README.md` in that fork.

This page replaces an earlier version that described a standalone Derby test as if it were the client integration. It was not.

## What exists

| Item | State |
|---|---|
| Shared Java 11 engine module, same sources as `android/livenessmanager` | built, 103 tests pass |
| SBI frame source, Face Mesh landmarks, MiniFASNet PAD, quality, `DesktopLivenessEngine` | built, tested on real models |
| Audit through the real `AuditManagerService` (`REG-LIV-001..006`) | verified into `AUDIT.APP_AUDIT_LOG` with the client's own DDL |
| Config from `global_param` (`mosip.registration.face.liveness.config`) | verified against the real `REG.GLOBAL_PARAM` DDL; tamper rejected; offline restart uses the cached copy |
| JavaFX popup, all 16 states, en / fr / ar | rendered off-screen from the real FXML/CSS/bundles: [desktop-screens/](desktop-screens/) |
| Gates in `GenericBiometricsController`, `LoginController`, `AuthenticationController` | written and compiled |

## What has NOT been verified

* The real client has **not been run** with an SBI device or Mock SBI. It needs a MOSIP environment that was not available, so the gates have not been exercised in the running UI. This is therefore not claimed as "integrated" end to end.
* Print, phone-screen and video-replay attacks with real media were not tested. An injected still photo was stopped by the random challenge, not by passive PAD alone.
* Timings come from an Apple-silicon Mac, not a registration PC. No screen recording exists.
* French and Arabic wording of the new alert is a draft.
* The MiniFASNet weights have an unclear upstream licence.

## Facts that differ from the earlier text

* The client's audit table is **`AUDIT.APP_AUDIT_LOG`**, not `APP_AUDIT_LOG` in the default schema.
* The existing 4-argument `audit()` overwrites `ref_id` with the user id or registration id; a new 5-argument overload keeps the liveness session id.
* `REG.GLOBAL_PARAM.VAL` is `VARCHAR(512)`. The full default config (about 1,100 characters signed) does not fit, so the synced value must list only the fields that differ from the defaults (about 270 characters). `tools/liveness-config-signer` signs and checks it.

## `tools/derby`

`tools/derby` is a **standalone adapter test, not the client schema**. It exercises the neutral adapters against a hand-made Derby database. It is kept for reference only and is not evidence of client integration.
