#!/usr/bin/env bash
set -euo pipefail

echo "================================================================================"
echo "   MOSIP Desktop Face Liveness - Derby Database Automated Verification"
echo "================================================================================"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f "tools/derby/derby.jar" ]]; then
    echo "[INFO] Downloading derby.jar..."
    curl -s -L -o "tools/derby/derby.jar" "https://repo1.maven.org/maven2/org/apache/derby/derby/10.14.2.0/derby-10.14.2.0.jar"
fi

if [[ ! -f "tools/derby/gson.jar" ]]; then
    echo "[INFO] Downloading gson.jar..."
    curl -s -L -o "tools/derby/gson.jar" "https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
fi

mkdir -p "tools/derby/classes"

echo "[BUILD] Compiling Java classes..."
javac -cp "tools/derby/derby.jar:tools/derby/gson.jar" -d "tools/derby/classes" @tools/derby/sources.txt

echo "[RUN] Running Derby Verification Harness..."
java -cp "tools/derby/classes:tools/derby/derby.jar:tools/derby/gson.jar" tools.derby.TestDerbyIntegration

echo "[SUCCESS] Derby Database tests completed successfully!"
