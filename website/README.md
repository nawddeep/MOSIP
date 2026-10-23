# MOSIP Registration Client: website and in-browser demo

A website and in-browser demo client for the MOSIP Registration Client. It mirrors the Android client's flows, fields, validation and copy: login, sign-up, registration, face liveness, Manage Applications, approval and settings. Discovery notes: [docs/discovery.md](docs/discovery.md).

Built by Charvi Paliwal, team member. Integrated from [CharviPaliwal/Mosip_desktop](https://github.com/CharviPaliwal/Mosip_desktop) at commit `c8e23a2` (2026-10-20). See [Changes made during integration](#changes-made-during-integration).

## What's here

| Path | What it is |
|---|---|
| `index.html` | The built page: landing site plus the demo client. Open it directly in a browser. |
| `index.src.html` | Source template. `__SPEC_JSON__` is replaced with `spec.json` at build time. |
| `intro-1080p.mp4` | Intro video shown full screen before the page on every load; only the first 5 s play. A 1080p copy of `full16x9_4k_10s.mp4` (84 MB, not loaded by the page) made with macOS `avconvert`. |
| `spec.json` | Compact UI spec used by the demo client (extracted from the upstream build; see discovery section b3). |
| `build.py` | Rebuilds `index.html`; `--check` verifies it is up to date and follows project rules. |
| `vendor/` | Self-hosted Lenis smooth scrolling (MIT) and fonts Inter Tight / JetBrains Mono (OFL), with licence files. |
| `test/mvel.test.mjs` | Tests the safe UI-spec rule evaluator, including injection attempts. |
| `docs/discovery.md` | Discovery of the Android client: flows, fields, API contract, rules, open questions (written against commit `9be3e30`). |

## Run

Open `website/index.html` in Chrome, Edge, Firefox or Safari. No build step, server or internet connection is needed: every script and font is local.

Demo accounts: `officer01`, `supervisor01`, `operator02` (not onboarded). The password for all of them is `mosip@123`. These exist only inside the page; they are not MOSIP credentials. The first login runs a simulated "first login on this machine" sync, then press **Continue**.

## Rebuild and check after editing

```bash
python3 website/build.py          # index.src.html + spec.json -> index.html
python3 website/build.py --check  # fails if index.html is stale, loads anything from a CDN, or uses eval/new Function
node website/test/mvel.test.mjs   # UI-spec rule evaluator: truth table + injection attempts
```

## Demo mode

The page is not connected to a MOSIP server. Login against a real server needs a registered machine's signing key, and packets must be encrypted with server keys. A browser can't do either safely (discovery Q2 and Q3). All data lives in memory for the tab; only the theme choice and a dismissed-banner flag are kept in `localStorage`.

**Face liveness in the browser is simulated.** The camera shows a preview, but no face analysis runs; steps and outcomes follow timers and the "Demo controls". The screen says so. The real liveness and PAD engine runs on the Android app (MediaPipe + MiniFASNet ensemble).

## Changes made during integration

| Change | Why |
|---|---|
| Google Fonts and CDN scripts (GSAP, ScrollTrigger, Lenis) replaced with local files | Offline-first project rule; the page now makes no network requests |
| GSAP animations rewritten with CSS keyframes + `IntersectionObserver` (same timings and easing, disabled under reduced motion) | GSAP's licence is not OSI-approved, and it was used for only one block of cosmetic motion |
| UI-spec rule evaluator: `new Function(...)` replaced by a small parser for the MVEL subset used by the spec | The old guard could not stop expressions like `fetch(...)`; code injection once the spec comes from a server. Equivalence verified: 16 expressions x 240 states, 0 mismatches against the original |
| Liveness intro: added "Demo only: this browser preview does not analyse your face…" | The check is simulated; the page must not suggest a real liveness result |
| `build.py` reproduces `index.html` byte-for-byte; `spec.json` committed | The upstream rebuild step needed a `spec.json` that was not in the repo |

Verified in Chrome (file://): 5 local requests, 0 external, 0 console errors; demo login, first-login sync, Home navigation, liveness intro; XSS probe on login and registration fields (payload rendered escaped, nothing executed). Not exercised with a payload: preview and acknowledgement screens (the probe stops at field validation).

## Licences

| Item | Licence |
|---|---|
| Page code (`index.src.html`, `docs/`) | **No LICENSE file in the source repo.** Ask the author to add one before public release. |
| Lenis 1.1.13 (`vendor/lenis/`) | MIT |
| Inter Tight, JetBrains Mono (`vendor/fonts/`) | SIL Open Font License 1.1 |

## Not built yet (from the upstream plan)

- Electron desktop app (`desktop/`): custom title bar, OS keychain for the machine key and token, real login and sync, SBI discovery on localhost.
- Shared packages (`shared/`): tokens, components and a typed API client as a TypeScript/Vite build.
