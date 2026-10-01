# CLAUDE.md — Project rules (auto-loaded every session)

Project: MiniFASNet passive-liveness study + deployment prep for MOSIP Registration Client.
Full context lives in spec.md, architecture.md, report.md. Do NOT rely on chat history.

## Session start protocol (run this at the start of every session / after any long gap)

1. Read spec.md.
2. Read architecture.md.
3. Read only the CURRENT STATE section of report.md.
4. Check git status and recent relevant commits (if the dir is a git repo — see report.md).
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
   Do not study, add, or compare other liveness models. Do not touch/modify any other model.
2. EVIDENCE: Every claim about the repo must come from the actual code/files after cloning.
   Show excerpts with file paths. Anything not verified is marked `UNVERIFIED`.
3. NO MEMORY CLAIMS: Facts from model memory (before the repo is read) live in spec.md as
   "hypotheses to verify" — never present them as findings.
4. PHASE GATE: Current phase is stated in report.md CURRENT STATE. "Theory only" phases = no code,
   no cloning, no installs, until the user says to start the build.
5. LICENSE: OSI-approved licenses only (code, weights, training data). Unclear = flag as blocker, do not assume OK.
6. EXPLANATIONS: simple language, define jargon on first use, small ASCII diagrams where they help.
7. CHECKPOINTS: work in small checkpoints (see architecture.md). Each ends with: verified evidence recorded in
   report.md, docs synced, then (if git repo) one commit. Do not skip ahead.
8. DOC SYNC: when something changes, update the relevant doc in the same checkpoint. report.md CURRENT STATE
   must stay short (<= 25 lines) and always true.
9. DESTRUCTIVE / OUTWARD actions (git init, push, installs into system python, deleting files): ask first.
10. Temporary files go in the session scratchpad, not the project dir.
