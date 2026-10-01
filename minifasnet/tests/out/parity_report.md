# Parity report (Step 4)

Target: max |real_score diff| < 0.001. Cases: 17 valid + 3 error.

- PyTorch vs Python ONNX: **5.484e-04**
- Python ONNX vs Java: **4.273e-13**
- PyTorch vs Java: **5.484e-04**

Why PyTorch-vs-ours is not ~0: upstream uses cv2.resize, whose output depends on the OpenCV build
(this machine: arm64 build with Carotene/KleidiCV HAL). We use one canonical integer resize in both
Python and Java, so Python-vs-Java is exact on every platform.

## Canonical resize, pixel level (random 8-bit patches -> 80x80)

| src size | Java vs Python max px diff | cv2 vs canonical max px diff | cv2 differing px fraction |
|---|---|---|---|
| (240, 248) | 0 | 1 | 0.127 |
| (91, 91) | 0 | 1 | 0.256 |
| (133, 133) | 0 | 1 | 0.259 |
| (160, 160) | 0 | 1 | 0.376 |
| (100, 120) | 0 | 1 | 0.301 |
| (81, 79) | 0 | 1 | 0.004 |
| (60, 70) | 0 | 1 | 0.004 |
| (271, 325) | 0 | 0 | 0.000 |
| (480, 496) | 0 | 0 | 0.000 |
| (80, 80) | 0 | 0 | 0.000 |
| (33, 40) | 0 | 1 | 0.005 |
| (700, 699) | 0 | 0 | 0.000 |
| (2, 2) | 0 | 1 | 0.032 |
| (1000, 90) | 0 | 0 | 0.000 |

## Scores

| case | box | PyTorch | Python ONNX | Java | max diff |
|---|---|---|---|---|---|
| image_F1.jpg | (178.0, 136.0, 222.0, 224.0) | 0.084346 | 0.084346 | 0.084346 | 4.74e-07 |
| image_F2.jpg | (120.0, 252.0, 252.0, 256.0) | 0.181286 | 0.181286 | 0.181286 | 6.12e-08 |
| image_T1.jpg | (105.0, 147.0, 207.0, 214.0) | 0.994769 | 0.994769 | 0.994769 | 6.90e-08 |
| T1_dark | (105.0, 147.0, 207.0, 214.0) | 0.999628 | 0.999628 | 0.999628 | 2.76e-08 |
| T1_overexposed | (105.0, 147.0, 207.0, 214.0) | 0.686433 | 0.686431 | 0.686431 | 1.50e-06 |
| T1_noise | (105.0, 147.0, 207.0, 214.0) | 0.406205 | 0.406205 | 0.406205 | 1.01e-07 |
| T1_blur | (105.0, 147.0, 207.0, 214.0) | 0.971616 | 0.971616 | 0.971616 | 6.66e-08 |
| T1_half_res | (52.5, 73.5, 103.5, 107.0) | 0.990435 | 0.989886 | 0.989886 | 5.48e-04 |
| T1_square_crop | (105.0, 67.0, 207.0, 214.0) | 0.998270 | 0.998270 | 0.998270 | 3.48e-08 |
| T1_odd_size | (100.0, 141.0, 180.0, 130.0) | 0.998717 | 0.998717 | 0.998717 | 2.33e-08 |
| T1_corner_box | (0.0, 0.0, 100.0, 120.0) | 0.999977 | 0.999977 | 0.999977 | 1.48e-08 |
| T1_bottom_right | (400.0, 500.0, 100.0, 120.0) | 0.918442 | 0.918442 | 0.918442 | 3.35e-07 |
| T1_partly_outside | (-20.0, -20.0, 120.0, 120.0) | 0.999407 | 0.999407 | 0.999407 | 1.00e-08 |
| F1_huge_box | (10.0, 10.0, 460.0, 620.0) | 0.195524 | 0.195524 | 0.195524 | 4.34e-07 |
| F1_fractional | (150.5, 200.25, 180.5, 190.75) | 0.168217 | 0.168217 | 0.168217 | 3.11e-07 |
| T1_min_face_33 | (180.0, 200.0, 33.0, 33.0) | 0.996163 | 0.995847 | 0.995847 | 3.17e-04 |
| gray_frame | (150.0, 200.0, 150.0, 180.0) | 0.420989 | 0.420990 | 0.420990 | 1.37e-06 |
| err_tiny_face | (10, 10, 10, 10) | (no validation) | FaceTooSmallError | FACE_TOO_SMALL | - |
| err_outside | (5000, 5000, 60, 60) | (no validation) | InvalidBoxError | INVALID_BOX | - |
| err_zero_size | (10, 10, 0, 50) | (no validation) | InvalidBoxError | INVALID_BOX | - |
