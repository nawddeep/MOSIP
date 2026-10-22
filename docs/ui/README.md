# UI

The operator screens follow the web demo (`website/`): paper palette, IBM Plex Sans with the
web demo type scale, flat hairline panels, 5/8 px radii, one accent-blue primary action. Face
verification uses the web demo ink (dark) palette.

- Design system: [design-system.md](design-system.md)
- Plan and per-screen notes: [redesign-plan.md](redesign-plan.md)
- Changelog, including text/locator changes for `ui-test/`: [CHANGELOG.md](CHANGELOG.md)
- Web demo reference captures: [web-demo-reference/](web-demo-reference/)
- Before/after renders: [screenshots/before/](screenshots/before/), [screenshots/after/](screenshots/after/)
- Golden previews (compared by tests): [previews/](previews/)
- Behaviour rules: [design-contract.md](design-contract.md)

## Tests

```sh
flutter analyze
flutter test test/face_liveness test/ui test/widgets
```

`test/ui/modern_visual_review_test.dart` compares golden previews (phone 390x844, tablet 1024x768,
200% text). `test/ui/rtl_layout_test.dart` checks Arabic RTL layout on the same sizes. To refresh the
Arabic screenshots in `screenshots/after/` (macOS; uses the system Arabic font at render time only):

```sh
UI_SCREENSHOTS=1 flutter test test/ui/rtl_layout_test.dart --update-goldens
```

`test/ui/components_test.dart` covers reduced motion, the 600 ms motion budget, badges and touch
sizes; `test/ui/ui_arb_parity_test.dart` keeps redesign strings translated in all six locales.
