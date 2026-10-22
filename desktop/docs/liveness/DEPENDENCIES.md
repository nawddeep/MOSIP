# Dependencies added for face liveness

| Component | Version | Licence | Used for |
|---|---|---|---|
| ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime`) | 1.19.2 | MIT | runs the detector, landmark and PAD models |
| Gson | 2.10.1 | Apache-2.0 | config parsing and canonical JSON |
| JUnit 5 + vintage (tests only) | 5.6.2 | EPL-2.0 | core module tests |
| BlazeFace short-range (ONNX conversion) | pinned by SHA-256 in `liveness/desktop-models/manifest.json` | Apache-2.0 per its model card | face and eye detection |
| Face Mesh V2 (ONNX conversion) | pinned by SHA-256 in the same manifest | Apache-2.0 per its model card | facial landmarks |
| MiniFASNetV2 + MiniFASNetV1SE (ONNX) | see `registration-liveness-core/MODEL_CARD.md` | code Apache-2.0; **weights licence unclear** | passive presentation-attack detection |

Licence texts: `registration-services/src/main/resources/liveness/desktop-models/LICENSE-Apache-2.0.txt`.

Open item: the MiniFASNet weights have no clear OSI licence upstream. Until that is resolved, the rule that every
dependency is OSI-licensed is **not fully met**; replace the weights or obtain a written licence before release.
Sources and commits of the model conversions are recorded in `MODEL_CARD.md` and `UPSTREAM.md` in the core module.

## MiniFASNet weights: source and licence (read 2026-10-22)

| Item | Finding |
|---|---|
| Where the weights come from | https://github.com/minivision-ai/Silent-Face-Anti-Spoofing (Caffe models in `models/`; its README says the open-source models are in that folder) |
| Licence of that repository | Apache License 2.0. Read at https://github.com/minivision-ai/Silent-Face-Anti-Spoofing/blob/master/LICENSE (GitHub also shows "Apache-2.0 license") |
| Does it say it covers the weights? | **No.** The README (https://github.com/minivision-ai/Silent-Face-Anti-Spoofing) has no sentence about a licence, terms of use or commercial use of the weights. Weights licence not stated separately; covered only by the repository licence: **known issue** |
| Training data | not released, origin and licence not stated |
| ONNX export and vendored Java code we ship | https://github.com/pratik-aep/faceDetection_manifestNET @ `d0cfabe`: no licence label and no LICENSE file among its top-level files (checked 2026-10-22). Needs a LICENSE from its author |

These pages were read through a web-fetch tool that summarises pages, not by cloning the repositories. Re-check the
LICENSE files directly before release. Nothing here is legal advice; whether Apache-2.0 on the repository also covers the
binary weights needs a legal read.
