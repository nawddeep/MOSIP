# Real-device test evidence (Desktop)

> **Status: template. Nothing in this file has been measured yet.** The only numbers that exist for the Desktop client
> come from an Apple-silicon Mac (see [README.md](README.md)). Every `TODO(team)` below is for the team to fill with
> real results on the demo PC. Do not estimate.

## A. Devices

| Device / PC | OS version | RAM | CPU | SBI device (or Mock SBI) | Tester | Date |
|---|---|---|---|---|---|---|
| TODO(team) | TODO(team) | TODO(team) | TODO(team) | TODO(team) | TODO(team) | TODO(team) |

## B. Timing

How to get the numbers: on the demo PC run `DesktopFaceDetectorTest` (see README) and copy the timing it prints.

| Device | Frames measured | Mean ms/frame | p95 ms/frame | Time to decision (s) |
|---|---|---|---|---|
| TODO(team) | TODO(team) | TODO(team) | TODO(team) | TODO(team) |

## C. Attacks and bona fide

Protocol: at least 10 tries per row; for bona fide, 3 or more different people; normal room lighting; write down
glasses and lighting in the notes. A "Passed" count above 0 on an attack row is a failure to report, not to hide.

| Device | Attack | Tries | Blocked | Passed (should be 0) | Stage that stopped it (PAD / challenge / timeout) | Notes |
|---|---|---|---|---|---|---|
| TODO(team) | Printed photo | TODO(team) | TODO(team) | TODO(team) | TODO(team) | |
| TODO(team) | Photo on a phone screen | TODO(team) | TODO(team) | TODO(team) | TODO(team) | |
| TODO(team) | Video replay on a phone or tablet | TODO(team) | TODO(team) | TODO(team) | TODO(team) | |
| TODO(team) | Paper mask (optional) | TODO(team) | TODO(team) | TODO(team) | TODO(team) | |

| Device | Real person | Tries | Passed | Wrongly rejected | Notes (glasses, lighting) |
|---|---|---|---|---|---|
| TODO(team) | TODO(team) | TODO(team) | TODO(team) | TODO(team) | |

## D. End-to-end checklist (Desktop)

- [ ] resident: liveness pass, then face capture (Mock SBI), then packet created
- [ ] capture refused without a pass (`REG-LIV-006` recorded)
- [ ] operator login gated; supervisor approval gated
- [ ] device unplugged mid-session: `DEVICE_UNAVAILABLE` / `CAMERA_ERROR` shown, retry works
- [ ] offline restart uses the last synced config
- [ ] audit entries `REG-LIV-001..006` visible in `AUDIT.APP_AUDIT_LOG`

## D2. Checks with no face (pipeline and error states only)

These are **not** attack or bona fide results and are not in the tables above. They show that the stream path and the
error states work. Machine: MacBook (Apple M4, 16 GB, macOS), JDK 11.0.32.1; raw output is kept outside the repository.
Path: Desktop engine through the harness `tools/evidence/DesktopLivenessHarness.java`, **not inside the running client**.

| Check | Source | Observed | Time |
|---|---|---|---|
| Engine tests on this Mac (`DesktopFaceDetectorTest`, `AuditRowsIntegrationTest`) | unit tests | 16 tests, 0 failures; engine mean 9 ms / p95 11 ms per frame with PAD at 640x480 | n/a |
| MockMDS stream check: SBI `STREAM` to engine | MockMDS `v1.2.0.2`, Face device, fixed image | 32 frames read; the engine ran; passive PAD rejected the fixed image twice, then `MAX_RETRIES`; no challenge was shown | decision after 1.98 s; engine mean 11.2 ms, worst-window p95 13.2 ms |
| Device not running | MockMDS stopped | `DEVICE_UNAVAILABLE`, 0 frames | 0.76 s |
| Stream ends (recorded file ends) | non-face test pattern | `CAMERA_ERROR` after the stream stalled | 9.9 s |
| Steady stream without a face | non-face test pattern, looped, 25 s | no decision (guidance only); 349 frames analysed, engine mean 3.2 ms | n/a |
| Parser | ffmpeg `-f mpjpeg` file and a generated MJPEG file | all frames read (60 of 60 and 373 frames) | n/a |

Not done: stopping MockMDS **while** a run is in progress. Two attempts finished before the stop landed, because the engine
decides on the fixed image within about 2 s. The "stream stops" case is covered only by the recorded-file result above.

The real client needs a MOSIP environment and a Windows 10 machine with TPM 2.0
([installation guide](https://docs.mosip.io/1.2.0/id-lifecycle-management/identity-issuance/registration-client/deploy/registration-client-installation-guide));
we have neither unless the outreach succeeds.

## E. Results summary

TODO(team): fill after tests.
