# MODEL CARD — MiniFASNet passive liveness (ONNX)

Every fact below was read from the upstream code or measured here. Items not checked are marked **UNVERIFIED**.

## Source
- Upstream: https://github.com/minivision-ai/Silent-Face-Anti-Spoofing
- Commit used: `b6d5f04ad78778917853b25c778acef6d5626d15` (2020-08-05)
- Architecture: `src/model_lib/MiniFASNet.py` (imported at export time, not copied)
- Conversion: `minifasnet/python/export_onnx.py` (torch 2.14.1, opset 17, legacy exporter)

## Files
| ONNX file | Variant | Crop scale | Params | Size (B) | SHA-256 |
|---|---|---|---|---|---|
| `2.7_80x80_MiniFASNetV2.onnx` | MiniFASNetV2 | 2.7 | 434,560 | 1,744,241 | `fe59ec8048b16a1d9be0139d4d03749078053aa2e1e57c6f333957895886c866` |
| `4_0_0_80x80_MiniFASNetV1SE.onnx` | MiniFASNetV1SE | 4.0 | 433,586 | 1,743,409 | `2b45c7b23879e1af72adfb565b493cd33704c57984b8e63f9e39db61057f2342` |

Source weights (upstream `.pth`, for provenance):
- `2.7_80x80_MiniFASNetV2.pth` — `a5eb02e1843f19b5386b953cc4c9f011c3f985d0ee2bb9819eea9a142099bec0`
- `4_0_0_80x80_MiniFASNetV1SE.pth` — `84ee1d37d96894d5e82de5a57df044ef80a58be2b218b5ed7cdfd875ec2f5990`

Export is deterministic: re-running `export_onnx.py` (2026-10-02, same torch/onnx versions) reproduced both SHA-256 values exactly.
On load, both the Python and Java wrappers check each graph against its file name (input `input` [N,3,H,W], output `logits` [N,3]).

Scale and input size are encoded in the file name and parsed by `src/utility.py:parse_model_name`.

## Input spec (from `test.py`, `generate_patches.py`, `data_io/functional.py`)
- Tensor name `input`, shape `[batch, 3, 80, 80]`, **float32**, **NCHW**.
- **Channel order BGR** (OpenCV `imread` order; no conversion anywhere in upstream).
- **No normalization, no /255**: values stay in 0..255 (`to_tensor` returns `img.float()`; the `div(255)` is commented out).
- Crop: face box scaled around its center by the per-model scale (see `CropImage._get_new_box`), shifted (not padded) to stay inside
  the frame, resized to 80x80. Upstream uses `cv2.resize` (bilinear); this module uses a canonical integer bilinear (OpenCV's generic
  fixed-point algorithm) because cv2.resize output is platform-dependent (+-1 px). Measured effect vs upstream: <= 5.5e-4 in real_score.

## Output spec
- Tensor name `logits`, shape `[batch, 3]`, raw logits. Softmax (over the 3 classes) is applied outside the graph.
- Class index **1 = real face**; the other two indices are spoof (what each spoof index means: **UNVERIFIED**, upstream does not document it).
- Fusion (`test.py`): sum of the two models' softmax vectors, `label = argmax`, `score = sum[label] / 2`.
  Note: that score is the confidence of the *winning* class, so a "0.9" can mean 0.9 real OR 0.9 fake. Our API will expose `real_score`
  (= averaged class-1 probability) separately.

## Verified in this repo
- ONNX vs PyTorch, 5 random inputs per model: max |logit diff| 2.62e-06 (V2), 1.43e-06 (V1SE).
- Full pipeline parity (17 real/edge-case frames): upstream PyTorch vs this module 5.5e-4; Python vs Java 4.3e-13
  (`minifasnet/tests/out/parity_report.md`).

## License verdict (OSI-approved only rule)
| Item | Finding | Verdict |
|---|---|---|
| Upstream code | `LICENSE` is Apache License 2.0 | OK (OSI) |
| Pretrained weights | No separate license or terms found in repo / README | **UNCLEAR — blocker pending your decision** |
| Training data | Not released; README documents only a directory layout; origin/consent/license not stated | **UNCLEAR — blocker pending your decision** |
| Weights as derivative of Apache code | Apache-2.0 covers code in the repo; whether it also covers the binary weight files: **UNVERIFIED** (needs legal read) | **UNVERIFIED** |

Other facts: README says the repo "open sources the model with training architecture" and a closed "High precision model" exists (private) —
the open models are the weaker ones. README warns results depend on camera model and scene.

## Training (read from upstream code; NOT reproduced here)
- `src/train_main.py` + `src/default_config.py`: SGD lr 0.1, momentum 0.9, 25 epochs, lr x0.1 at epochs 10/15/22, batch 1024, 3 classes.
- Loss `0.5 * CrossEntropy + 0.5 * MSE(Fourier map)`: `MultiFTNet` adds a small conv head (`FTGenerator`) on the features after `conv_4`
  that must predict the log-magnitude FFT of the grayscale input (min-max normalised, resized to 10x10; `data_io/dataset_folder.py:generate_FT`).
  The head runs only in training (`if self.training`), so it is not part of the exported ONNX graph and costs nothing at inference.
- Augmentation: RandomResizedCrop, ColorJitter, RandomRotation(10), RandomHorizontalFlip. Data layout: `<scale>_80x80/{0,1,2}/*.png` patches.
- **Mismatch:** the training script always builds `MiniFASNetV2SE`, but the shipped weights are V2 and V1SE — the exact recipe that produced
  the shipped models is not in the repo (**UNVERIFIED** how they were trained). Training data is not released.

## Known limits (from upstream README + design)
- RGB single-frame passive model; trained/tested for camera-captured images (README: images must come from a camera).
- Face should be fully visible, rotation < 30 degrees (README).
- Domain shift, lighting, demographic bias, 3D mask / video-injection resistance: **UNVERIFIED — to be measured with our own data (Step 5).**
