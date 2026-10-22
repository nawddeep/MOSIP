# Vendored: MiniFASNet passive PAD (org.minifasnet)

| | |
|---|---|
| Source repo | https://github.com/pratik-aep/faceDetection_manifestNET (`minifasnet/java`) |
| Commit | `d0cfabe` (2026-10) |
| Files copied **unchanged** | `ImageOps.java`, `MiniFasNetPad.java`, `ModelScore.java`, `PadException.java`, `PadResult.java`, test `MiniFasNetPadTest.java`, `MODEL_CARD.md` |
| Not copied | `Cli.java` (desktop CLI), Python reference/export/eval, dev dashboard, PDF/report — dev tooling, not shipped in the app |
| Added here | `GoldenSamplesTest.java` (upstream parity-report scores, EXIF-rotation aware), `build.gradle` (java-library, Java 11) |
| Models | `livenessmanager/src/main/assets/liveness/models/minifasnet/` — byte-identical to upstream `minifasnet/models/*.onnx` (SHA-256 in `MODEL_CARD.md` and `models/manifest.json`) |

**Do not edit the library sources in place.** Its value is the measured parity (Java vs Python 4.3e-13, vs upstream PyTorch 5.5e-4). To update: copy the new upstream files, rerun `./gradlew :minifasnet:test -PminifasnetSamples=<folder with image_T1/F1/F2.jpg>`, update the hashes in `manifest.json`, and re-tune thresholds (scores change).

Licence status (must be resolved before production release):
- The source repository has **no LICENSE file**. Ask the author to add one (e.g. Apache-2.0, matching upstream Silent-Face-Anti-Spoofing).
- Model **weights**: upstream code is Apache-2.0, weights carry no separate terms. `MODEL_CARD.md` rates this UNCLEAR. The same applies to the previously shipped MiniFASNetV2 model.
