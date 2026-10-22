# Demo video script (target 3:35)

Rules for the recording: never show a score, percentage, threshold, model name or attack type on screen (the product
does not show them; do not open logcat or a debugger on camera). Showing the audit rows is fine. Record only what
actually runs. If something was not run on the day, cut that shot; do not mock it.

| # | Time | Shot | On screen | Say (one sentence) | Must be visible |
|---|---|---|---|---|---|
| 1 | 0:00 to 0:15 (15 s) | Intro | Title card, then the Android client home | "We added face liveness and presentation attack detection to the MOSIP registration clients, on Android and Desktop, with one shared engine." | Project name, both client names |
| 2 | 0:15 to 0:45 (30 s) | Android resident pass | Resident face capture, liveness screen: oval over live camera, ring, then success, then the normal capture | "A real person passes the passive check and moves on to the normal face capture." | Oval over the live preview, instruction text, green success state |
| 3 | 0:45 to 1:15 (30 s) | Android challenge path | A run where a random challenge appears (blink, smile or turn) and is completed | "When confidence is low, a random challenge is asked and must be performed live." | The challenge instruction and its progress |
| 4 | 1:15 to 1:45 (30 s) | Android blocked attack | A printed photo or a photo on a phone held to the camera; the screen ends in a failure or recovery state | "A photo of a face does not pass, and capture stays blocked." | The failure/recovery screen with generic wording (no scores) |
| 5 | 1:45 to 2:15 (30 s) | Android operator and supervisor gates | Operator login, then supervisor approval, each asking for liveness after the existing authentication | "Operator login and supervisor approval are gated the same way." | Both gates appear after the existing password step |
| 6 | 2:15 to 2:45 (30 s) | Desktop resident pass on the SBI stream | Desktop client face capture with Mock SBI: liveness popup over the live SBI preview, then success, then capture | "On Desktop the same engine reads the SBI preview stream, with no second stream opened." | Popup over the SBI preview, then the capture starting |
| 7 | 2:45 to 3:05 (20 s) | Desktop blocked attack | A photo or phone screen in front of the SBI camera; the popup ends without a pass; capture refused | "No pass means no capture: the Java gate refuses it." | Refusal alert, capture did not start |
| 8 | 3:05 to 3:20 (15 s) | Desktop audit rows | A query or viewer on `AUDIT.APP_AUDIT_LOG` showing `REG-LIV-001` to `REG-LIV-006` rows | "Every session and every blocked capture is written through MOSIP's own audit service." | Event ids, no images, no face data |
| 9 | 3:20 to 3:35 (15 s) | Closing | Title card with both repository names | "It runs offline, fails closed, stores no images, and is documented with what we verified and what we did not." | Repository links |

Before recording: fill [../liveness/12-test-evidence.md](../liveness/12-test-evidence.md) so the claims in the narration match the results.
Shots 6 to 8 need the Desktop client running against a MOSIP environment with Mock SBI; that has not been done yet
(see the Desktop `docs/liveness/README.md`). If it is not ready, say so in the video and remove those shots.
