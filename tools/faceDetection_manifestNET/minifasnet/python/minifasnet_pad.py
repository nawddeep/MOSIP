"""MiniFASNetPAD -- reference implementation (source of truth for the Java port).

Given a BGR frame and a caller-supplied face box, returns a passive real/spoof score.
This module does NOT detect faces. Preprocessing follows the upstream repo
(minivision-ai/Silent-Face-Anti-Spoofing, commit b6d5f04):
  src/generate_patches.py (crop)   src/data_io/functional.py (to_tensor)   test.py (fusion)
with one deliberate change: the resize is a canonical integer bilinear (see resize_bilinear_u8).

Runtime dependencies: numpy + onnxruntime. OpenCV is only imported for the cv2 diagnostic path.
"""
import os
import re
from dataclasses import dataclass, field
from typing import List, Optional, Sequence, Tuple

import numpy as np
import onnxruntime as ort

DEFAULT_MODEL_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "models"))
REAL_CLASS_INDEX = 1  # upstream test.py: `if label == 1: Real Face`
NUM_CLASSES = 3
INPUT_NAME = "input"  # names fixed by python/export_onnx.py
OUTPUT_NAME = "logits"

Box = Tuple[float, float, float, float]


class PADError(Exception):
    """Base class: every input/model problem raises a subclass, never fails silently."""


class InvalidFrameError(PADError):
    """Frame is None/empty, not a uint8 HxWx3 array, or smaller than 2x2."""


class InvalidBoxError(PADError):
    """Box malformed, non-finite, non-positive size, or centered outside the frame."""


class FaceTooSmallError(PADError):
    """Box smaller than min_face_size."""


class ModelLoadError(PADError):
    """Model dir/file missing, file name not parseable, or model I/O not as expected."""


class InferenceError(PADError):
    """ONNX Runtime failed while running a model, or returned an unexpected output."""


@dataclass(frozen=True)
class ModelScore:
    name: str
    scale: float
    probs: List[float]  # softmax over the 3 classes
    real_prob: float  # probs[REAL_CLASS_INDEX]


@dataclass(frozen=True)
class PADResult:
    real_score: float  # mean class-1 probability over the models, 0..1 (threshold this)
    label: str  # "real" | "fake"  (argmax of summed softmax, as upstream)
    label_confidence: float  # upstream's printed score: summed[label] / n_models
    per_model_scores: List[ModelScore] = field(default_factory=list)


@dataclass(frozen=True)
class _Model:
    name: str
    scale: float
    width: int
    height: int
    session: ort.InferenceSession


def _parse_model_name(fname: str) -> Tuple[int, int, float]:
    """'2.7_80x80_MiniFASNetV2.onnx' -> (h=80, w=80, scale=2.7). Same convention as upstream parse_model_name:
    scale = first '_' token ('4_0_0_80x80_...' -> 4.0, not 4.0.0); input size = the 'HxW' token."""
    m = re.match(r"^([0-9.]+)_(?:\d+_)*?(\d+)x(\d+)_.+\.onnx$", fname)
    if not m:
        raise ModelLoadError(f"cannot parse model file name (expected '<scale>_<H>x<W>_<name>.onnx'): {fname}")
    try:
        scale = float(m.group(1))
    except ValueError:
        raise ModelLoadError(f"invalid scale in model file name: {fname}") from None
    h, w = int(m.group(2)), int(m.group(3))
    if scale <= 0 or h <= 0 or w <= 0:
        raise ModelLoadError(f"scale and input size must be positive: {fname}")
    return h, w, scale


def get_new_box(src_w: int, src_h: int, bbox: Sequence[float], scale: float) -> Tuple[int, int, int, int]:
    """Port of upstream CropImage._get_new_box. Returns inclusive (x1, y1, x2, y2) ints."""
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
    Difference vs upstream's cv2.resize is +-1 pixel on a fraction of pixels (measured in tests/out/parity_report.md).
    """
    h, w = patch.shape[:2]
    x0, x1, a0, a1 = _axis_coefs(w, out_w)
    y0, y1, b0, b1 = _axis_coefs(h, out_h)

    def hpass(src_rows: np.ndarray) -> np.ndarray:  # horizontal pass, only on the source rows actually sampled
        p = patch[src_rows].astype(np.int64)
        return p[:, x0, :] * a0[None, :, None] + p[:, x1, :] * a1[None, :, None]

    r = ((((hpass(y0) >> 4) * b0[:, None, None]) >> 16)
         + (((hpass(y1) >> 4) * b1[:, None, None]) >> 16) + 2) >> 2  # vertical pass
    return np.clip(r, 0, 255).astype(np.uint8)


def crop_patch(image_bgr: np.ndarray, bbox: Sequence[float], scale: float, out_w: int, out_h: int,
               use_cv2_resize: bool = False) -> np.ndarray:
    h, w = image_bgr.shape[:2]
    x1, y1, x2, y2 = get_new_box(w, h, bbox, scale)
    patch = image_bgr[y1:y2 + 1, x1:x2 + 1]  # +1: upstream slice end is inclusive
    if patch.size == 0:
        raise InvalidBoxError(f"crop window is empty for box={tuple(bbox)} frame={w}x{h}")
    if use_cv2_resize:  # diagnostics only: platform-dependent, see resize_bilinear_u8
        import cv2  # lazy: OpenCV is not a runtime dependency

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
                 providers: Optional[List[str]] = None, intra_op_threads: int = 0):
        """min_face_size: min(box w, box h) in pixels; my own guard (upstream has none), tune with data.
        intra_op_threads: ONNX Runtime threads per model (0 = ORT default; 1-2 is sensible on low-end devices)."""
        if min_face_size < 0:
            raise ValueError(f"min_face_size must be >= 0, got {min_face_size}")
        if intra_op_threads < 0:
            raise ValueError(f"intra_op_threads must be >= 0, got {intra_op_threads}")
        if not os.path.isdir(model_dir):
            raise ModelLoadError(f"model dir not found: {model_dir}")
        names = sorted(f for f in os.listdir(model_dir) if f.endswith(".onnx"))
        if not names:
            raise ModelLoadError(f"no .onnx files in {model_dir}")
        opts = ort.SessionOptions()
        opts.intra_op_num_threads = intra_op_threads
        self.min_face_size = min_face_size
        self.models: List[_Model] = []
        for n in names:
            h, w, scale = _parse_model_name(n)
            try:
                sess = ort.InferenceSession(os.path.join(model_dir, n), opts,
                                            providers=providers or ["CPUExecutionProvider"])
            except Exception as e:
                raise ModelLoadError(f"failed to load {n}: {e}") from e
            self._check_io(n, sess, h, w)
            self.models.append(_Model(n, scale, w, h, sess))

    @staticmethod
    def _check_io(name: str, sess: ort.InferenceSession, h: int, w: int) -> None:
        """Reject files whose graph does not match the name (wrong/renamed/corrupted model)."""
        inputs, outputs = sess.get_inputs(), sess.get_outputs()
        if len(inputs) != 1 or inputs[0].name != INPUT_NAME or list(inputs[0].shape[1:]) != [3, h, w]:
            raise ModelLoadError(f"{name}: expected one input '{INPUT_NAME}' [N,3,{h},{w}], "
                                 f"got {[(i.name, i.shape) for i in inputs]}")
        if len(outputs) != 1 or outputs[0].name != OUTPUT_NAME or outputs[0].shape[-1] != NUM_CLASSES:
            raise ModelLoadError(f"{name}: expected one output '{OUTPUT_NAME}' [N,{NUM_CLASSES}], "
                                 f"got {[(o.name, o.shape) for o in outputs]}")

    def _validate(self, image: np.ndarray, face_box: Sequence[float]) -> Box:
        # Annotations describe correct use; the checks below still handle anything a caller passes at runtime.
        if not isinstance(image, np.ndarray) or image.size == 0:
            raise InvalidFrameError("frame is None/empty/not a numpy array")
        if image.dtype != np.uint8:
            raise InvalidFrameError(f"frame dtype must be uint8, got {image.dtype}")
        if image.ndim != 3 or image.shape[2] != 3:
            raise InvalidFrameError(f"frame must be HxWx3 BGR, got shape {image.shape}")
        fh, fw = image.shape[:2]
        if fw < 2 or fh < 2:
            raise InvalidFrameError(f"frame too small: {fw}x{fh}")
        if isinstance(face_box, (str, bytes)):
            raise InvalidBoxError(f"face_box must be (x, y, w, h) numbers, got {face_box!r}")
        try:
            x, y, w, h = (float(v) for v in face_box)  # wrong length/types -> ValueError/TypeError
        except (TypeError, ValueError):
            raise InvalidBoxError(f"face_box must be (x, y, w, h) numbers, got {face_box!r}") from None
        if not all(np.isfinite([x, y, w, h])):
            raise InvalidBoxError("face_box contains NaN/inf")
        if w <= 0 or h <= 0:
            raise InvalidBoxError(f"face_box has non-positive size: {face_box}")
        if not (0 <= x + w / 2 < fw and 0 <= y + h / 2 < fh):
            raise InvalidBoxError(f"face_box center is outside the {fw}x{fh} frame: {face_box}")
        if min(w, h) < self.min_face_size:
            raise FaceTooSmallError(f"face {w:.0f}x{h:.0f} < min_face_size {self.min_face_size}")
        return x, y, w, h

    def predict(self, image_bgr: np.ndarray, face_box: Sequence[float]) -> PADResult:
        box = self._validate(image_bgr, face_box)
        total = np.zeros(NUM_CLASSES, dtype=np.float64)
        per_model = []
        for m in self.models:
            tensor = to_tensor(crop_patch(image_bgr, box, m.scale, m.width, m.height))
            try:
                out = m.session.run(None, {INPUT_NAME: tensor})
            except Exception as e:
                raise InferenceError(f"inference failed for {m.name}: {e}") from e
            logits = np.asarray(out[0], dtype=np.float64)
            if logits.shape != (1, NUM_CLASSES) or not np.all(np.isfinite(logits)):
                raise InferenceError(f"{m.name}: unexpected output {logits.shape} / non-finite values")
            probs = _softmax(logits[0])
            total += probs
            per_model.append(ModelScore(m.name, m.scale, probs.tolist(), float(probs[REAL_CLASS_INDEX])))
        n = len(self.models)
        idx = int(np.argmax(total))
        return PADResult(
            real_score=float(total[REAL_CLASS_INDEX] / n),
            label="real" if idx == REAL_CLASS_INDEX else "fake",
            label_confidence=float(total[idx] / n),
            per_model_scores=per_model,
        )
