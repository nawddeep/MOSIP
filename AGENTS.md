# AGENTS.md — Project rules (auto-loaded every session)

Project: MiniFASNet passive-liveness module (ONNX Runtime; Python reference + Java wrapper) for the MOSIP Registration Client.
Full context lives in spec.md, architecture.md, report.md. Do NOT rely on chat history.

## Session start protocol (run this at the start of every session / after any long gap)

1. Read spec.md.
2. Read architecture.md.
3. Read only the CURRENT STATE section of report.md.
4. Check git status and recent relevant commits.
5. Do not broadly scan the repository or reread conversation history unless the task requires it.

Then:
* Briefly state the current verified state.
* Identify the next checkpoint.
* Before implementation, investigate only the files relevant to that checkpoint.
* Follow the project rules in this file.
* Treat spec.md, architecture.md, and report.md as living sources of truth.
* Verify every meaningful change before marking it complete.
* Commit only verified checkpoints.
* Keep documentation synchronized with implementation.

Do not implement anything beyond the current checkpoint unless required for correctness or explicitly requested.

## Project rules

1. SCOPE: ONE model only — MiniFASNet (Silent-Face-Anti-Spoofing, minivision-ai), run via ONNX Runtime.
   Do not study, add, or compare other liveness models. No detector/device/UI/active-challenge/Android/config/audit code
   unless the user explicitly widens the scope.
2. EVIDENCE: Every claim about the upstream repo must come from its actual code/files (clone in `minifasnet-study/`, read-only,
   gitignored). Anything not verified is marked `UNVERIFIED`.
3. QUALITY GATE (must stay green before any commit), run from the repo root:
   - `.venv/bin/python -m pytest` (warnings are errors; 43 tests), `.venv/bin/ruff check minifasnet`, `.venv/bin/pyright` (0 errors)
   - `cd minifasnet/java && mvn -q package` (compiles with `-Xlint:all` + failOnWarning, runs JUnit)
   - after any preprocessing/model change: `.venv/bin/python minifasnet/tests/test_parity.py` and update the parity numbers in the docs.
4. PREPROCESSING IS A SPEC: crop + canonical resize + BGR/0..255/NCHW must stay identical in Python and Java
   (see minifasnet/README.md "Preprocessing spec"). Never swap in cv2.resize or a platform resizer.
5. LICENSE: OSI-approved licenses only (code, weights, training data). Unclear = flag as blocker, do not assume OK.
   New dependencies: state why + license, pin the version in minifasnet/requirements*.txt or pom.xml.
6. EXPLANATIONS: simple language, define jargon on first use, small ASCII diagrams where they help.
7. CHECKPOINTS: small checkpoints; each ends with verified evidence recorded in report.md, docs synced, one git commit.
8. DOC SYNC: when something changes, update the relevant doc in the same checkpoint. report.md CURRENT STATE
   must stay short (<= 25 lines) and always true.
9. PERMISSIONS: user granted standing permission (2026-10-02) to proceed without asking: installs into `.venv`/Homebrew,
   git commits, moving to the next step. Still never: push to a remote, delete user data, commit face images/videos/raw frames.
10. Temporary files go in the session scratchpad, not the project dir. Face data never enters git (.gitignore).

## Environment facts (this machine)
- Python: always `.venv/bin/python` (python3.11). System `python3` (3.13 framework) silently does nothing in this shell.
- Java: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` (macOS `java` stub has no runtime).
- opencv-python must stay < 5 (5.x removed readNetFromCaffe + CascadeClassifier used by upstream and the dev helper).
