"""DEV-ONLY dashboard: check that the MiniFASNet module works, and try it on images / webcam.

    .venv/bin/python minifasnet/dashboard/server.py [--port 8765] [--model-dir DIR]
    open http://127.0.0.1:8765

Not for production: it uses the Haar dev face box when you do not draw one, listens on localhost only,
and keeps every uploaded image in memory only (nothing is written to disk). Standard library + the module's own deps.

Endpoints:  GET /                 the page
            GET /api/selfcheck    health checks (hashes, golden scores, error handling, known samples)
            POST /api/predict     body = image bytes; query: x,y,w,h (optional box), threshold (optional)
"""
import argparse
import hashlib
import json
import os
import re
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Dict, List, Optional, Tuple
from urllib.parse import parse_qs, urlparse

import cv2
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(MOD, ".."))
sys.path.insert(0, os.path.join(MOD, "python"))
from dev_box import dev_get_box  # noqa: E402
from minifasnet_pad import DEFAULT_MODEL_DIR, MiniFASNetPAD, PADError  # noqa: E402

MAX_BODY = 12 * 1024 * 1024  # bytes
SAMPLE_DIR = os.path.join(ROOT, "minifasnet-study", "Silent-Face-Anti-Spoofing", "images", "sample")
# Known upstream samples + upstream RetinaFace boxes (tests/out/parity_report.md): name, box, expected label.
KNOWN_SAMPLES = [("image_T1.jpg", (105, 147, 207, 214), "real"),
                 ("image_F1.jpg", (178, 136, 222, 224), "fake"),
                 ("image_F2.jpg", (120, 252, 252, 256), "fake")]
# Golden value: uniform gray 480x640 frame, box (150,200,150,180) -> real_score 0.420990 (parity_report "gray_frame").
GOLDEN_GRAY = (0.420990, 1e-3)


def model_card_hashes(model_dir: str) -> Dict[str, str]:
    """{onnx file name: expected sha256} parsed from the MODEL_CARD.md table next to the models."""
    path = os.path.join(model_dir, "MODEL_CARD.md")
    if not os.path.isfile(path):
        return {}
    out: Dict[str, str] = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            m = re.search(r"`([^`]+\.onnx)`.*`([0-9a-f]{64})`", line)
            if m:
                out[m.group(1)] = m.group(2)
    return out


def sha256_of(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def run_selfcheck(pad: MiniFASNetPAD, model_dir: str) -> Dict[str, Any]:
    """Each check -> {name, status: pass|fail|skip, detail}. 'ok' = no failures."""
    checks: List[Dict[str, str]] = []

    def add(name: str, status: str, detail: str) -> None:
        checks.append({"name": name, "status": status, "detail": detail})

    add("Models loaded", "pass", ", ".join(f"{m.name} (scale {m.scale})" for m in pad.models))

    expected = model_card_hashes(model_dir)
    if not expected:
        add("Model files match MODEL_CARD SHA-256", "skip", "no MODEL_CARD.md hashes found next to the models")
    else:
        bad = [m.name for m in pad.models
               if expected.get(m.name) != sha256_of(os.path.join(model_dir, m.name))]
        add("Model files match MODEL_CARD SHA-256", "fail" if bad else "pass",
            f"mismatch or unlisted: {bad}" if bad else f"{len(pad.models)} files verified")

    gray = np.full((640, 480, 3), 128, np.uint8)
    box = (150, 200, 150, 180)
    try:
        a, b = pad.predict(gray, box).real_score, pad.predict(gray, box).real_score
        target, tol = GOLDEN_GRAY
        ok = abs(a - target) < tol
        add("Golden score (gray frame)", "pass" if ok else "fail", f"got {a:.6f}, expected {target:.6f} +- {tol}")
        add("Deterministic", "pass" if a == b else "fail", f"two runs: {a:.9f} / {b:.9f}")
    except PADError as e:
        add("Golden score (gray frame)", "fail", f"{type(e).__name__}: {e}")

    try:
        pad.predict(gray, (10, 10, 0, 50))
        add("Rejects invalid box", "fail", "no error raised for a zero-width box")
    except PADError as e:
        add("Rejects invalid box", "pass", type(e).__name__)

    for name, sbox, want in KNOWN_SAMPLES:
        path = os.path.join(SAMPLE_DIR, name)
        img = cv2.imread(path) if os.path.isfile(path) else None
        if img is None:
            add(f"Known sample {name} -> {want}", "skip", "upstream sample images not found (minifasnet-study/ clone)")
            continue
        try:
            r = pad.predict(img, sbox)
            ok = r.label == want
            add(f"Known sample {name} -> {want}", "pass" if ok else "fail",
                f"real_score {r.real_score:.4f}, label {r.label}")
        except PADError as e:
            add(f"Known sample {name} -> {want}", "fail", f"{type(e).__name__}: {e}")

    return {"ok": not any(c["status"] == "fail" for c in checks), "checks": checks}


def predict_image(pad: MiniFASNetPAD, body: bytes, query: Dict[str, List[str]]) -> Tuple[int, Dict[str, Any]]:
    arr = np.frombuffer(body, np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR) if arr.size else None
    if img is None:
        return 422, {"error": "could not decode the image (use JPEG/PNG)", "type": "DecodeError"}
    box: Optional[Tuple[float, float, float, float]]
    source = "manual"
    if all(k in query for k in ("x", "y", "w", "h")):
        try:
            box = (float(query["x"][0]), float(query["y"][0]), float(query["w"][0]), float(query["h"][0]))
        except ValueError:
            return 422, {"error": "x, y, w, h must be numbers", "type": "InvalidBoxError"}
    else:
        box, source = dev_get_box(img), "haar (dev-only)"
        if box is None:
            return 422, {"error": "no face found by the dev Haar detector - draw a box on the image",
                         "type": "NoFace", "frame": {"width": img.shape[1], "height": img.shape[0]}}
    t0 = time.perf_counter()
    try:
        r = pad.predict(img, box)
    except PADError as e:
        return 422, {"error": str(e), "type": type(e).__name__, "box": list(box),
                     "frame": {"width": img.shape[1], "height": img.shape[0]}}
    ms = (time.perf_counter() - t0) * 1e3
    return 200, {"real_score": r.real_score, "label": r.label, "label_confidence": r.label_confidence,
                 "per_model": [{"name": m.name, "scale": m.scale, "probs": m.probs, "real_prob": m.real_prob}
                               for m in r.per_model_scores],
                 "box": list(box), "box_source": source, "latency_ms": ms,
                 "frame": {"width": img.shape[1], "height": img.shape[0]}}


def make_handler(pad: MiniFASNetPAD, model_dir: str):
    with open(os.path.join(HERE, "index.html"), "rb") as fh:
        page = fh.read()

    class Handler(BaseHTTPRequestHandler):
        def _send(self, code: int, payload: bytes, ctype: str) -> None:
            self.send_response(code)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(payload)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(payload)

        def _json(self, code: int, obj: Dict[str, Any]) -> None:
            self._send(code, json.dumps(obj).encode("utf-8"), "application/json")

        def do_GET(self) -> None:  # noqa: N802 (http.server API)
            path = urlparse(self.path).path
            if path == "/":
                self._send(200, page, "text/html; charset=utf-8")
            elif path == "/api/selfcheck":
                self._json(200, run_selfcheck(pad, model_dir))
            else:
                self._json(404, {"error": "not found"})

        def do_POST(self) -> None:  # noqa: N802
            url = urlparse(self.path)
            if url.path != "/api/predict":
                self._json(404, {"error": "not found"})
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                length = -1
            if length <= 0 or length > MAX_BODY:
                self._json(413 if length > MAX_BODY else 400, {"error": f"body must be 1..{MAX_BODY} bytes"})
                return
            code, obj = predict_image(pad, self.rfile.read(length), parse_qs(url.query))
            self._json(code, obj)

        def log_message(self, format: str, *args: Any) -> None:  # noqa: A002 (name fixed by the base class)
            pass  # quiet: no request logging

    return Handler


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--model-dir", default=DEFAULT_MODEL_DIR)
    ap.add_argument("--min-face-size", type=int, default=32)
    a = ap.parse_args()
    pad = MiniFASNetPAD(a.model_dir, min_face_size=a.min_face_size)
    srv = ThreadingHTTPServer(("127.0.0.1", a.port), make_handler(pad, a.model_dir))
    print(f"MiniFASNet dashboard (dev-only) on http://127.0.0.1:{a.port}  - Ctrl+C to stop")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        srv.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
