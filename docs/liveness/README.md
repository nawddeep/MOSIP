# Face Liveness + PAD for the MOSIP Android Registration Client

Passive liveness first, then a random active challenge (blink / smile / turn head) when confidence is low. Capture or login proceeds only when liveness passes. It covers resident face capture, operator login and supervisor approval, and runs fully offline on the device.

| Doc | Contents |
|---|---|
| [00-discovery.md](00-discovery.md) | Existing code map, frame flow, plug-in points, risks |
| [01-architecture.md](01-architecture.md) | Interfaces, state machine, sequence, bridge contract, config, decisions |
| [02-foundation-and-integration.md](02-foundation-and-integration.md) | Camera/mock/device adapters, bridge, config, audit, workflow wiring |
| [03-engine-and-ui.md](03-engine-and-ui.md) | Engine internals, challenges, quality, Flutter screens and animations |
| [06-error-handling-matrix.md](06-error-handling-matrix.md) | Every failure case, message, recovery, evidence |
| [07-performance-accuracy.md](07-performance-accuracy.md) | Measurements, accuracy findings, default values, limits |
| [08-technical-notes.md](08-technical-notes.md) | Privacy and security, ISO/IEC 30107 alignment, known limits, future work |
| [DEPENDENCIES.md](DEPENDENCIES.md) | Every added dependency, model and font with its licence |
| [liveness-config.schema.json](liveness-config.schema.json) | Config JSON Schema |

## 1. Setup

Toolchain (as pinned by the project CI):

| Tool | Version | Install (macOS) |
|---|---|---|
| Flutter | **3.10.4** (newer Flutter cannot build this repo's Gradle setup) | download `flutter_macos_arm64_3.10.4-stable.zip` from flutter.dev releases into `~/sdks/flutter-3.10.4` |
| JDK | **21** | `brew install openjdk@21` |
| Android SDK | platform 34, build-tools 34.0.0, platform-tools | `brew install --cask android-commandlinetools` then `sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"` |

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH=$HOME/sdks/flutter-3.10.4/bin:$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH

cd android-registration-client
bash pigeon.sh                 # pub get + build_runner + pigeon codegen (as the main README)
flutter build apk --debug      # or: flutter run
```

Notes:
- The models (`face_landmarker.task` 3.8 MB; MiniFASNet ensemble `minifasnet/2.7_80x80_MiniFASNetV2.onnx` + `minifasnet/4_0_0_80x80_MiniFASNetV1SE.onnx`, 1.7 MB each) are committed under `android/livenessmanager/src/main/assets/liveness/models/`, pinned by SHA-256 in `manifest.json`. Nothing is downloaded at runtime. The PAD library is vendored in `android/minifasnet/` (see its `UPSTREAM.md`).
- `android/gradle.properties` raises the Gradle heap to 4 GB (the native ML libraries need it).
- If the Gradle wrapper download times out from the JVM, download `gradle-8.5-all.zip` with curl into `~/.gradle/wrapper/dists/gradle-8.5-all/<hash>/`.

## 2. Run and test

```bash
# Native engine unit tests (state machine, challenges, config, attacks, metrics, PAD adapter): 81 tests
cd android && ./gradlew :livenessmanager:testDebugUnitTest

# Vendored MiniFASNet library: real ONNX inference on the shipped models (8 tests + 3 golden-sample tests)
./gradlew :minifasnet:test -PminifasnetSamples=/path/to/upstream/samples   # golden tests skip without samples

# Flutter widget tests for every liveness state: 26 tests
flutter test test/face_liveness

# UI and engine playground (debug menu: jump to any state, scripted scenarios, real engine)
flutter run -t lib/main_face_liveness_preview.dart
```

In the full app (debug build), **double-tap the MOSIP logo** in the login page's bottom bar to open the same debug menu.

## 3. Config reference

Delivered as MOSIP global param **`mosip.registration.face.liveness.config`** (value = JSON string), which syncs with the other global params. The app caches the last valid copy and falls back to the bundled `default_liveness_config.json`.

| Field | Scope | Default | Meaning |
|---|---|---|---|
| `schemaVersion` | root | 1 | must be 1 |
| `configVersion` | root | | free text, audited when applied |
| `livenessTokenTtlSeconds` | root | 60 | how long a pass stays valid for the guarded step (single use) |
| `performance.frameSkip` | root | 1 | analyse every Nth camera frame |
| `performance.padEveryNthFrame` | root | 1 | run PAD on every Nth analysed frame |
| `enabled` | policy | resident true, others false | gate on/off for the workflow |
| `passiveThreshold` | policy | 0.90 | mean live score for a passive pass |
| `padRejectThreshold` | policy | 0.30 | below: PAD_FAILED without a challenge |
| `challengeTypes` | policy | all 4 | pool: `BLINK`, `SMILE`, `TURN_LEFT`, `TURN_RIGHT` |
| `challengeCount` | policy | 2 | distinct challenges per attempt (random) |
| `challengeTimeoutSeconds` | policy | 8 | per challenge |
| `maxRetries` | policy | 3 | attempts per session |
| `minFaceSize` | policy | 0.25 | face width / shorter frame side |
| `maxFaces` | policy | 1 | only 1 supported |
| `passiveFrames` | policy | 5 | scored frames before the passive decision |
| `positioningStableFrames` | policy | 5 | good frames before scoring starts |
| `minPassiveMotion` | policy | 0.002 | natural micro-motion needed for a passive pass (0 disables) |
| `quality.minBrightness` / `maxBrightness` | policy | 50 / 220 | mean gray of face region |
| `quality.minSharpness` | policy | 25 | variance of Laplacian |
| `quality.maxMotion` | policy | 0.15 | face-centre movement per frame (face widths) |
| `integrity` | root | | **required for synced configs**: `{"algorithm":"SHA-256","value":"<hex>"}` |

**Computing `integrity.value`:** SHA-256 (lower-case hex) of the canonical JSON of the document **without** `integrity`: object keys sorted, no whitespace, numbers in plain form without trailing zeros, Gson string escaping without HTML escaping. Reference implementation: `CanonicalJson.integrityDigest()`; see the test helper `LivenessConfigTest.sign()`. Unknown keys, out-of-range values or a digest mismatch cause the update to be rejected (audited), and the previous config stays active.

## 4. Mock device guide

Use it to run the real engine with no camera or certified device, for example to replay attack recordings.

1. Debug build only (release builds ignore the mock source).
2. Put media on the device:
   ```bash
   # a video (mp4/3gp/webm)
   adb push my_session.mp4 /sdcard/Android/data/io.mosip.registration_client/files/liveness_mock/sample.mp4
   # or a folder of frames, played in file-name order and looped
   adb shell mkdir -p /sdcard/Android/data/io.mosip.registration_client/files/liveness_mock/frames
   adb push frames/. /sdcard/Android/data/io.mosip.registration_client/files/liveness_mock/frames/
   ```
3. Open the debug menu, then *Real engine → Mock video* or *Mock frames folder*, then **Start**.
4. With no media present, you get the device-unavailable screen (useful for testing that path).
5. Timing per session: `adb logcat -s FaceLivenessAudit` (PERFORMANCE lines; no scores).

From code: `FaceLivenessService().setSource(mock: true, path: 'frames', mirrored: false)`. `mirrored` says whether the recording is a selfie-mirrored image; it decides what "left" means for head-turn challenges. Most phone recordings are **not** mirrored.

Swapping in a vendor device: implement `VendorDeviceAdapter.VendorFaceSdk` (attach / open frame source / close) and return the adapter from `FaceLivenessApi.currentDevice()`. Engine, session and UI are unchanged.

## 5. Sign up and demo accounts

MOSIP operator accounts are created by an administrator (Keycloak + Admin Portal) and mapped to a centre and machine; the registration client has no API to create users. The login screen's **"Don't have an account? Sign up"** link therefore opens a **request-access form**:

- Fields: full name, email, phone (optional), preferred username, registration centre, requested role (Registration officer / Supervisor), note.
- **Send request to administrator** opens the email app with the request filled in, addressed to the global param `mosip.registration.signup.admin.email` (if configured). Without an email app, the request is copied to the clipboard. No password is ever included.

**Local demo account (debug builds only).** The same page has a "Testing only" section that creates an account stored **only on this device**, so the app can be explored without a MOSIP server:
- The password is stored as PBKDF2-HMAC-SHA256 (120 000 iterations, random salt) in app-private storage (`DemoAccountApi.java`). It is never synced or uploaded.
- Login with that username/password goes straight to the home screen. There is no server session, so sync, packet upload and other server features do not work.
- In release builds the native API refuses every call, the section is hidden, and the login page never checks demo accounts.

Code: `lib/ui/signup/signup_page.dart`, `lib/platform_android/demo_account_service.dart`, `android/app/.../api_services/DemoAccountApi.java`, `AuthProvider.startDemoSession`. Tests: `test/widgets/signup_page_test.dart`, `test/widgets/password_visibility_test.dart`.
