"""MiniFASNetPAD -- reference implementation (source of truth for the Java port).

Given a BGR frame and a caller-supplied face box, returns a passive real/spoof score.
This module does NOT detect faces. Preprocessing mirrors the upstream repo
(minivision-ai/Silent-Face-Anti-Spoofing, commit b6d5f04) line for line:
  src/generate_patches.py  (crop)   src/data_io/functional.py (to_tensor)   test.py (fusion)
"""
import os
import re
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Sequence, Tuple

import cv2
import numpy as np
import onnxruntime as ort

DEFAULT_MODEL_DIR = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "models"))
REAL_CLASS_INDEX = 1  # upstream test.py: `if label == 1: Real Face`


class PADError(Exception):
    """Base class: every input/model problem raises a subclass, never fails silently."""


class InvalidFrameError(PADError):
    """Frame is None/empty, not a uint8 HxWx3 array."""


class InvalidBoxError(PADError):
    """Box malformed, non-positive size, or centered outside the frame."""


class FaceTooSmallError(PADError):
    """Box smaller than min_face_size."""


class ModelLoadError(PADError):
    """Model file missing or name not parseable."""


@dataclass
class ModelScore:
    name: str
    scale: float
    probs: List[float]      # softmax over the 3 classes
    real_prob: float        # probs[REAL_CLASS_INDEX]


@dataclass
class PADResult:
    real_score: float       # mean class-1 probability over both models, 0..1 (use this for thresholding)
    label: str              # "real" | "fake"  (argmax of summed softmax, as upstream)
    label_confidence: float  # upstream's printed score: summed[label]/n_models
    per_model_scores: List[ModelScore] = field(default_factory=list)


def _parse_model_name(fname: str) -> Tuple[int, int, float]:
    """'2.7_80x80_MiniFASNetV2.onnx' -> (h=80, w=80, scale=2.7). Same convention as upstream."""
    # Upstream parse_model_name: scale = float(first '_' token) ('4_0_0_...' -> 4.0, not 4.0.0);
    # input size = the 'HxW' token.
    m = re.match(r"^([0-9.]+)_(?:\d+_)*?(\d+)x(\d+)_.+\.onnx$", fname)
    if not m:
        raise ModelLoadError(f"cannot parse model file name: {fname}")
    return int(m.group(2)), int(m.group(3)), float(m.group(1))


def get_new_box(src_w: int, src_h: int, bbox: Sequence[float], scale: float):
    """Port of CropImage._get_new_box. Returns inclusive (x1, y1, x2, y2) ints."""
    x, y, box_w, box_h = bbox
    # Shrink the scale if the scaled box could not fit inside the frame.
    scale = min((src_h - 1) / box_h, min((src_w - 1) / box_w, scale))
    new_w, new_h = box_w * scale, box_h * scale
    cx, cy = box_w / 2 + x, box_h / 2 + y
    x1, y1 = cx - new_w / 2, cy - new_h / 2
    x2, y2 = cx + new_w / 2, cy + new_h / 2
    # Shift (do not pad) the window back inside the frame.
    if x1 < 0:
        x2 -= x1
        x1 = 0
    if y1 < 0:
        y2 -= y1
        y1 = 0
    if x2 > src_w - 1:
        x1 -= x2 - src_w + 1
        x2 = src_w - 1
    if y2 > src_h - 1:
        y1 -= y2 - src_h + 1
        y2 = src_h - 1
    return int(x1), int(y1), int(x2), int(y2)  # int() truncates toward zero, like upstream


def _axis_coefs(src: int, dst: int):
    """Source index + 11-bit fixed-point weights per output pixel (OpenCV INTER_LINEAR, half-pixel centers).
    float32 steps on purpose: they mirror OpenCV's (and our Java port's) float math exactly."""
    d = np.arange(dst, dtype=np.float64)
    f = ((d + 0.5) * (src / dst) - 0.5).astype(np.float32)
    s = np.floor(f).astype(np.int64)
    f = (f - s.astype(np.float32)).astype(np.float32)
    f[s < 0] = 0
    s[s < 0] = 0
    f[s >= src - 1] = 0
    s[s >= src - 1] = src - 1
    w0 = np.rint((np.float32(1) - f) * np.float32(2048)).astype(np.int64)  # round-half-even like cvRound
    w1 = np.rint(f * np.float32(2048)).astype(np.int64)
    return s, np.minimum(s + 1, src - 1), w0, w1


def resize_bilinear_u8(patch: np.ndarray, out_w: int, out_h: int) -> np.ndarray:
    """CANONICAL resize used by this module (Python and Java implement it identically, integer math only).

    Why not cv2.resize? cv2.resize output differs by +-1 pixel between OpenCV builds/CPUs because builds may route
    it through vendor HALs (this Mac's arm64 build uses Carotene/KleidiCV). That makes "match cv2" impossible to
    guarantee on Android/x86/ARM. This function is OpenCV's generic fixed-point INTER_LINEAR: 11-bit weights,
    horizontal pass in ints, vertical pass with the SIMD rounding ((b0*(r0>>4))>>16 + (b1*(r1>>4))>>16 + 2) >> 2.
    Difference vs upstream's cv2.resize is +-1 pixel on a fraction of pixels (measured in tests/parity report).
    """
    h, w = patch.shape[:2]
    x0, x1, a0, a1 = _axis_coefs(w, out_w)
    y0, y1, b0, b1 = _axis_coefs(h, out_h)
    p = patch.astype(np.int64)
    rows = p[:, x0, :] * a0[None, :, None] + p[:, x1, :] * a1[None, :, None]  # horizontal pass
    r = ((((rows[y0] >> 4) * b0[:, None, None]) >> 16)
         + (((rows[y1] >> 4) * b1[:, None, None]) >> 16) + 2) >> 2              # vertical pass
    return np.clip(r, 0, 255).astype(np.uint8)


def crop_patch(image_bgr: np.ndarray, bbox, scale: float, out_w: int, out_h: int,
               use_cv2_resize: bool = False) -> np.ndarray:
    h, w = image_bgr.shape[:2]
    x1, y1, x2, y2 = get_new_box(w, h, bbox, scale)
    patch = image_bgr[y1:y2 + 1, x1:x2 + 1]  # +1: upstream slice end is inclusive
    if patch.size == 0:
        raise InvalidBoxError(f"crop window is empty for box={tuple(bbox)} frame={w}x{h}")
    if use_cv2_resize:  # diagnostics only: platform-dependent, see resize_bilinear_u8
        return cv2.resize(patch, (out_w, out_h))
    return resize_bilinear_u8(patch, out_w, out_h)


def to_tensor(patch_bgr: np.ndarray) -> np.ndarray:
    """HWC uint8 BGR -> [1,3,H,W] float32. NO /255, NO mean/std, NO BGR->RGB (upstream does none)."""
    return np.ascontiguousarray(patch_bgr.transpose(2, 0, 1)[None].astype(np.float32))


def _softmax(logits: np.ndarray) -> np.ndarray:
    e = np.exp(logits - logits.max(axis=-1, keepdims=True))
    return e / e.sum(axis=-1, keepdims=True)


class MiniFASNetPAD:
    def __init__(self, model_dir: str = DEFAULT_MODEL_DIR, min_face_size: int = 32,
                 providers: Optional[List[str]] = None):
        """min_face_size: min(box w, box h) in pixels; my own guard (upstream has none), tune with data."""
        if not os.path.isdir(model_dir):
            raise ModelLoadError(f"model dir not found: {model_dir}")
        names = sorted(f for f in os.listdir(model_dir) if f.endswith(".onnx"))
        if not names:
            raise ModelLoadError(f"no .onnx files in {model_dir}")
        self.min_face_size = min_face_size
        self.models = []  # (name, scale, in_w, in_h, session)
        for n in names:
            h, w, scale = _parse_model_name(n)
            try:
                sess = ort.InferenceSession(os.path.join(model_dir, n),
                                            providers=providers or ["CPUExecutionProvider"])
            except Exception as e:
                raise ModelLoadError(f"failed to load {n}: {e}") from e
            self.models.append((n, scale, w, h, sess))

    def _validate(self, image, face_box):
        if image is None or not isinstance(image, np.ndarray) or image.size == 0:
            raise InvalidFrameError("frame is None/empty/not a numpy array")
        if image.dtype != np.uint8:
            raise InvalidFrameError(f"frame dtype must be uint8, got {image.dtype}")
        if image.ndim != 3 or image.shape[2] != 3:
            raise InvalidFrameError(f"frame must be HxWx3 BGR, got shape {image.shape}")
        try:
            x, y, w, h = [float(v) for v in face_box]
        except (TypeError, ValueError):
            raise InvalidBoxError(f"face_box must be (x, y, w, h) numbers, got {face_box!r}")
        if not all(np.isfinite([x, y, w, h])):
            raise InvalidBoxError("face_box contains NaN/inf")
        if w <= 0 or h <= 0:
            raise InvalidBoxError(f"face_box has non-positive size: {face_box}")
        fh, fw = image.shape[:2]
        if not (0 <= x + w / 2 < fw and 0 <= y + h / 2 < fh):
            raise InvalidBoxError(f"face_box center is outside the {fw}x{fh} frame: {face_box}")
        if min(w, h) < self.min_face_size:
            raise FaceTooSmallError(f"face {w:.0f}x{h:.0f} < min_face_size {self.min_face_size}")
        if fw < 2 or fh < 2:
            raise InvalidFrameError("frame too small")
        return (x, y, w, h)

    def predict(self, image_bgr: np.ndarray, face_box: Sequence[float]) -> PADResult:
        box = self._validate(image_bgr, face_box)
        total = np.zeros(3, dtype=np.float64)
        per_model = []
        for name, scale, w, h, sess in self.models:
            patch = crop_patch(image_bgr, box, scale, w, h)
            logits = sess.run(None, {"input": to_tensor(patch)})[0][0]
            probs = _softmax(logits.astype(np.float64))
            total += probs
            per_model.append(ModelScore(name, scale, probs.tolist(), float(probs[REAL_CLASS_INDEX])))
        n = len(self.models)
        idx = int(np.argmax(total))
        return PADResult(
            real_score=float(total[REAL_CLASS_INDEX] / n),
            label="real" if idx == REAL_CLASS_INDEX else "fake",
            label_confidence=float(total[idx] / n),
            per_model_scores=per_model,
        )
