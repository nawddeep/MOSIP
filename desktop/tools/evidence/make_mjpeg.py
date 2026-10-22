#!/usr/bin/env python3
"""Write a multipart MJPEG file from ONE jpeg repeated N times (standard library only).

Used to test the harness's MJPEG parser before a face is involved:
  make_mjpeg.py photo.jpg out.mjpeg [frames=90]
Each part has the same headers ffmpeg's `-f mpjpeg` writes (boundary line, Content-Type, Content-Length).
Never commit the output: it contains whatever image you gave it.
"""
import sys

jpg = open(sys.argv[1], "rb").read()
out = sys.argv[2]
n = int(sys.argv[3]) if len(sys.argv) > 3 else 90
with open(out, "wb") as f:
    for _ in range(n):
        f.write(b"--harnessboundary\r\nContent-Type: image/jpeg\r\nContent-Length: " + str(len(jpg)).encode() + b"\r\n\r\n")
        f.write(jpg)
        f.write(b"\r\n")
    f.write(b"--harnessboundary--\r\n")
print("wrote", n, "frames to", out)
