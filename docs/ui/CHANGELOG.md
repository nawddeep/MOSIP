# UI changelog: Desktop-matched redesign

Spec: [design-system.md](design-system.md) · Plan: [redesign-plan.md](redesign-plan.md) ·
Desktop reference: [desktop-reference/](desktop-reference/) · Before/after: [screenshots/](screenshots/)

Presentation only. Not touched: `lib/provider`, `lib/platform_android`, `lib/platform_spi`, `pigeon/`,
`android/` native code, navigation, form validation, UI-spec interpretation, sync/packet logic,
authentication, liveness engine. No new packages.

## Screens

| # | Commit | Screen | What changed |
|---|---|---|---|
| 1 | `6e3fdbd` | All (tokens) | Desktop paper palette, IBM Plex type scale, 4-pt spacing, 5/8 radii, touch sizes, motion tokens; full Material 3 theme applied app-wide in `main.dart` (was a bare blue `ThemeData`). Legacy `app_config` colour names re-pointed to tokens. `LivenessTheme` → Desktop ink palette. |
| 2 | `e33b54c` | Shared | `components.dart`: SectionLabel, Panel, StatusBadge, InlineAlert, StepProgress, EmptyState, IconTile, FadeRise, SyncSpinner, SuccessCheck. |
| 3 | `532a9b6` | Login | Flat hairline panel, field/label tokens, pill language chips with check mark. Sync-result and sync-progress dialogs no longer fixed boxes with 8–10px text; they grow with text size and show a determinate bar under the existing "Sync n of 7". |
| 4 | `05e98f2` | Home / tasks | Hairline task cards with sunken icon tiles, segmented task tabs, themed bottom nav with top hairline, EmptyState for no processes. Motion 1, 2, 6. |
| 5 | `678078c` | Registration form | White header + hairline, ink title, StepProgress bar; step tabs accent/ok/muted with icons; Continue with trailing arrow and no fixed max size (was 209×52, clipped at 200%). Controls' literal sizes/radii/colours → tokens. |
| 6 | `9e3772b` | Biometric capture | Tokens across capture/scan/exception blocks; quality track `sunken`; SuccessCheck on scanned tiles (motion 4). |
| 7 | `32031a5` | Face liveness | Accent primary, transparent outlined secondary, ink-1 camera panel with 8px hairline frame, 5px buttons. Copy and states unchanged. |
| 8 | `0aca068` | Approve packet | Review status as StatusBadge (icon + same word); flat card; empty state. |
| 9 | `bb927ff` | Export packet | Tokens, accent-tint selected rows, empty state. |
| 10 | `43b4d1e` | Settings | White page header (was blue band), ink/muted tabs with accent indicator, flat hairline cards. |
| 11 | `8157475` | Profile | Tokens; logout uses `err`. |
| 12 | `dffdd5e` | Signup | Fields use the theme's 3:1 outline; demo notice on warn tokens. |
| 13 | `139bac6` | Rest | Dashboard, tablet header/footer/nav, onboarding, preview/auth/acknowledgement (SuccessCheck, motion 5; 48px print button with icon), scanner, shared dialogs, machine keys. |
| 14 | `3faf0e4` | Tests | Arabic RTL layout test (phone, tablet, 200%), after screenshots. |

After the pass, `lib/ui` has no literal hex colours or font sizes outside the two theme files;
remaining literal radii are circles (avatars) and 2px bars.

## Text and locator changes (for `ui-test/`)

Existing user-visible strings, widget `Key`s and semantics labels are **unchanged**. Things a
locator might still notice:

| Where | Change | Locator impact |
|---|---|---|
| Approve / Export packet | **New strings** when the list is empty: `ui_packets_empty_title` "No packets to show", `ui_packets_empty_message` (all 6 locales). The table is not rendered when empty. | Tests that expect an empty table header on an empty list should look for the new text instead. |
| Approve packet status cell | "Pending/Approved/Rejected" text now sits inside a StatusBadge with an icon. | Same `@text`; element is nested one level deeper. |
| Registration form Continue | Button child is now a Row: the same Text + an arrow icon. | Same `@text`. |
| Registration form header | New StepProgress semantics node with value `n/total` (no label). | New element; nothing renamed. |
| Acknowledgement Print | Same Text + print icon; min height 48. | Same `@text`. |
| Sync progress dialog | Same "Sync n of 7" text; progress bar excluded from semantics. | None. |
| Synchronize Data card icon | SVG replaced by Material sync icon (inside `ExcludeSemantics`). | None. |
| Scanned biometric tile | `Green_Check.png` replaced by drawn check (no content-desc before or after). | None. |
| Section labels | Not upper-cased in code (Desktop does it in CSS) to keep `@text` stable. | None. |

## Known gaps

- The home app-bar title ("MOSIP Registration Client!") still truncates with an ellipsis at 200% text,
  as before; enlarging the toolbar would change every screen's layout.
- `OperationalTasks.getMemoryProvider()` contains hard-coded storage numbers (286 GB, 72%). It is not
  called anywhere, so it is not shown; left untouched as out of scope.
- Golden previews and RTL checks run in widget tests with sample data. No physical device, TalkBack
  or Cortex-A53 frame-time check was run in this pass.
