# registration-liveness-core

Platform-neutral face liveness engine and passive PAD (MiniFASNet ensemble), plain Java 11.
These are the **same sources** the Android client runs (`nawddeep/MOSIP`, `android/livenessmanager`
and `android/minifasnet`), minus the `android/` package.

| What | Where |
|---|---|
| Engine, state machine, challenges, quality, config, audit contracts | `io.mosip.registration.livenessmanager.*` |
| MiniFASNet PAD (ONNX Runtime) | `org.minifasnet` |
| Bundled PAD models + licence | `src/main/resources/liveness/models/minifasnet/` |
| Default policy | `src/main/resources/liveness/default_liveness_config.json` |

## Keeping it in sync

```sh
./sync-from-android.sh /path/to/MOSIP/android
```

Main sources are copied unchanged. Test-side changes are listed in the script header and in
`UPSTREAM.md`. No Java 12+ syntax was found: the main sources compile with `--release 11` unchanged.

## Verified

JDK 11.0.32 (Homebrew OpenJDK, arm64), Maven 3.10.0:
`mvn clean test` → 103 tests, 0 failures, 3 skipped (golden-image tests that need extra sample
photos, same as on Android).

## Licences

Gson 2.10.1 (Apache-2.0), ONNX Runtime 1.19.2 (MIT), MiniFASNet code (Apache-2.0). The MiniFASNet
**weights** have an unclear upstream licence: see `MODEL_CARD.md`. Test-only: JUnit 4.12 (EPL-1.0),
JUnit 5 (EPL-2.0).
