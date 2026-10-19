# UI refresh

The account and home screens retain MOSIP blue and white. Face verification retains its dark theme and amber/green/red status colors. Shared IBM Plex Sans typography, 14–20 px control/surface radii, quieter borders, and constrained responsive layouts connect the screens.

- Login: responsive brand/form composition, language chips, labeled fields, keyboard submission, password visibility, and disabled busy actions.
- Signup: refreshed access-request form and clearly separate local testing section. Validation and email/clipboard behavior are preserved.
- Home: labeled Material navigation, segmented task selection, consistent actionable cards, two-column tablet workflows, and readable icons. At 200% text, narrow-screen task tabs stack. Empty workflows retain synchronization.
- Verification: framed camera region, clearer header hierarchy, rounded controls, larger prompts, action progress, permission and error recovery. The native camera texture replaces the empty fixture preview on a real device.

## Verification

52 tests pass using Flutter 3.10.4. Phone (390x844), tablet (1024x768), and 200% home text previews are retained under `previews/`. Visual tests use sample workflow data; they do not establish backend or model correctness. Static analysis of changed frontend files reports no errors or warnings; informational style notices remain.

The design contract and review-evidence JSON record the scope and checks. No physical Android device was connected; on-device camera and TalkBack verification remain to be performed.

To run the selected suite:

```sh
flutter test test/face_liveness test/widgets/signup_page_test.dart test/widgets/password_visibility_test.dart test/ui
```

APK packaging also supports Flutter's per-ABI builds without conflicting with release ABI filters. The normal universal build retains its existing architecture filters.
