# Judge evaluation guide: Desktop liveness

Everything about the Desktop client is in the fork **[nawddeep/registration-client](https://github.com/nawddeep/registration-client)** (`release-1.2.0.x`). Read `docs/liveness/README.md` there first; it separates what was verified from what was not.

## Check these claims

| Claim | How to check |
|---|---|
| Builds on JDK 11 | `cd registration && mvn clean install` with JDK 11 |
| Nothing already passing got worse | compare per-test-class results with an unmodified clone of the same tag (summary in the fork README) |
| Audit rows reach the client's audit table through `AuditManagerService` | `mvn -pl registration-services test -Dtest=AuditRowsIntegrationTest` and read the printed rows of `AUDIT.APP_AUDIT_LOG` |
| Config fits the real `global_param` column and a tampered one is rejected | `-Dtest=GlobalParamColumnTest,DesktopLivenessRuntimeTest` |
| No new tables, no images stored | the tests above use only the client's own DDL; search the diff for `CREATE TABLE` (none) |
| Fails closed | `DesktopLivenessServiceTest` (tampered model, bad config, engine error) |
| UI states | [desktop-screens/](desktop-screens/) (rendered with `tools/liveness-screenshots`) |
| Gates are in Java | `GenericBiometricsController`, `LoginController`, `AuthenticationController` in the fork diff |

## What cannot be checked from this submission

The running client with Mock SBI (resident pass, challenge, print/phone/replay attacks, operator login, supervisor approval, unplugged device, offline restart), timings on the demo PC, and a screen recording were **not** done. The README in the fork lists the steps to do them.
