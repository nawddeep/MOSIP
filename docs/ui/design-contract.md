# Modern registration UI

Purpose: support repeated registration work, operator access, and face verification.
Audience: registration operators, residents following camera instructions, supervisors.
Direction: calm, readable operations UI matched to the Desktop client (paper palette, ink face verification); see design-system.md.
Detail: sunken icon tiles, 1px hairline panels, icon + word status badges, 5/8 px radii.

Targets: 390x844 phone and 1024x768 tablet; scrolling at enlarged text and keyboard insets.
Login primary action: validate username, then existing password authentication. Preserve language, forgot-password, signup and debug access.
Signup primary action: request account; keep validation and local testing mode explicitly separate.
Home primary action: select a registration workflow; operations and synchronization remain accessible.
Face primary action: follow automatic instructions, then continue after verification only.

Loading: show existing progress and disable repeated submission.
Empty/partial: use actual available workflows, never fabricate counts or account state.
Errors/permission: retain actionable recovery and camera access flow.
Success: show verification outcome and next step without bypassing authentication.
Review: render phone/tablet surfaces with bundled fonts; exercise existing signup/password and liveness tests.
