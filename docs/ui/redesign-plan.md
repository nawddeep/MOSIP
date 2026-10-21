# Redesign plan (Desktop-matched)

Spec: `docs/ui/design-system.md`. Presentation only: no changes to providers, platform code,
pigeon, native code, navigation, validation, sync/packet logic, auth or the liveness engine.
No text, widget `Key` or semantics-label changes (ui-test locators use `@text` / `@content-desc`).

## Current look and problems

| Area | Today | Problem vs Desktop |
|---|---|---|
| Palette | MOSIP blue `#1C43A1`, pale-blue canvas `#F4F7FC`, ~40 near-duplicate constants in `app_config.dart` | many blues/greys; no neutral paper; status colours inconsistent (3 reds, 2 greens) |
| Shape | radii 14–20, drop shadows on cards | Desktop is flat: 5/8 radii, 1px hairlines |
| Type | Plex Sans, ad-hoc sizes 13–28 inline (≈175 literal `fontSize`) | no shared scale; bold 700 everywhere vs Desktop 500–600 |
| Hard-coding | ≈450 literal colours, ≈110 literal radii in `lib/ui` (most in `process_ui`, `onboard`) | theme changes don't propagate |
| Home | blue cards, icon images | Desktop: hairline task cards with section labels, greeting |
| Form shell | blue header band, tab-like step chips | Desktop: step label + progress, white panels, single blue Continue |
| Liveness | dark navy-grey, amber/green/red oval | close already; retune to Desktop ink palette |
| Lists (approve/export) | dense rows, colour-only status text | need badges (icon + word), 56px rows, 48px checkboxes |

## Commits (each followed by `flutter analyze` + `flutter test test/face_liveness test/ui test/widgets`)

1. **Tokens.** `RegistrationTheme` gains the design-system tokens and a full `ThemeData`
   (buttons, inputs, cards, dialogs, chips, checkbox, radio, snackbar, nav bar, progress).
   `app_config.dart` constants re-pointed to the tokens (same names, so every screen follows).
   `AppTextStyle` re-pointed to the type scale. `LivenessTheme` re-pointed to ink tokens.
2. **Shared components.** `SectionLabel`, `Panel`, `StatusBadge`, `StepProgress`, `EmptyState`,
   `InlineAlert`, and reduced-motion-aware motion helpers.
3. **Login.** Paper canvas, white panel, label-above inputs, one 52px primary; success → home fade (moment 1).
4. **Home / tasks.** Section labels, hairline task cards with icon tiles, stagger (moment 2),
   sync indicator (moment 6). No invented stats.
5. **Registration form shell** (`process_ui`). Header, step progress, control widgets (textbox,
   dropdown, date, radio, checkbox, gender, document upload, consent box), Continue/Back bar.
   The spec interpreter is not touched.
6. **Biometric capture.** Panels, capture tiles, exception block, success check (moment 4).
7. **Face liveness.** Ink palette, focus-blue checking ring, button styles; layout and copy unchanged.
8. **Approve packet.** Row list, status badges, review dialog.
9. **Export packet.** Same list pattern, empty state.
10. **Settings.** Panel sections, list tiles.
11. **Profile.** Identity panel, detail rows, danger logout.
12. **Signup.** Matches login.
13. **Tests + screenshots.** Renders at 390×844, 1024×768, 2.0× text and Arabic RTL into
    `docs/ui/screenshots/`; changelog `docs/ui/CHANGELOG.md` (screens + any text/locator changes).

## Assumptions

- Keep IBM Plex Sans (brief) instead of Inter Tight; mono labels approximated with tracked Plex.
- Phone keeps its existing bottom navigation (Desktop side nav is not a touch pattern).
- Legacy constant names stay (renaming would touch hundreds of widget files for no visual gain);
  literal colours inside widgets are replaced screen by screen as each is restyled.
