#!/bin/bash
# Start the DEV-ONLY dashboard with the project's own Python (the macOS system python3 is broken/silent here)
# and open it in the browser.   Usage: minifasnet/dashboard/run.sh [port]
set -e
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PY="$ROOT/.venv/bin/python"
PORT="${1:-8765}"

if [ ! -x "$PY" ]; then
  echo "ERROR: $PY not found. Create it first:" >&2
  echo "  python3.11 -m venv .venv && .venv/bin/pip install -r minifasnet/requirements-dev.txt" >&2
  exit 1
fi
if ! "$PY" -c "import cv2, numpy, onnxruntime" 2>/dev/null; then
  echo "ERROR: missing packages. Run: .venv/bin/pip install -r minifasnet/requirements-dev.txt" >&2
  exit 1
fi
# If the port is taken, move to the next free one.
while lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; do
  echo "port $PORT is busy, trying $((PORT + 1))"
  PORT=$((PORT + 1))
done
URL="http://127.0.0.1:$PORT"
echo "Starting dashboard -> $URL   (Ctrl+C to stop)"
( sleep 2; open "$URL" 2>/dev/null || true ) &
exec "$PY" -u "$ROOT/minifasnet/dashboard/server.py" --port "$PORT"
