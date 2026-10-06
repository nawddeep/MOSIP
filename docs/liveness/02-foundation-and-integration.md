# Phases 2 and 5: Foundation (frames, devices, bridge, config, audit) and Workflow Integration

## Phase 2: what was built

| Item | Where |
|---|---|
| CameraX `FaceFrameSource` (Preview into a Flutter texture, plus ImageAnalysis RGBA_8888 with keep-only-latest) | `livenessmanager/.../android/CameraXFrameSource.java` |
| Mock source: a recorded video (`MediaMetadataRetriever.getFrameAtIndex`) **or** a folder of JPEG/PNG frames, looping at a fixed fps | `android/MockVideoFrameSource.java` |
| Device adapter layer (`FaceCaptureDevice`: connect / disconnect / status / L0/L1) | `CameraDeviceAdapter` (L0 built-in camera, permission and presence checks), `MockDeviceAdapter`, `VendorDeviceAdapter` (L1 skeleton + `VendorFaceSdk` contract) |
| Flutter bridge (MethodChannel + EventChannel + `Texture`) | `app/.../api_services/FaceLivenessApi.java`, `lib/platform_android/face_liveness_service_impl.dart` |
| Config loader (JSON, validated, last-known-good cache, bundled default) | `config/CachedLivenessConfigProvider`, `android/FileConfigCache` |
| Audit logger (events only, never frames) | `app/.../liveness/AuditManagerLivenessLogger.java` → standard `AuditManagerService`, events `REG-LIV-001..006` |

### Switching camera and mock (debug builds only)

`FaceLivenessService().setSource(mock: true, path: 'frames')` or the **Liveness debug** menu:
- In the full app: double-tap the MOSIP logo in the login page's bottom bar (debug builds).
- Standalone: `flutter run -t lib/main_face_liveness_preview.dart`.

Release builds ignore `setSource` (`BuildConfig.DEBUG` check), so mock input can never reach production.

### Verified on an Android 14 arm64 emulator (2026-10-05)

| Check | Result |
|---|---|
| Baseline app (unmodified) builds and launches | ✅ login screen |
| App with liveness builds, launches, no crash | ✅ |
| Mock source with **no media file** | ✅ clean "Camera device not available…" + Retry connection |
| Real engine init on device: OpenCV native load, model SHA-256 check, MediaPipe + ONNX sessions | ✅ (logcat: `OpenCV 4.10.0` loaded; reached device stage, so engine init passed) |
| CameraX front camera into the Flutter texture, frames into MediaPipe | ✅ live preview inside the oval; synthetic scene correctly gave **NO_FACE** |
| Mock frames folder with a face, too small in frame | ✅ **POOR_QUALITY / "Move closer"** |

## Phase 5: workflow integration

| Workflow | Hook | Behaviour |
|---|---|---|
| Resident face capture | `ensureResidentFaceLiveness(context, modality)` before each `BiometricsApi().invokeDiscoverSbi` (`biometric_scan_middle_block.dart` ×2, `biometric_capture_scan_block_portrait.dart`) | Only modality `Face` is gated. On pass, the existing SBI/MDS capture runs unchanged. **Native defence in depth:** `BiometricsDetailsApi.invokeDiscoverSbi` refuses `Face` without a fresh single-use pass (`LivenessGate`, TTL `livenessTokenTtlSeconds`) and audits `REG-LIV-006`. |
| Operator login | `login_page.dart` `_navigateToHomePage()`, after password auth and centre/machine checks | Login completes only if liveness passes. On cancel, failure or max retries: `logoutUser()` and the operator stays on the login page. |
| Supervisor approval | `authenticate_dialogbox.dart` `submitReviews()`, after the supervisor password check | Review is submitted only if liveness passes. Otherwise it shows the same "login failed" feedback. |

Each workflow uses its own policy (`policies.resident|operator|supervisor`). With `enabled:false` the gate is a no-op: no screen, and the native gate allows. Bundled defaults: resident **on**; operator and supervisor **off**, because that is new UX and policy on top of password login and needs a programme decision (see D3 in 01-architecture.md).

### Offline behaviour

- Every liveness decision runs on-device. No network call is made.
- On `start`, `config.refresh(NetworkUtils.isNetworkConnected())`: offline means a no-op, so the cached config stays active. Online means the synced global param `mosip.registration.face.liveness.config` is re-read, validated, its SHA-256 integrity checked, and only then applied and cached. A bad update never replaces a good one (`CONFIG_REJECTED` audit). See `LivenessConfigTest` (offline, tamper, corrupt cache, restart-from-cache).
- The UI shows a small **Offline** pill (connectivity_plus).

### Audit log entries

Every record has timestamp, session id, workflow, attempt, state transition (from→to), challenge type, outcome and error code (`LIV-xxx`). PAD decision records also carry mean/min/max live score, thresholds and the motion measure. PERFORMANCE records carry frame count and mean/p95/max inference ms with model ids. The record type can only hold strings and enums, so frames cannot be attached (`PrivacyContractTest`).

### Not verified end to end, and why

- **SBI capture after a resident pass** needs the MOSIP Mock MDS app and an onboarded machine with server sync. Neither is available here (no MOSIP sandbox credentials), so the native gate path is covered by code review and unit tests only.
- **Operator/supervisor flows** need a synced machine and users for password login to succeed first. The gate placement was code-reviewed, and the liveness screen itself was run on device through the preview entrypoint.
