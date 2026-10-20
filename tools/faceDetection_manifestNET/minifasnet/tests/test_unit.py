"""Unit tests for the Python reference (no upstream clone or Java needed)."""
import os
import shutil
import sys
import tempfile

import numpy as np
import pytest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "python"))
from minifasnet_pad import (  # noqa: E402
    DEFAULT_MODEL_DIR,
    FaceTooSmallError,
    InvalidBoxError,
    InvalidFrameError,
    MiniFASNetPAD,
    ModelLoadError,
    _parse_model_name,
    get_new_box,
    resize_bilinear_u8,
    to_tensor,
)

GRAY = np.full((640, 480, 3), 128, np.uint8)


@pytest.fixture(scope="module")
def pad() -> MiniFASNetPAD:
    return MiniFASNetPAD()


def test_model_name_parsing_matches_upstream() -> None:
    assert _parse_model_name("2.7_80x80_MiniFASNetV2.onnx") == (80, 80, 2.7)
    assert _parse_model_name("4_0_0_80x80_MiniFASNetV1SE.onnx") == (80, 80, 4.0)
    for bad in ("weird.onnx", "1..2_80x80_X.onnx", "0_80x80_X.onnx", "org_1_80x60_X.onnx"):
        with pytest.raises(ModelLoadError):
            _parse_model_name(bad)


def test_new_box_values() -> None:
    assert get_new_box(480, 640, (105, 147, 207, 214), 2.7) == (0, 6, 479, 501)
    assert get_new_box(480, 640, (400, 500, 100, 120), 4.0) == (79, 159, 479, 639)


def test_resize_identity_and_constant() -> None:
    img = np.random.default_rng(0).integers(0, 256, (80, 80, 3), dtype=np.uint8)
    assert np.array_equal(resize_bilinear_u8(img, 80, 80), img)
    assert np.all(resize_bilinear_u8(np.full((300, 200, 3), 77, np.uint8), 80, 80) == 77)


def test_to_tensor_is_bgr_nchw_unnormalized() -> None:
    patch = np.zeros((80, 80, 3), np.uint8)
    patch[..., 0], patch[..., 2] = 10, 200  # B=10, R=200
    t = to_tensor(patch)
    assert t.shape == (1, 3, 80, 80) and t.dtype == np.float32
    assert t[0, 0, 0, 0] == 10 and t[0, 2, 0, 0] == 200  # channel 0 stays B, no /255


def test_valid_prediction(pad: MiniFASNetPAD) -> None:
    r = pad.predict(GRAY, (150, 200, 150, 180))
    assert 0 <= r.real_score <= 1 and r.label in ("real", "fake") and len(r.per_model_scores) == 2


def test_one_thread_gives_same_score(pad: MiniFASNetPAD) -> None:
    one = MiniFASNetPAD(intra_op_threads=1)
    box = (150, 200, 150, 180)
    assert abs(one.predict(GRAY, box).real_score - pad.predict(GRAY, box).real_score) < 1e-9


def test_non_contiguous_frame_is_accepted(pad: MiniFASNetPAD) -> None:
    big = np.full((640, 960, 3), 128, np.uint8)
    view = big[:, ::2]  # strided view, same pixels as GRAY
    assert abs(pad.predict(view, (150, 200, 150, 180)).real_score
               - pad.predict(GRAY, (150, 200, 150, 180)).real_score) < 1e-12


@pytest.mark.parametrize("frame,box,err", [
    (None, (1, 1, 50, 50), InvalidFrameError),
    (np.zeros((0, 0, 3), np.uint8), (1, 1, 50, 50), InvalidFrameError),
    (np.zeros((1, 1, 3), np.uint8), (0, 0, 1, 1), InvalidFrameError),
    (GRAY.astype(np.float32), (1, 1, 50, 50), InvalidFrameError),
    (GRAY[..., 0], (1, 1, 50, 50), InvalidFrameError),
    (GRAY, (1, 1, 50), InvalidBoxError),
    (GRAY, None, InvalidBoxError),
    (GRAY, "abc", InvalidBoxError),
    (GRAY, "1234", InvalidBoxError),
    (GRAY, (float("nan"), 1, 50, 50), InvalidBoxError),
    (GRAY, (1, 1, float("inf"), 50), InvalidBoxError),
    (GRAY, (10, 10, 0, 50), InvalidBoxError),
    (GRAY, (5000, 5000, 60, 60), InvalidBoxError),
    (GRAY, (10, 10, 10, 10), FaceTooSmallError),
])
def test_invalid_inputs(pad: MiniFASNetPAD, frame, box, err) -> None:
    with pytest.raises(err):
        pad.predict(frame, box)


def test_bad_construction() -> None:
    with pytest.raises(ModelLoadError):
        MiniFASNetPAD("/nonexistent")
    with pytest.raises(ValueError):
        MiniFASNetPAD(min_face_size=-1)
    with pytest.raises(ValueError):
        MiniFASNetPAD(intra_op_threads=-1)


def test_renamed_model_with_wrong_size_is_rejected() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        src = os.path.join(DEFAULT_MODEL_DIR, "2.7_80x80_MiniFASNetV2.onnx")
        shutil.copy(src, os.path.join(tmp, "2.7_96x96_MiniFASNetV2.onnx"))  # name claims 96x96, graph is 80x80
        with pytest.raises(ModelLoadError):
            MiniFASNetPAD(tmp)
