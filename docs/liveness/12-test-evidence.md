# Real-device test evidence

> **Status: template. Nothing in this file has been measured yet.** The emulator numbers in
> [07-performance-accuracy.md](07-performance-accuracy.md) stay as they are and are labelled as emulator results.
> Every `TODO(team)` below is for the team to fill with real results. Do not estimate.

## A. Devices

| Device / PC | OS version | RAM | CPU | Camera | Tester | Date |
|---|---|---|---|---|---|---|
| TODO(team) | TODO(team) | TODO(team) | TODO(team) | TODO(team) | TODO(team) | TODO(team) |

## B. Timing

How to get the numbers: connect the device and run `adb logcat -s FaceLivenessAudit`; use the `PERFORMANCE` lines.

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

## D. End-to-end checklist (Android)

- [ ] resident: liveness pass, then face capture (Mock MDS), then packet created
- [ ] capture refused without a pass (`REG-LIV-006` recorded)
- [ ] operator login gated; supervisor approval gated
- [ ] device camera unavailable mid-session: `DEVICE_UNAVAILABLE` / `CAMERA_ERROR` shown, retry works
- [ ] offline restart uses the last synced config
- [ ] audit entries `REG-LIV-001..006` visible

## E. Results summary

TODO(team): fill after tests.
