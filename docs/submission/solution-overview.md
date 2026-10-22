# Solution overview (draft for judges)

Face Liveness and Presentation Attack Detection for the MOSIP registration clients (MOSIP Decode 2026, Problem Statement 04).
Repositories: Android client https://github.com/nawddeep/MOSIP (branch `main`); Desktop client
https://github.com/nawddeep/registration-client (branch `release-1.2.0.x`).

## Problem

TODO(team): paste the Problem Statement 04 wording. In our own words: a registration client that captures a face must
know that the face belongs to a live person in front of the camera, and not to a printed photo, a screen or a replayed video;
this check must also protect operator login and supervisor approval, work offline, and fit MOSIP's existing audit and
configuration mechanisms.

## Solution

A passive presentation-attack check runs first on the camera frames; when its confidence is low, a random active challenge
(blink, smile, turn head) must be performed live; only a decision that passes issues a single-use, short-lived pass, which
the resident capture, the operator login and the supervisor approval each have to consume. The same engine (landmarks,
passive PAD, quality check, challenge logic, state machine, config parser) is used on Android and Desktop; each client only
adds its own frame source, UI, audit and config adapters.

## Architecture

* Android: [docs/liveness/01-architecture.md](../liveness/01-architecture.md) (module layout, state machine and sequence diagrams, bridge contract).
* Desktop: shared core module, services adapters and JavaFX popup, described in the Desktop repository's `docs/liveness/README.md`.
* Both are plugged into the existing MOSIP mechanisms: the audit service, the global-parameter config, the workflow controllers. No new tables.

## What we built versus upstream MOSIP

| | Android (`nawddeep/MOSIP`) | Desktop (`nawddeep/registration-client`) |
|---|---|---|
| Fork point | upstream commit `8f4643a` (`bf1b3b91` in this repository after the history rewrite) | tag `v1.2.0.2` |
| Existing upstream files modified | 99 (many are the Flutter UI restyle, not liveness) | 15, all small, each listed with its reason in that README |
| New files | 271 (code, tests, docs, screenshots) | new module `registration-liveness-core`, `io.mosip.registration.liveness.*`, JavaFX popup, tools |

Counts come from `git diff` against the fork point (Android) and from the Desktop README's modified-file table.

## Privacy and security

* No score, percentage, threshold, model name or attack type is shown in any UI; the failure wording is generic. Scores appear only in audit descriptions.
* No frame, crop, thumbnail or embedding is stored on disk, in the database, in logs or in audit.
* Fail closed: any engine, model, configuration or audit-setup error ends in an error state, never a pass.
* Models and configuration are checked by SHA-256 (pinned model manifest; signed configuration with an integrity block).
* No network call in any liveness decision path.

## Verified and not verified

Taken from the documents; read those for detail.

* Android: results come from an Android 14 arm64 emulator on a Mac, JVM simulations and offline model checks; no physical phone was available (see [07-performance-accuracy.md](../liveness/07-performance-accuracy.md)). End-to-end SBI capture after a pass and the operator/supervisor flows are not exercised on device (see [08-technical-notes.md](../liveness/08-technical-notes.md), known limits).
* Desktop: builds on JDK 11, tests pass, audit rows verified into `AUDIT.APP_AUDIT_LOG` with the client's own DDL, config verified against the real `REG.GLOBAL_PARAM` column, all 16 UI states rendered. **Not** run in the real client with an SBI device or Mock SBI, gates not exercised in the running UI, only a still-photo attack tried, timings from a Mac only.
* Real-device results: see [12-test-evidence.md](../liveness/12-test-evidence.md), currently an empty template.

## Limits and future work

* The MiniFASNet weights have no separate licence upstream (see [DEPENDENCIES.md](../liveness/DEPENDENCIES.md)).
* French and Arabic wording of the new Desktop alert is a draft and needs native review.
* Demo configuration: Android bundled default and the Desktop signed demo value both enable resident, operator and supervisor.
* TODO(team): add the future-work list the team agrees on (the Android list is in [08-technical-notes.md](../liveness/08-technical-notes.md)).
