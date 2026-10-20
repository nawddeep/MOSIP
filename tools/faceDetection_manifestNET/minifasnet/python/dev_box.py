"""DEV-ONLY helper -- NOT FOR PRODUCTION.

Gets a face box from OpenCV's bundled Haar cascade so you can try the module quickly.
Its box definition differs from upstream's RetinaFace and from MediaPipe, so scores will
differ from the upstream demo. Production callers must supply their own box.

Usage: python dev_box.py <image>
"""
import os
import sys
from typing import Optional, Tuple

import cv2
import cv2.data  # submodule holding the bundled cascade path (explicit import keeps type checkers happy)
import numpy as np
from minifasnet_pad import MiniFASNetPAD, PADError

_cascade: Optional[cv2.CascadeClassifier] = None


def dev_get_box(image_bgr: np.ndarray) -> Optional[Tuple[float, float, float, float]]:
    """Return the largest (x, y, w, h) Haar face box, or None if no face found."""
    global _cascade
    if _cascade is None:
        path = os.path.join(cv2.data.haarcascades, "haarcascade_frontalface_default.xml")
        _cascade = cv2.CascadeClassifier(path)
        if _cascade.empty():
            raise RuntimeError(f"could not load Haar cascade: {path}")
    gray = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2GRAY)
    faces = _cascade.detectMultiScale(gray, 1.1, 5, minSize=(40, 40))
    if len(faces) == 0:
        return None
    x, y, w, h = max(faces, key=lambda b: b[2] * b[3])
    return float(x), float(y), float(w), float(h)


def main(argv: list) -> int:
    if len(argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    img = cv2.imread(argv[1]) if os.path.isfile(argv[1]) else None
    if img is None:
        print(f"cannot read image: {argv[1]}", file=sys.stderr)
        return 2
    box = dev_get_box(img)
    print("box (dev-only Haar):", box)
    if box is None:
        print("no face found", file=sys.stderr)
        return 1
    try:
        print(MiniFASNetPAD().predict(img, box))
    except PADError as e:
        print(f"{type(e).__name__}: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
