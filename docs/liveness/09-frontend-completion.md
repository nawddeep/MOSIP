# Frontend completion

The existing face liveness design is retained. This pass changes Flutter presentation and interaction only; model, native engine, database and security policies are unchanged.

- Exhausted attempts open recovery guidance for the selected workflow. Returning exits with a failed verification result; it does not bypass the gate or reset attempts.
- Camera permission denial prevents engine start and offers retry, cancel, and device settings when access is permanently denied.
- Camera startup, unreadable images, device disconnection, and engine failure have distinct guidance and appropriate retry labels.
- Challenge screens show the current action number, seconds remaining, and acknowledgement when the next action starts. Brief quality guidance does not increment the action number. New attempts reset progress.
- Operator and supervisor success means face verification passed; the user continues to the existing authentication flow.
- The layout fills available height and scrolls when landscape or enlarged text needs more space. Attempt indicators wrap, and offline status stays inside the header column.
- Screen reader challenge names use the same readable instructions as the visible prompt. Existing reduced-motion handling is preserved.
- Startup/retry exceptions and event stream errors display an engine failure instead of leaving the interface busy.

## Engine contract

The current event stream does not include the total challenge count. The frontend displays `Challenge 1`, `Challenge 2`, etc. from distinct observed challenges; it does not assume a policy count. To display `Challenge 1 of 2`, the engine must supply authoritative challenge index and total fields. No database integration is required for these frontend changes.

## Verification

31 widget/unit tests pass, including recovery guidance, permission denial, challenge progress across quality interruptions, workflow-specific success, and a 568 x 320 display at 200% text scale. A temporary render using bundled IBM Plex Sans fonts was visually inspected. No Android device was connected for an actual camera smoke test.
