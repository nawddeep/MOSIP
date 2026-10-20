#!/bin/bash
# Double-click in Finder: opens a Terminal window, starts the MiniFASNet demo dashboard and opens it in the browser.
cd "$(dirname "$0")" || exit 1
exec minifasnet/dashboard/run.sh
