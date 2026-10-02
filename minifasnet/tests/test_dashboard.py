"""Tests for the dev-only dashboard logic (dashboard/server.py): self-check, predict endpoint helper, hash parsing."""
import os
import sys

import cv2
import numpy as np
import pytest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "python"))
sys.path.insert(0, os.path.join(HERE, "..", "dashboard"))
from minifasnet_pad import DEFAULT_MODEL_DIR, MiniFASNetPAD  # noqa: E402
from server import model_card_hashes, predict_image, run_selfcheck  # noqa: E402


@pytest.fixture(scope="module")
def pad() -> MiniFASNetPAD:
    return MiniFASNetPAD()


def jpeg(img: np.ndarray) -> bytes:
    ok, buf = cv2.imencode(".jpg", img)
    assert ok
    return buf.tobytes()


def test_model_card_hashes_cover_both_models() -> None:
    h = model_card_hashes(DEFAULT_MODEL_DIR)
    assert set(h) == {"2.7_80x80_MiniFASNetV2.onnx", "4_0_0_80x80_MiniFASNetV1SE.onnx"}
    assert all(len(v) == 64 for v in h.values())


def test_selfcheck_passes_on_shipped_models(pad: MiniFASNetPAD) -> None:
    r = run_selfcheck(pad, DEFAULT_MODEL_DIR)
    assert r["ok"], r
    status = {c["name"]: c["status"] for c in r["checks"]}
    assert status["Model files match MODEL_CARD SHA-256"] == "pass"
    assert status["Golden score (gray frame)"] == "pass"


def test_selfcheck_detects_a_tampered_model(pad: MiniFASNetPAD, tmp_path) -> None:
    # Copy models + card, flip one byte in a model: the hash check must FAIL (dashboard must not say "working").
    for name in os.listdir(DEFAULT_MODEL_DIR):
        with open(os.path.join(DEFAULT_MODEL_DIR, name), "rb") as fh:
            data = fh.read()
        if name == "2.7_80x80_MiniFASNetV2.onnx":
            data = data[:-1] + bytes([data[-1] ^ 0xFF])
        (tmp_path / name).write_bytes(data)
    r = run_selfcheck(pad, str(tmp_path))
    assert not r["ok"]
    assert any(c["name"].startswith("Model files match") and c["status"] == "fail" for c in r["checks"])


def test_predict_manual_box(pad: MiniFASNetPAD) -> None:
    img = np.full((640, 480, 3), 128, np.uint8)
    code, out = predict_image(pad, jpeg(img), {"x": ["150"], "y": ["200"], "w": ["150"], "h": ["180"]})
    assert code == 200 and out["box_source"] == "manual" and 0 <= out["real_score"] <= 1
    assert len(out["per_model"]) == 2 and out["frame"] == {"width": 480, "height": 640}


def test_predict_errors_are_json_not_exceptions(pad: MiniFASNetPAD) -> None:
    img = np.full((640, 480, 3), 128, np.uint8)
    assert predict_image(pad, b"not an image", {})[0] == 422
    assert predict_image(pad, b"", {})[0] == 422
    code, out = predict_image(pad, jpeg(img), {"x": ["9999"], "y": ["1"], "w": ["50"], "h": ["50"]})
    assert code == 422 and out["type"] == "InvalidBoxError"
    code, out = predict_image(pad, jpeg(img), {"x": ["a"], "y": ["1"], "w": ["2"], "h": ["3"]})
    assert code == 422 and out["type"] == "InvalidBoxError"
    code, out = predict_image(pad, jpeg(img), {})  # blank gray frame: Haar finds no face
    assert code == 422 and out["type"] == "NoFace"
