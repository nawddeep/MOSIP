#!/bin/bash
# Pre-demo browser check (dev-only): runs the real dashboard page in headless Chrome with a FAKE camera built from the
# upstream sample images, and checks the verdicts. Needs: Google Chrome, Node 22+, the upstream clone, the venv.
# Usage: minifasnet/dashboard/e2e/run_e2e.sh        (exit 0 = all scenarios passed)
set -u
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"; E2E="$ROOT/minifasnet/dashboard/e2e"; PY="$ROOT/.venv/bin/python"
CH="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
SAMP="$ROOT/minifasnet-study/Silent-Face-Anti-Spoofing/images/sample"; PORT=8791
for need in "$PY" "$CH"; do [ -x "$need" ] || { echo "SKIP: missing $need"; exit 2; }; done
command -v node >/dev/null || { echo "SKIP: node not installed"; exit 2; }
[ -d "$SAMP" ] || { echo "SKIP: upstream clone missing ($SAMP)"; exit 2; }
TMP="$(mktemp -d)"
cleanup() { [ -n "${SPID:-}" ] && { kill "$SPID" 2>/dev/null; wait "$SPID" 2>/dev/null; }
            [ -n "${CPID:-}" ] && kill "$CPID" 2>/dev/null; rm -rf "$TMP"; }
trap cleanup EXIT

# Fake cameras (Y4M, generated in a temp dir; face data never enters the repo): T1 = live person, F1 = printed photo.
"$PY" - "$SAMP" "$TMP" <<'PYEOF'
import sys, cv2, numpy as np
samp, tmp = sys.argv[1], sys.argv[2]
for name, out in [("image_T1.jpg", "real.y4m"), ("image_F1.jpg", "attack.y4m")]:
    img = cv2.imread(f"{samp}/{name}"); h, w = img.shape[:2]; rng = np.random.default_rng(0)
    with open(f"{tmp}/{out}", "wb") as f:
        f.write(f"YUV4MPEG2 W{w} H{h} F15:1 Ip A1:1 C420jpeg\n".encode())
        for _ in range(45):  # slight jitter so frames differ like a real camera
            m = np.float32([[1, 0, rng.normal(0, 2)], [0, 1, rng.normal(0, 2)]])
            fr = cv2.warpAffine(img, m, (w, h), borderMode=cv2.BORDER_REPLICATE)
            f.write(b"FRAME\n" + cv2.cvtColor(fr, cv2.COLOR_BGR2YUV_I420).tobytes())
PYEOF

"$PY" -u "$ROOT/minifasnet/dashboard/server.py" --port $PORT > "$TMP/server.log" 2>&1 &
SPID=$!
for _ in $(seq 50); do curl -s -o /dev/null "http://127.0.0.1:$PORT/" && break; sleep 0.2; done
FAIL=0
chrome() { rm -rf "$TMP/prof"; "$CH" --headless=new --remote-debugging-port=$1 --user-data-dir="$TMP/prof" --no-first-run \
  --use-fake-device-for-media-stream --use-fake-ui-for-media-stream --use-file-for-fake-video-capture="$TMP/$2" about:blank >/dev/null 2>&1 &
  CPID=$!; }
expect() { # name, output, expected substring
  if echo "$2" | grep -q "$3"; then echo "PASS  $1"; else echo "FAIL  $1"; echo "      got: $2" | head -c 400; echo; FAIL=1; fi; }

chrome 9361 real.y4m;   OUT=$(node "$E2E/live.mjs" 9361 "http://127.0.0.1:$PORT/" "$TMP/real.png" 1 4000); kill $CPID; wait $CPID 2>/dev/null
expect "live camera, live person -> LIVE PERSON" "$OUT" '"verdict":"LIVE PERSON"'
expect "self-check passed in the page"            "$OUT" '"health":"Self-check passed"'
expect "no JavaScript errors"                      "$OUT" 'logs: \[\]'
chrome 9362 attack.y4m; OUT=$(node "$E2E/live.mjs" 9362 "http://127.0.0.1:$PORT/" "$TMP/attack.png" 1 4000); kill $CPID; wait $CPID 2>/dev/null
expect "live camera, printed photo -> SPOOF"       "$OUT" '"verdict":"SPOOF / ATTACK"'
for s in "image_T1:LIVE PERSON" "image_F1:SPOOF / ATTACK" "image_F2:SPOOF / ATTACK"; do
  chrome 9363 real.y4m; OUT=$(node "$E2E/upload.mjs" 9363 "http://127.0.0.1:$PORT/" "$SAMP/${s%%:*}.jpg"); kill $CPID; wait $CPID 2>/dev/null
  expect "photo upload ${s%%:*} -> ${s#*:}" "$OUT" "\"verdict\":\"${s#*:}\""
done
chrome 9364 real.y4m; OUT=$(node "$E2E/live.mjs" 9364 "file://$ROOT/minifasnet/dashboard/index.html" "$TMP/file.png" 0); kill $CPID; wait $CPID 2>/dev/null
expect "opened as file:// -> clear instructions"   "$OUT" 'Wrong way to open this page'
grep -q Traceback "$TMP/server.log" && { echo "FAIL  server printed a traceback"; FAIL=1; } || echo "PASS  server log has no errors"
[ $FAIL = 0 ] && echo "ALL BROWSER CHECKS PASSED" || echo "SOME BROWSER CHECKS FAILED"
exit $FAIL
