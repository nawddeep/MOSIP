# Dependencies, Models and Assets Added for Face Liveness

All are OSI-approved licences. Versions are pinned in `android/build.gradle` (`ext{}`) or in `models/manifest.json` (by SHA-256).

## Runtime (shipped in the APK)

| Component | Version | Licence | Used for | Where |
|---|---|---|---|---|
| AndroidX CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`) | 1.3.4 | Apache-2.0 | front-camera preview and analysis frames | `:livenessmanager` |
| MediaPipe Tasks Vision (`com.google.mediapipe:tasks-vision`) | 0.10.14 | Apache-2.0 | face detection + 478 landmarks | `:livenessmanager` |
| └ transitive `protobuf-javalite` | 4.26.1 | BSD-3-Clause | MediaPipe runtime | |
| ONNX Runtime Android (`com.microsoft.onnxruntime:onnxruntime-android`) | 1.19.2 | MIT | PAD model inference | `:livenessmanager` |
| OpenCV (`org.opencv:opencv`) | 4.10.0 | Apache-2.0 | PAD crop/resize/colour conversion, quality metrics | `:livenessmanager` |
| Gson | 2.13.2 (already in app) | Apache-2.0 | config parsing, canonical JSON | `:livenessmanager` |

## Models (assets, verified by SHA-256 before use)

| Model | File | SHA-256 | Licence | Source |
|---|---|---|---|---|
| MediaPipe Face Landmarker (float16, v1) | `face_landmarker.task` (3.8 MB) | `64184e22…0bc9ff` | Apache-2.0 | storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/ |
| MiniFASNetV2 2.7_80x80 (Silent-Face-Anti-Spoofing) | `MiniFASNetV2.onnx` (1.7 MB) | `0cbe5cae…bfbccb8` | Apache-2.0 (weights: Minivision / Mininglamp; licence copy shipped as `LICENSE-MiniFASNet-Apache-2.0.txt`) | ONNX conversion: github.com/QingHeYang/Silent-Face-Anti-Spoofing-onnx (Apache-2.0) of github.com/minivision-ai/Silent-Face-Anti-Spoofing |

## Fonts

| Font | Files | Licence |
|---|---|---|
| IBM Plex Sans 400/500/600 | `assets/fonts/ibm_plex_sans/*.ttf` | SIL Open Font License 1.1 (`OFL.txt` shipped) |

## Existing Flutter packages newly used (no new pub dependencies)

`permission_handler` (MIT, camera permission), `connectivity_plus` (BSD-3-Clause, Offline pill). `pubspec.lock` is unchanged.

## Build changes

| Change | Why |
|---|---|
| `app/build.gradle`: `configurations.all { exclude protobuf-java }` | `biometrics-util:1.3.0` declares `protobuf-java:3.16.3` but never uses it (no class references it); it conflicts with MediaPipe's `protobuf-javalite` |
| `app/build.gradle`: `pickFirsts "google/protobuf/*.proto"` | duplicate well-known `.proto` resources |
| `gradle.properties`: `-Xmx4g` | asset compression OOM'd at 1.5 GB with native libs |

## Dev-only tooling (not shipped)

| Tool | Licence | Use |
|---|---|---|
| Python 3 + onnxruntime (MIT), numpy (BSD-3), opencv-python-headless 4.x (Apache-2.0) | as stated | offline reference check of PAD preprocessing (doc 07) |
| Android Emulator / system image `android-34;google_apis;arm64-v8a` | Android SDK licence | on-device verification |

## Considered and rejected

| Option | Reason |
|---|---|
| ML Kit Face Detection | not open source (Google APIs ToS), so not OSI |
| Datasets OULU-NPU / SiW / CelebA-Spoof for tuning | research / non-commercial licences; check terms before product tuning |
| `google_fonts` package | downloads fonts at runtime, which breaks offline-first |
