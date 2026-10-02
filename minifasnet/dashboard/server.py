"""DEV-ONLY demo dashboard for the MiniFASNet passive-liveness module: live camera, self-check, evidence.

    minifasnet/dashboard/run.sh [port]        (recommended: uses the venv, opens the browser)
    .venv/bin/python minifasnet/dashboard/server.py [--port 8765] [--model-dir DIR]   then open http://127.0.0.1:8765

Not for production: localhost only, images kept in memory only (never written to disk), dev face detectors.

Endpoints:  GET  /                 the page
            GET  /api/info         model facts, detectors, versions, verified parity numbers
            GET  /api/selfcheck    health checks (hashes, golden score, determinism, error handling, known samples)
            POST /api/predict      body = JPEG/PNG bytes; query: detector=retinaface|haar, or x,y,w,h (manual box)
"""
import argparse
import hashlib
import json
import math
import os
import re
import sys
import threading
import time
import traceback
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Callable, Dict, List, Optional, Tuple
from urllib.parse import parse_qs, urlparse

import cv2
import numpy as np
import onnxruntime as ort

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(MOD, ".."))
sys.path.insert(0, os.path.join(MOD, "python"))
from dev_box import dev_get_box  # noqa: E402
from minifasnet_pad import DEFAULT_MODEL_DIR, MiniFASNetPAD, PADError  # noqa: E402

Box = Tuple[float, float, float, float]
MAX_BODY = 12 * 1024 * 1024  # bytes
UPSTREAM = os.path.join(ROOT, "minifasnet-study", "Silent-Face-Anti-Spoofing")
SAMPLE_DIR = os.path.join(UPSTREAM, "images", "sample")
RETINA_DIR = os.path.join(UPSTREAM, "resources", "detection_model")
PARITY_REPORT = os.path.join(MOD, "tests", "out", "parity_report.md")
# Known upstream samples + upstream RetinaFace boxes (tests/out/parity_report.md): name, box, expected label.
KNOWN_SAMPLES = [("image_T1.jpg", (105, 147, 207, 214), "real"),
                 ("image_F1.jpg", (178, 136, 222, 224), "fake"),
                 ("image_F2.jpg", (120, 252, 252, 256), "fake")]
# Golden value: uniform gray 480x640 frame, box (150,200,150,180) -> real_score 0.420990 (parity_report "gray_frame").
GOLDEN_GRAY = (0.420990, 1e-3)


class RetinaFaceBoxes:
    """DEV-ONLY. The face detector the upstream repo uses (src/anti_spoof_predict.py: Detection.get_bbox), so the
    boxes match what the model saw in the upstream pipeline. Files come from the upstream clone (license: UNCLEAR,
    same status as the weights). Returns the most confident face, or None below the confidence threshold."""

    def __init__(self, conf_threshold: float = 0.6):
        proto = os.path.join(RETINA_DIR, "deploy.prototxt")
        model = os.path.join(RETINA_DIR, "Widerface-RetinaFace.caffemodel")
        self.net = cv2.dnn.readNetFromCaffe(proto, model)
        self.conf_threshold = conf_threshold  # upstream defines 0.6 but never applies it; we do (no-face detection)
        self.lock = threading.Lock()  # one cv2.dnn Net must not run from several threads at once

    @staticmethod
    def available() -> bool:
        return os.path.isfile(os.path.join(RETINA_DIR, "Widerface-RetinaFace.caffemodel"))

    def detect(self, img: np.ndarray) -> Tuple[Optional[Box], int]:
        """-> (best box or None, number of faces above the threshold). Same resize/mean as upstream get_bbox."""
        h, w = img.shape[:2]
        small = img
        if w * h >= 192 * 192:
            ar = w / h
            small = cv2.resize(img, (int(192 * math.sqrt(ar)), int(192 / math.sqrt(ar))),
                               interpolation=cv2.INTER_LINEAR)
        blob = cv2.dnn.blobFromImage(small, 1, mean=(104, 117, 123))
        with self.lock:
            self.net.setInput(blob, "data")
            out = np.asarray(self.net.forward("detection_out")).reshape(-1, 7)
        confident = out[out[:, 2] >= self.conf_threshold]
        if len(confident) == 0:
            return None, 0
        best = confident[int(np.argmax(confident[:, 2]))]
        left, top, right, bottom = best[3] * w, best[4] * h, best[5] * w, best[6] * h
        box = (float(int(left)), float(int(top)), float(int(right - left + 1)), float(int(bottom - top + 1)))
        return box, int(len(confident))


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


def parity_summary() -> List[str]:
    """The headline '- X vs Y: **n**' lines of tests/out/parity_report.md (verified numbers, not recomputed)."""
    if not os.path.isfile(PARITY_REPORT):
        return []
    with open(PARITY_REPORT, encoding="utf-8") as fh:
        return [ln.strip("- \n").replace("**", "") for ln in fh if re.match(r"^- .+ vs .+: \*\*", ln)]


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


class App:
    """Shared state for the request handlers (ORT sessions are thread-safe; RetinaFace has its own lock)."""

    def __init__(self, model_dir: str, min_face_size: int = 32):
        self.model_dir = model_dir
        self.pad = MiniFASNetPAD(model_dir, min_face_size=min_face_size)
        self.retina = RetinaFaceBoxes() if RetinaFaceBoxes.available() else None

    def info(self) -> Dict[str, Any]:
        hashes = model_card_hashes(self.model_dir)
        return {
            "models": [{"name": m.name, "scale": m.scale, "input": f"1x3x{m.height}x{m.width} BGR 0..255",
                        "size_bytes": os.path.getsize(os.path.join(self.model_dir, m.name)),
                        "sha256": hashes.get(m.name, "")} for m in self.pad.models],
            "detectors": (["retinaface"] if self.retina else []) + ["haar"],
            "default_detector": "retinaface" if self.retina else "haar",
            "versions": {"onnxruntime": ort.__version__, "numpy": np.__version__, "opencv": cv2.__version__,
                         "python": sys.version.split()[0]},
            "parity": parity_summary(),
            "min_face_size": self.pad.min_face_size,
        }

    def find_box(self, img: np.ndarray, detector: str) -> Tuple[Optional[Box], int, str]:
        if detector == "retinaface" and self.retina is not None:
            box, n = self.retina.detect(img)
            return box, n, "retinaface (upstream, dev-only)"
        box = dev_get_box(img)
        return box, int(box is not None), "haar (dev-only)"

    def predict(self, body: bytes, query: Dict[str, List[str]]) -> Tuple[int, Dict[str, Any]]:
        """Bad input -> 422; 'no face' / 'box rejected' are normal live-camera states -> 200 with a status."""
        t0 = time.perf_counter()
        arr = np.frombuffer(body, np.uint8)
        img = cv2.imdecode(arr, cv2.IMREAD_COLOR) if arr.size else None
        if img is None:
            return 422, {"error": "could not decode the image (use JPEG/PNG)", "type": "DecodeError"}
        frame = {"width": int(img.shape[1]), "height": int(img.shape[0])}
        faces = 1
        if all(k in query for k in ("x", "y", "w", "h")):
            try:
                box: Optional[Box] = (float(query["x"][0]), float(query["y"][0]),
                                      float(query["w"][0]), float(query["h"][0]))
            except ValueError:
                return 422, {"error": "x, y, w, h must be numbers", "type": "InvalidBoxError", "frame": frame}
            source = "manual"
        else:
            box, faces, source = self.find_box(img, query.get("detector", ["retinaface"])[0])
            if box is None:
                return 200, {"status": "no_face", "box_source": source, "frame": frame, "faces": 0,
                             "total_ms": (time.perf_counter() - t0) * 1e3}
        t1 = time.perf_counter()
        try:
            r = self.pad.predict(img, box)
        except PADError as e:
            return 200, {"status": "rejected", "error": str(e), "type": type(e).__name__, "box": list(box),
                         "box_source": source, "frame": frame, "faces": faces}
        t2 = time.perf_counter()
        return 200, {"status": "ok", "real_score": r.real_score, "label": r.label,
                     "label_confidence": r.label_confidence,
                     "per_model": [{"name": m.name, "scale": m.scale, "probs": m.probs, "real_prob": m.real_prob}
                                   for m in r.per_model_scores],
                     "box": list(box), "box_source": source, "faces": faces, "frame": frame,
                     "model_ms": (t2 - t1) * 1e3, "total_ms": (t2 - t0) * 1e3}


def make_handler(app: App):
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

        def _guard(self, fn: Callable[[], None]) -> None:
            """Never drop a connection: any unexpected exception becomes a JSON 500 and is printed."""
            try:
                fn()
            except (BrokenPipeError, ConnectionResetError):
                pass  # browser went away (e.g. tab closed mid-request)
            except Exception as e:  # last-resort guard for a dev server
                traceback.print_exc()
                try:
                    self._json(500, {"error": f"server error: {type(e).__name__}: {e}", "type": "ServerError"})
                except OSError:
                    pass

        def _get(self) -> None:
            path = urlparse(self.path).path
            if path == "/":
                self._send(200, page, "text/html; charset=utf-8")
            elif path == "/api/info":
                self._json(200, app.info())
            elif path == "/api/selfcheck":
                self._json(200, run_selfcheck(app.pad, app.model_dir))
            else:
                self._json(404, {"error": "not found"})

        def _post(self) -> None:
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
            code, obj = app.predict(self.rfile.read(length), parse_qs(url.query))
            self._json(code, obj)

        def do_GET(self) -> None:  # noqa: N802 (http.server API)
            self._guard(self._get)

        def do_POST(self) -> None:  # noqa: N802
            self._guard(self._post)

        def log_message(self, format: str, *args: Any) -> None:  # noqa: A002 (name fixed by the base class)
            pass  # quiet: no per-request logging (errors are printed by _guard)

    return Handler


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--model-dir", default=DEFAULT_MODEL_DIR)
    ap.add_argument("--min-face-size", type=int, default=32)
    a = ap.parse_args()
    app = App(a.model_dir, a.min_face_size)
    srv = ThreadingHTTPServer(("127.0.0.1", a.port), make_handler(app))
    print(f"MiniFASNet demo dashboard (dev-only): http://127.0.0.1:{a.port}", flush=True)
    print(f"face detector: {'retinaface (upstream)' if app.retina else 'haar (upstream clone not found)'}", flush=True)
    print("keep this terminal open while demoing; Ctrl+C to stop", flush=True)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        srv.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
