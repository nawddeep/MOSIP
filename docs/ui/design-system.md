# Registration Client design system

Source of truth: the Desktop web client in `website/` (`index.src.html`, `:root` tokens).
Reference captures: `docs/ui/desktop-reference/01…09-*.png` (1280×800, light theme; liveness in dark).

The Android app uses the Desktop **paper** (light) theme on all operator screens and the
**ink** (dark) theme on face liveness only, exactly as the Desktop does.

Code entry points:

| File | Holds |
|---|---|
| `lib/ui/theme/registration_theme.dart` | `RegistrationTheme` tokens + `ThemeData light()` + shared surface decorations |
| `lib/utils/app_config.dart` | legacy colour constants, re-pointed to the tokens below (names kept so existing widgets follow) |
| `lib/utils/app_style.dart` | legacy `AppTextStyle` styles, re-pointed to the type scale below |
| `lib/ui/face_liveness/liveness_theme.dart` | ink tokens for the liveness screen |

Widgets must read colours, sizes, radii and durations from these files, never literals.

## 1. Colour

### Paper (operator screens)

| Token | Hex | Use |
|---|---|---|
| `canvas` | `#F7F7F5` | scaffold background (paper-0) |
| `surface` | `#FFFFFF` | panels, cards, inputs, dialogs |
| `surfaceAlt` | `#FAFAF8` | table header, nav rail (paper-1) |
| `sunken` | `#EDEDE9` | selected chip, disabled fill, progress track (paper-2) |
| `sunkenStrong` | `#E0E0DB` | pressed / divider on sunken (paper-3) |
| `ink` | `#0A0A0B` | primary text (fg-on-paper) |
| `muted` | `#64646A` | secondary text, helper text, icons |
| `line` | `#0A0A0B` @ 11% (`0x1C0A0A0B`) | 1px borders, dividers |
| `lineStrong` | `#0A0A0B` @ 22% (`0x380A0A0B`) | input / outlined-button border, dialog border |
| `accent` | `#3B6FE8` | primary button fill, focus ring, selected indicator |
| `accentPress` | `#2F5FD0` | pressed primary, **all blue text and links on paper** |
| `onAccent` | `#FFFFFF` | text on accent |
| `navy` | `#0F1E4A` | dark stat tile, header band |
| `slate` | `#6F7885` | neutral stat tile (large text only) |
| `ok` | `#3B6948` | success text/icon on paper |
| `warn` | `#7A5A1E` | warning text/icon on paper |
| `err` | `#9A3F37` | error text/icon, mandatory `*`, destructive button |
| `focus` | `#2F5FD0` | 2px focus outline |
| `scrim` | `#141416` @ 42% | modal barrier |

Status tints (inline alerts, chips): background = status colour at 9% on surface; border = status colour at 50%.

### Ink (face liveness)

| Token | Hex | Use |
|---|---|---|
| `background` | `#0B0B0C` | screen (ink-0) |
| `ovalFill` | `#161618` | oval before camera frames (ink-2) |
| `raised` | `#1C1C1F` | buttons / sheets (ink-3) |
| `line` | `#F5F5F7` @ 11% | borders |
| `text` | `#F5F5F7` | primary text |
| `textSecondary` | `#8E8E93` | secondary text |
| `checking` | `#86A6F5` | oval while checking (Desktop focus-blue; plain blue fails contrast) |
| `challenge` | `#CDA866` | oval during a challenge (warn-ink) |
| `success` | `#86AE90` | oval on success (ok-ink) |
| `failure` | `#D48A82` | oval on failure (err-ink) |
| `accent` | `#3B6FE8` | primary button fill (white label) |

### Contrast (WCAG 2.1, measured)

| Pair | Ratio | Verdict |
|---|---|---|
| ink on canvas | 18.45 | AA |
| muted on canvas / on sunken | 5.48 / 5.01 | AA |
| white on accent (button) | 4.54 | AA |
| accent `#3B6FE8` as text on canvas | **4.23** | fails → links use `accentPress` (5.34) |
| ok / warn / err on white | 6.36 / 6.35 / 6.69 | AA |
| white on navy / on slate | 16.09 / **4.47** | slate tiles: numbers ≥ 24px only (large-text AA) |
| liveness text / secondary on ink | 18.07 / 6.03 | AA |
| ok / warn / err / focus-blue on ink | 7.94 / 8.80 / 7.27 / 8.23 | AA |
| accent `#3B6FE8` on ink | **4.33** | not used for text on ink |

Colour is never the only signal: every status also carries an icon and a word
(`✓ Synced`, `! Pending`, `× Failed`), and the liveness oval state is always paired with the instruction text.

## 2. Typography

Family: **IBM Plex Sans** (bundled, `IBMPlexSans`). Desktop uses Inter Tight; this is the one
deliberate difference: Plex is already bundled (offline, OFL-licensed) and the brief asks for it.
Sizes, weights and tracking follow the Desktop.

Mono labels: Desktop uses JetBrains Mono uppercase. Android has no bundled mono, so `label` is
Plex Sans 11.5/600 with tracking 1.2, uppercased (a no-op in hi/ta/kn/ar).

| Style | Size / line | Weight | Tracking | Desktop source |
|---|---|---|---|---|
| `display` | 28 / 1.15 | 600 | -0.6 | greeting "Amina, ready when you are." |
| `title` | 21 / 1.25 | 600 | -0.3 | h3 / screen title |
| `heading` | 17 / 1.3 | 600 | -0.1 | panel header |
| `body` | 16 / 1.5 | 400 | 0 | body (17 on Desktop; 16 on phone) |
| `bodyStrong` | 16 / 1.5 | 500 | 0 | field values |
| `small` | 14.5 / 1.45 | 400 | 0 | helper, secondary |
| `xs` | 12.5 / 1.4 | 500 | 0 | timestamps, meta |
| `label` | 11.5 / 1.3 | 600 | 1.2 | section labels (`CONSENT`, `REGISTRATION TASKS`) |
| `button` | 16 / 1.2 | 600 | 0 | all buttons |
| `stat` | 32 / 1.1 | 600 | -0.8 | stat tile numbers |

All sizes follow `MediaQuery.textScaleFactor`; nothing is clamped. Layouts must survive 2.0×.

## 3. Space, size, radius

- Spacing: 4-pt grid — `s1 4`, `s2 8`, `s3 12`, `s4 16`, `s5 20`, `s6 24`, `s8 32`, `s10 40`.
- Screen gutter: 16 phone, 24 tablet. Content max width 560 (forms, dialogs), 1100 (home grid).
- Radius: `r 5` (buttons, inputs, toasts), `rLg 8` (panels, cards, dialogs, tiles),
  `rBadge 3`, `rPill 999` (filter chips). The previous 14–20px radii are retired.
- Elevation: none. Separation is a 1px `line` border. Only dialogs and bottom sheets get one
  soft shadow (`0 12 32 #0A0A0B @ 8%`).

## 4. Touch adaptation (Android ≠ Desktop)

| Desktop | Android |
|---|---|
| `.btn` 44px | buttons **48** min; primary **52** (`.btn-lg`); full-width primary at phone width |
| `.btn-sm` 34px | not used; smallest target 48×48 |
| Chip 36px | chip visual 36, hit area padded to 48 |
| Side nav, 38px items | phone: existing bottom navigation, 64 tall, icon + label; tablet: existing top/side nav restyled |
| Hover states | pressed states (`accentPress`, `sunken`) + ink ripple |
| Toast top-right | floating SnackBar, bottom, inverted (ink bg, white text), radius 5 |

## 5. Components

- **Primary button**: accent fill, white label, 52 tall, `r 5`, optional leading icon.
  One per screen. Disabled: `sunken` fill, `muted` label (still shows its text).
- **Secondary (line) button**: transparent, 1px `lineStrong`, `ink` label, 48 tall.
- **Ghost button**: no border, `accentPress` label, 48 tall (links, "Forgot password").
- **Danger**: `err` fill, white label (logout confirm, discard).
- **Input**: `surface` fill, 1px `lineStrong`, `r 5`, 52 tall, label above the field (`small`, 500,
  `ink`), mandatory `*` in `err`. Focus: 2px `accent`. Error: 1px `err` border + message below with
  an error icon, in `small`/`err`.
- **Panel / card**: `surface`, 1px `line`, `rLg 8`, padding 16 (phone) / 20 (tablet). Optional
  header row: `label`-style title + trailing action.
- **Task card** (home): panel with a leading 40px icon tile (`sunken`, `r 5`, `ink` icon), `heading`
  title, trailing chevron. The whole card is one tap target, 72+ tall.
- **Stat tile**: solid block `rLg 8` in navy / slate / white (bordered) / accent, `label` on top,
  `stat` number. Rendered only for numbers the app actually has.
- **Status badge**: 22 tall, `rBadge 3`, tinted 9% bg + 50% border, icon + word in `xs`/600.
- **Step progress**: segmented bar (one segment per screen of the UI spec): current `accent`,
  done `ink`, todo `sunken`, with the existing step title text above it.
- **Inline alert**: tinted panel, leading status icon, `small` text, optional ghost action.
- **Dialog**: `surface`, 1px `lineStrong`, `rLg 8`, max width 560, title in `title`, actions
  bottom-end (phone: stacked full-width).
- **List rows** (approve/export): 56 min tall, `line` dividers, `surfaceAlt` header with `label`
  text, checkbox 48 hit area, status badge column.
- **Empty state**: centred `muted` icon 32, `heading` sentence of what is missing, `small`
  sentence of what to do, optional single action.
- **Icons**: the Material set already in the app, 20 (inline) / 24 (nav), always with a text label.

## 6. Motion

Easing `Cubic(0.22, 1, 0.36, 1)` (Desktop `--ease`). Durations: `fast 160ms`, `base 280ms`, `slow 480ms`.
Every animation is skipped when `MediaQuery.disableAnimations` is true. Allowed moments only:

1. Login success → Home: fade + 8px rise, `base`.
2. Home cards: staggered fade/rise, 40ms stagger, total < 600ms.
3. Liveness oval: existing ring animations (each state change < 600ms).
4. Biometric capture success: check mark, `slow`.
5. Registration complete: check + soft scale, `slow`.
6. Sync indicator: rotating sync icon while syncing.

Forbidden: `BackdropFilter` over the camera, custom shaders, Lottie/Rive, new image assets, new packages.

## 7. Localization and RTL

- All copy comes from `AppLocalizations` (en, hi, fr, ar, ta, kn). No new strings unless added to all six ARB files.
- Use `EdgeInsetsDirectional`, `AlignmentDirectional`, `start/end`; chevrons and back arrows flip in `ar`.
- Never truncate operator-entered data; wrap instead.

## 8. Privacy (face liveness)

Never render scores, percentages, thresholds, model names or attack types; only the localized
instruction and outcome copy from `liveness_copy.dart`. `PrivacyContractTest` enforces this.
