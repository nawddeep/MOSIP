"""DEV-ONLY helper -- NOT FOR PRODUCTION.

Gets a face box from OpenCV's bundled Haar cascade so you can try the module quickly.
Its box definition differs from upstream's RetinaFace and from MediaPipe, so scores will
differ from the upstream demo. Production callers must supply their own box.
"""
import sys
import cv2
from minifasnet_pad import MiniFASNetPAD


def dev_get_box(image_bgr):
    """Return the largest (x, y, w, h) Haar face box, or None if no face found."""
    cascade = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")
    gray = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2GRAY)
    faces = cascade.detectMultiScale(gray, 1.1, 5, minSize=(40, 40))
    if len(faces) == 0:
        return None
    return tuple(int(v) for v in max(faces, key=lambda b: b[2] * b[3]))


if __name__ == "__main__":
    img = cv2.imread(sys.argv[1])
    box = dev_get_box(img) if img is not None else None
    print("box (dev-only Haar):", box)
    if box:
        print(MiniFASNetPAD().predict(img, box))
