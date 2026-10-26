# Automated test results

**Automated tests only. Real-device accuracy results (APCER/BPCER) are in a separate file and are not part of this one.**

Run on 2026-10-27 against commit `0ed0fa12a0d9138386b6127268e867fb1cddf525`. Per-test rows: [automated-test-results.csv](automated-test-results.csv) (367 rows, made by [tools/ci/results_to_csv.py](../../tools/ci/results_to_csv.py)).

## Totals

| Suite | Module | Passed | Failed | Skipped | Not run | Wall time |
|---|---|---|---|---|---|---|
| Android native | `android/livenessmanager` | 93 | 0 | 0 | 0 | 8 s (both Gradle tasks) |
| Android native | `android/minifasnet` | 8 | 0 | 3 | 0 | (included above) |
| Flutter `test/face_liveness` | app | 46 | 0 | 0 | 0 | 3 s |
| Flutter `test/ui` | app | 37 | 0 | 0 | 0 | 5 s |
| Desktop core | `registration-liveness-core` | 100 | 0 | 3 | 0 | 11 s (includes `clean` and build) |
| Desktop liveness and audit | `registration-services` | 77 | 0 | 0 | 0 | 22 s (includes build) |
| **Total** | | **361** | **0** | **6** | **0** | |

## Environment

| Item | Value |
|---|---|
| OS | macOS 27.0, arm64 (Apple M4) |
| Java, Android | OpenJDK 21.0.12.1 |
| Java, Desktop | OpenJDK 11.0.32.1 |
| Flutter / Dart | 3.10.4 / 3.0.3 |
| Gradle | 8.5 (wrapper version) |
| Maven | 3.10.0 |
| Python | 3.9.6 |

## Commands

The Android and Desktop commands are the ones in `.github/workflows/liveness-tests.yml`. Gradle test tasks were cleaned first and Maven ran with `clean` for the core module, so no cached or stale reports were used.

```sh
# Android native (JDK 21)
cd android && ./gradlew --no-daemon :livenessmanager:cleanTestDebugUnitTest :minifasnet:cleanTest \
  :livenessmanager:testDebugUnitTest :minifasnet:test

# Flutter (3.10.4)
bash pigeon.sh
flutter test test/face_liveness --reporter json
flutter test test/ui --reporter json

# Desktop (JDK 11)
cd desktop/registration
mvn -B clean test -pl registration-liveness-core -am
mvn -B test -pl registration-services -am \
  -Dtest='io.mosip.registration.liveness.*Test,AuditManagerDescriptionOverloadTest' -DfailIfNoTests=false

# CSV
python3 tools/ci/results_to_csv.py --run-date 2026-10-27 --commit 0ed0fa12a0 \
  --out docs/test-results/automated-test-results.csv --junit SUITE:MODULE:DIR --flutter SUITE:MODULE:FILE
```

CI runs the Android, `test/face_liveness` and both Desktop commands. `test/ui` is run here in addition.

## Failures and skips

No test failed. Six were skipped, all `GoldenSamplesTest` (3 in `android/minifasnet`, 3 in `registration-liveness-core`): they compare scores on the upstream sample photos and skip with "samples not provided (`-PminifasnetSamples`)". The photos are not in the repository.

## Not covered by this run

- Other tests in `test/` (`login_test.dart`, `machine_details_test.dart`, `widget_test.dart`, `widgets/`), other Android modules, and the rest of the Desktop `registration-services` tests were not run: they are outside the liveness suites asked for.
- No test ran on a physical phone or inside the real Desktop client.
