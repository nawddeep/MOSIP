# Judge evaluation guide: Desktop liveness

Everything about the Desktop client is in **[desktop/](../../desktop/)** of this repository. Read [desktop/docs/liveness/README.md](../../desktop/docs/liveness/README.md) first; it separates what was verified from what was not.

## Check these claims

| Claim | How to check |
|---|---|
| Builds on JDK 11 | `cd desktop/registration && mvn clean install` with JDK 11 |
| Nothing already passing got worse | compare per-test-class results with an unmodified clone of the same tag (summary in desktop/docs/liveness/README.md) |
| Audit rows reach the client's audit table through `AuditManagerService` | `cd desktop/registration && mvn -pl registration-services -am test -Dtest=AuditRowsIntegrationTest -DfailIfNoTests=false` and read the printed rows of `AUDIT.APP_AUDIT_LOG` |
| Config fits the real `global_param` column and a tampered one is rejected | `-Dtest=GlobalParamColumnTest,DesktopLivenessRuntimeTest` |
| No new tables, no images stored | the tests above use only the client's own DDL; search the diff for `CREATE TABLE` (none) |
| Fails closed | `DesktopLivenessServiceTest` (tampered model, bad config, engine error) |
| UI states | [desktop-screens/](desktop-screens/) (rendered with `tools/liveness-screenshots`) |
| Gates are in Java | `GenericBiometricsController`, `LoginController`, `AuthenticationController` under `desktop/` |

## What cannot be checked from this submission

The running client with Mock SBI (resident pass, challenge, print/phone/replay attacks, operator login, supervisor approval, unplugged device, offline restart), timings on the demo PC, and a screen recording were **not** done. The Desktop README (`desktop/docs/liveness/README.md`) lists the steps to do them.
