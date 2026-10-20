"""Tests for the dev-only demo dashboard (dashboard/server.py): self-check, predict, detectors, HTTP error handling."""
import json
import os
import sys
import threading
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

import cv2
import numpy as np
import pytest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "python"))
sys.path.insert(0, os.path.join(HERE, "..", "dashboard"))
from minifasnet_pad import DEFAULT_MODEL_DIR  # noqa: E402
from server import (  # noqa: E402
    SAMPLE_DIR,
    App,
    RetinaFaceBoxes,
    make_handler,
    model_card_hashes,
    run_selfcheck,
)

GRAY = np.full((640, 480, 3), 128, np.uint8)
needs_upstream = pytest.mark.skipif(not RetinaFaceBoxes.available(),
                                    reason="upstream clone (minifasnet-study/) missing")


@pytest.fixture(scope="module")
def app() -> App:
    return App(DEFAULT_MODEL_DIR)


def jpeg(img: np.ndarray) -> bytes:
    ok, buf = cv2.imencode(".jpg", img)
    assert ok
    return buf.tobytes()


def test_model_card_hashes_cover_both_models() -> None:
    h = model_card_hashes(DEFAULT_MODEL_DIR)
    assert set(h) == {"2.7_80x80_MiniFASNetV2.onnx", "4_0_0_80x80_MiniFASNetV1SE.onnx"}
    assert all(len(v) == 64 for v in h.values())


def test_selfcheck_passes_on_shipped_models(app: App) -> None:
    r = run_selfcheck(app.pad, DEFAULT_MODEL_DIR)
    assert r["ok"], r
    status = {c["name"]: c["status"] for c in r["checks"]}
    assert status["Model files match MODEL_CARD SHA-256"] == "pass"
    assert status["Golden score (gray frame)"] == "pass"


def test_selfcheck_detects_a_tampered_model(app: App, tmp_path) -> None:
    # Copy models + card, flip one byte in a model: the hash check must FAIL (dashboard must not say "working").
    for name in os.listdir(DEFAULT_MODEL_DIR):
        with open(os.path.join(DEFAULT_MODEL_DIR, name), "rb") as fh:
            data = fh.read()
        if name == "2.7_80x80_MiniFASNetV2.onnx":
            data = data[:-1] + bytes([data[-1] ^ 0xFF])
        (tmp_path / name).write_bytes(data)
    r = run_selfcheck(app.pad, str(tmp_path))
    assert not r["ok"]
    assert any(c["name"].startswith("Model files match") and c["status"] == "fail" for c in r["checks"])


def test_info(app: App) -> None:
    info = app.info()
    assert len(info["models"]) == 2 and "haar" in info["detectors"]
    assert info["default_detector"] in info["detectors"]


def test_predict_manual_box(app: App) -> None:
    code, out = app.predict(jpeg(GRAY), {"x": ["150"], "y": ["200"], "w": ["150"], "h": ["180"]})
    assert code == 200 and out["status"] == "ok" and out["box_source"] == "manual"
    assert 0 <= out["real_score"] <= 1 and len(out["per_model"]) == 2 and out["frame"] == {"width": 480, "height": 640}


@pytest.mark.parametrize("detector", ["retinaface", "haar"])
def test_blank_frame_is_no_face_not_an_error(app: App, detector: str) -> None:
    code, out = app.predict(jpeg(GRAY), {"detector": [detector]})
    assert code == 200 and out["status"] == "no_face"


@needs_upstream
@pytest.mark.parametrize("name,want", [("image_T1.jpg", "real"), ("image_F1.jpg", "fake"), ("image_F2.jpg", "fake")])
def test_retinaface_detector_on_known_samples(app: App, name: str, want: str) -> None:
    with open(os.path.join(SAMPLE_DIR, name), "rb") as fh:
        code, out = app.predict(fh.read(), {"detector": ["retinaface"]})
    assert code == 200 and out["status"] == "ok" and out["label"] == want, out


def test_bad_inputs(app: App) -> None:
    assert app.predict(b"not an image", {})[0] == 422
    assert app.predict(b"", {})[0] == 422
    code, out = app.predict(jpeg(GRAY), {"x": ["a"], "y": ["1"], "w": ["2"], "h": ["3"]})
    assert code == 422 and out["type"] == "InvalidBoxError"
    code, out = app.predict(jpeg(GRAY), {"x": ["9999"], "y": ["1"], "w": ["50"], "h": ["50"]})
    assert code == 200 and out["status"] == "rejected" and out["type"] == "InvalidBoxError"


def test_http_server_end_to_end(app: App) -> None:
    srv = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(app))
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    base = f"http://127.0.0.1:{srv.server_address[1]}"
    try:
        with urllib.request.urlopen(base + "/") as r:
            assert r.status == 200 and b"MiniFASNet" in r.read()
        with urllib.request.urlopen(base + "/api/selfcheck") as r:
            assert json.load(r)["ok"]
        req = urllib.request.Request(base + "/api/predict?x=150&y=200&w=150&h=180", data=jpeg(GRAY), method="POST")
        with urllib.request.urlopen(req) as r:
            assert json.load(r)["status"] == "ok"
        with pytest.raises(urllib.error.HTTPError) as e:
            urllib.request.urlopen(urllib.request.Request(base + "/api/predict", data=b"x", method="POST"))
        assert e.value.code == 422
        e.value.close()
    finally:
        srv.shutdown()
        srv.server_close()
