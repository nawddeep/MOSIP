#!/bin/bash
# Start the DEV-ONLY demo dashboard with the project's own Python (the macOS system python3 is broken/silent here)
# and open it in the browser.   Usage: minifasnet/dashboard/run.sh [port]      (NO_BROWSER=1 to skip opening it)
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PY="$ROOT/.venv/bin/python"
PORT="${1:-8765}"

if [ ! -x "$PY" ]; then
  echo "ERROR: $PY not found. Create it first (in the project folder):" >&2
  echo "  python3.11 -m venv .venv && .venv/bin/pip install -r minifasnet/requirements-dev.txt" >&2
  exit 1
fi
if ! "$PY" -c "import cv2, numpy, onnxruntime" 2>/dev/null; then
  echo "ERROR: missing packages. Run: .venv/bin/pip install -r minifasnet/requirements-dev.txt" >&2
  exit 1
fi

LOG="$(mktemp)"
echo "Starting the MiniFASNet demo dashboard... (keep this window open; Ctrl+C to stop)"
# Server output goes to the screen and to $LOG (to read the URL). The server itself picks a free port.
"$PY" -u "$ROOT/minifasnet/dashboard/server.py" --port "$PORT" > >(tee "$LOG") 2>&1 &
SERVER=$!
# Background jobs in scripts ignore Ctrl+C, so stop the server explicitly on Ctrl+C / window close / exit.
stop() { kill "$SERVER" 2>/dev/null; wait "$SERVER" 2>/dev/null; rm -f "$LOG"; }
trap 'echo; echo "Stopping dashboard."; stop; exit 0' INT TERM HUP
trap stop EXIT

URL=""
for _ in $(seq 150); do
  URL="$(grep -o 'http://127\.0\.0\.1:[0-9]*' "$LOG" | head -1)"
  [ -n "$URL" ] && break
  kill -0 "$SERVER" 2>/dev/null || break   # server exited early (error already printed above)
  sleep 0.1
done
if [ -z "$URL" ]; then
  echo "ERROR: the dashboard did not start - see the messages above." >&2
  exit 1
fi
if [ "${NO_BROWSER:-0}" != 1 ]; then
  open "$URL" 2>/dev/null || echo "Open $URL in your browser."
fi
wait "$SERVER"
