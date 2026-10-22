# Discovery: MOSIP Android Registration Client

Source reviewed (read-only): a local checkout, branch `feature/face-liveness-pad`, HEAD `9be3e309` ("Add face liveness and demo account support"). Version `1.1.1+1`, Flutter 3.10.4, native Android (Java) modules `clientmanager`, `keymanager`, `packetmanager`, `livenessmanager`, `transliterationmanager`.

Nothing outside `Mosip_desktop/` was modified.

---

## 0. What this product actually is (read first)

The existing app is **not** a consumer face-verification app. It is the **MOSIP Registration Client**, a tablet app used by **trained operators at a registration centre** to enrol residents into a national ID system. That changes several assumptions in the brief:

| The brief assumes | What the code does |
|---|---|
| Users enrol their own face and later verify against it | An **operator** enrols a **resident**: demographics, documents, and biometrics (10 fingers, 2 irises, face). The output is an **encrypted registration packet** uploaded to the MOSIP server. |
| "Verification / authentication" means 1:1 face matching | There is **no face matching** anywhere (docs/liveness/08-technical-notes.md, limit 6). "Verification" means: (a) a **face liveness / PAD check** before face capture and optionally at operator login or supervisor approval, (b) a **supervisor approve/reject** review of packets, (c) **server processing status** of uploaded packets. |
| "Results / history" | **Manage Applications**: the list of packets with client status, server status and review status. **Acknowledgement**: the receipt after a registration. |
| Self sign-up | Accounts are created by an administrator in Keycloak/Admin Portal. "Sign up" only **prepares an access-request email**. A **local demo account** exists in debug builds only. |
| A REST backend the UI calls directly | The Flutter UI calls **native Java over Pigeon/MethodChannels**. Java does the networking, cryptography, local DB (Room/SQLite), packet building and sync. The server requires **machine-key-signed requests from a registered machine**. |
| Webcam capture of the face | Biometrics come from certified **SBI/MDS devices** (or Mock MDS). The phone camera is used only for the **liveness check** (CameraX) and for document scanning. The stored face is the SBI capture, not the liveness frame. |

Consequence for this project: the website and web demo can match the **flows, fields and validation** exactly, but they cannot talk to a real MOSIP server the way a browser normally would. See section 6 (open questions Q1 to Q3) for the decision this needs.

---

## a) User-facing flows

### a1. App start
1. Load env (`flutter_config`), locale, machine details, language list, password max length, location hierarchy, git build info. Audit `REG-LOAD-001`.
2. Idle timeout and auto-logout grace period loaded from global params (`mosip.registration.idle_time`, `mosip.registration.refreshed_login_time`). Defaults in Dart: 900 s idle, 600 s grace.
3. Inactivity tracker wraps the whole app. On timeout: dialog "You have been idle / For security reasons, you'll be logged out in $TIMER" with **LOG OUT** / **STAY LOGGED IN**.

### a2. Login (two steps) — `lib/ui/login_page.dart`
1. **Step 1, Username:** pick UI language (chips; languages come from synced master data), enter username, tap **NEXT**.
   - Client checks: not empty, max 50 chars.
   - Native `validateUser(username, langCode)` checks the local user table (synced from server). Unknown user gives "User not found!". On success: "User Validated!", stores centre id/name and user name.
   - Link: "Don't have an account? **Sign up**" opens the request-access page (a4).
2. **Step 2, Password:** password, **LOGIN**, **BACK**, **FORGOT PASSWORD?**
   - Client checks: not empty; length not over `mosip.registration.username_pwd_length` (default 50).
   - Debug only: if a local demo account matches, start a demo session and skip the server.
   - Otherwise: check connectivity, save server version, call `login(username, password, isConnected)`.
     - Online: signed JWT to `POST /v1/syncdata/authenticate/useridpwd` (see c).
     - Offline: compare against the salted password hash saved at the last online login. No saved hash gives `REG_CRED_EXPIRED`.
   - Locked account check first (`REG_LOGIN_LOCKED`).
   - After success: if offline and centre inactive gives `REG_CENTER_INACTIVE`; machine inactive gives `REG_MACHINE_INACTIVE`. If online and centre/machine inactive: dialog "Sync to continue!" with a **Sync** action.
   - **First login on a machine** (`lastSyncTime == "LastSyncTimeIsNull"`): runs a full initial sync with a progress dialog, then restarts the app (success) or exits (failure).
   - **Operator face liveness** (if the `operator` policy is enabled; off by default): liveness screen must pass, otherwise logout.
   - Then route: not onboarded and not "Default" role goes to **Onboarding**; otherwise **Home**.
   - Forgot password: opens `mosip.registration.reset_password_url` in the browser; offline shows "No Internet Connection! / Please connect with internet and retry. / RETRY".
3. Hidden: long-press the MOSIP logo opens **Device Credentials** (machine name, public keys, key index) with **COPY TEXT** / **DOWNLOAD JSON**. These values are what an admin registers on the server. Double-tap (debug) opens the liveness debug menu.

### a3. Operator onboarding — `lib/ui/onboard/`
- Landing: "HELLO <name>", "Please tap on 'GET ONBOARDED'…", buttons **GET ONBOARDED** / **SKIP TO HOME** (skip only where allowed).
- Biometric onboarding capture of the operator for the attributes in `mosip.registration.operator.onboarding.bioattributes`, then **VERIFY & SAVE**: biometrics are sent to `POST /registrationprocessor/v1/registrationstatus/auth` (IDA auth). Success: "You have onboarded successfully." Title varies by role: Officer's / Operator's / Supervisor's Biometric Onboarding.
- The same screen is reused later as **Update Operator Biometrics** from Home.

### a4. Sign up / request access — `lib/ui/signup/signup_page.dart`
- Form fields in b2. **Send request to administrator** opens a `mailto:` addressed to global param `mosip.registration.signup.admin.email` (empty recipient if unset). No mail app: the request is copied to the clipboard. Password is never included.
- Debug only: "Testing only: local demo account" section (password + confirm). Stored on device with PBKDF2-HMAC-SHA256, 120 000 iterations, random salt. Native error codes `EXISTS` / `INVALID` / `NOT_AVAILABLE`.

### a5. Home / dashboard
- **Registration Tasks:** one card per active process from the UI spec (`NEW` New Registration, `UPDATE` Update UIN, `LOST` Lost UIN, `BIOMETRIC_CORRECTION` Biometric correction). Order and labels come from the spec.
- **Operational Tasks:** Synchronize Data; Update Operator Biometrics; Application Upload (Manage Applications); Pending Approval (supervisor only, shows "N application(s)"). Commented out in code, so **not** shipped: Download Pre-Registration Data, Check Updates, Center Remap Sync, Sync Activities.
- **Dashboard tab:** counters Packets Created / Packets Synced / Packets Uploaded (+ pending), and a Users table (User ID, User Name, Status: Onboarded / Active - Not Onboarded / User Inactive). Last updated time.
- Nav: Home, Dashboard, Settings, Profile, plus an online/offline indicator and last sync time.

### a6. Registration (enrolment) — `lib/ui/process_ui/generic_process.dart`
Driven entirely by the UI spec. Steps:
1. **Language selection** dialog: choose data-entry languages. Mandatory languages are pre-selected and locked; count between `mosip.min-languages.count` and `mosip.max-languages.count` ("Please select any 2 languages for data entry." is the sample copy). Also notification language.
2. Pre-checks before starting a packet (native) may block with: `PAK_APPRVL_MAX_TIME`, `PAK_UPLOAD_MAX_TIME`, `PAK_UPLOAD_MAX_COUNT`, `PAK_DISK_SPACE_LOW`, `REG_PKT_APPRVL_CNT_EXCEED`, `OPT_TO_REG_TIME_SYNC_EXCEED`, `OPT_TO_REG_OUTSIDE_LOCATION` (GPS, if `mosip.registration.gps_device_enable_flag` is on).
3. One page per **screen** in the process (order from spec): Consent, Demographic Details, Documents, Biometric Details. Screens with `preRegFetchRequired` show a **Pre-registration Application ID** fetch box (type or scan QR). Screens with `additionalInfoRequestIdRequired` show an Additional Info Request ID field.
4. For **UPDATE**: enter UIN (or VID) and pick which field groups to update (`autoSelectedGroups` are pre-ticked). Required-ness of other fields depends on that selection (MVEL `requiredOn`).
5. **Biometrics** per attribute group (Iris, Right Hand, Left Hand, Thumbs, Face, Exception photo): select device, **Scan**, see quality vs threshold, attempts left, best capture kept. Mark exceptions (Permanent/Temporary + comment). Face capture is gated by **resident face liveness** (on by default), enforced natively too.
6. **Preview** (server template rendered as HTML).
7. **Authentication**: operator re-enters password ("Authentication using Password"), then packet is created: `submitRegistrationDto(makerName)` returns the **RID** (Application ID).
8. **Acknowledgement**: receipt (HTML template) with **PRINT** / **SHARE** / **NEW REGISTRATION** / **GO TO HOME**.
9. Leaving mid-flow: "There is still some action required! / Clicking on logout will terminate the process and could result in data loss."

### a7. Face liveness check — `lib/ui/face_liveness/`
Used by 3 workflows: `RESIDENT` (before face capture, on), `OPERATOR` (after login, off), `SUPERVISOR` (after supervisor password, off).
- Intro: "Before we start / Look at the camera. Keep your face inside the oval. / Start". Camera permission requested.
- Live states and copy (exact text in b5): positioning, passive check, random challenges (blink, smile, turn left, turn right; 2 per attempt, 8 s each), success.
- Guidance states: no face, multiple faces, poor quality (lighting / move closer / hold still).
- Failures: liveness failed, PAD failed, challenge timeout, max retries ("All 3 attempts have been used. Please follow the recovery process.").
- System errors: device unavailable, camera error, invalid frames, engine error. "Retry connection" when offline.
- Header shows the workflow title and "Attempt n of max".
- **No scores, thresholds or attack details are ever shown** (enforced by tests).

### a8. Manage Applications (results/history) — `lib/ui/export_packet/`
- Table: Sl.no, Application ID, Reg. Date, Reg. Type, Client Status, Server Status, (Operator ID). "Displaying N Applications", "x/y Applications Selected".
- Search by Application ID. Filters: Client Status (Approved, Rejected, Exported), Server Status (Received, Processing, Accepted, Deletion). **Clear Filter**.
- Actions on selection: **UPLOAD** (sync then upload) and **EXPORT** (to device storage).

### a9. Pending Approval (supervisor) — `lib/ui/approve_packet/`
- Table of created packets with Review Status. Open a packet to view its preview template. **APPROVE** or **REJECT**.
- Reject dialog: "Reject Packet?", reason dropdown (reasons from master data, `getReasonList(langCode)`), required ("Please select a reason...").
- **SUBMIT** opens "Supervisor's Authentication using Password": username must equal the logged-in user; password; then supervisor liveness (if enabled). Then packets are marked and synced.

### a10. Settings — `lib/ui/settings/`
Tabs come from the server **settings spec** filtered by the user's roles (`access-control`):
- **Global Config Settings:** table Key / Server Value / Local Value, search "Search for Key". Only keys in the permitted list are editable. **Submit Changes**, then "Local Preferences have been saved successfully. Application restart is needed…".
- **Device Settings:** per modality list of discovered devices (name, id, status). Empty: "No devices found / Please connect your devices and tap 'Scan' to detect them".
- **Scheduled Job Settings:** each permitted job: ID, Name, last/next run, editable **Cron Expression** validated natively, **Sync now**.
- Other spec entries render as description-only tabs.

### a11. Sync
Manual **Synchronize Data** runs, in order: policy key, global params, user details, ID schema, master data, CA certs (+ kernel certs). Each can fail with its own message (b6). Success shows "Sync Completed Successfully" and a **Restart** dialog. Background jobs: packet status sync, pre-reg data download, deletion of old audits/packets/pre-reg records.

### a12. Profile / logout
Profile shows user id, name, email, roles, centre, machine. **LOGOUT** clears the token; success "You have been successfully logged out!", failure "Something went wrong, please try again after some time".

---

## b) Form fields

### b1. Login

| Field | Type | Required | Validation | Error message | Default |
|---|---|---|---|---|---|
| Language | chip group (single) | yes | one of synced languages; some may be disabled | n/a | device/app locale |
| Username | text | yes | non-empty after trim; max 50 chars; must exist in local user table | "Username is required!" / "Username should not exceed 50 characters!" / "User not found!" | empty |
| Password | password (show/hide toggle) | yes | non-empty; length ≤ `mosip.registration.username_pwd_length` (fallback 50) | "Password is required!" / "Password should not exceed 50 characters!" | empty |

NEXT/LOGIN buttons are disabled while the field is invalid. Server/offline errors in b6.

### b2. Request an account (sign up)

| Field | Type | Required | Validation | Error |
|---|---|---|---|---|
| Full name | text | yes | non-empty | "This field is required" |
| Email | email | yes | `^[^@\s]+@[^@\s]+\.[^@\s]+$` | "Enter a valid email address" |
| Phone number | tel | no | empty or `^\+?[0-9 ()-]{7,20}$` | "Enter a valid phone number" |
| Preferred username | text | yes | `^[A-Za-z0-9._-]{3,50}$` (lower-cased for demo accounts) | "Use 3 to 50 letters, digits, dots, dashes or underscores" |
| Registration centre | text | yes | non-empty | "This field is required" |
| Requested role | select | yes | `Registration officer` (default) / `Supervisor` | n/a |
| Note (optional) | textarea (3 lines) | no | none | n/a |
| *Demo:* Password | password | yes | length ≥ 8 | "Use at least 8 characters" |
| *Demo:* Confirm password | password | yes | equals password | "Passwords do not match" |

### b3. Registration (server-driven UI spec)

Every field comes from the synced UI spec (`ProcessSpecApi.getNewProcessSpec`). Shape of a field: `id, controlType, type, fieldType (default|dynamic), required, requiredOn[{engine:'MVEL', expr}], visible{expr}, validators[{type:'regex', validator, langCode, errorCode}], label{lang:text}, subType, group, locationHierarchy, bioAttributes[], conditionalBioAttributes[], exceptionPhotoRequired, transliterate, format`.

**Control rules (apply to any spec):**

| controlType | Renders as | Validation |
|---|---|---|
| `textbox` | text input, one per selected language for `simpleType` (with optional transliteration) | required or `requiredOn` true: empty gives "Please enter a value"; regex mismatch gives "Invalid input". Optional and empty: OK; optional and non-empty: regex applies |
| `dropdown` | select; values from master data (`dynamic`) or location hierarchy (`default` + location group, cascading by parent) | required: "Please select a value" |
| `ageDate` | date of birth: date picker or age in years | format `yyyy/MM/dd`; max date today; min date today minus `mosip.registration.max_age` (default 150); "Age should not be greater than {age} years" |
| `date` | date picker | format `yyyy/MM/dd` |
| `checkbox` | consent checkbox | required must be ticked |
| `html` | consent text from template | display only |
| `fileupload` | document type select + **Reference Number** + scan/upload pages | doc type from `getDocumentCategories`; max file size `mosip.registration.document_size` bytes (default 2 MB): "File size exceeds limit {size}"; reference number: "Please enter reference number" |
| `biometrics` | capture blocks per attribute | per-modality threshold and retries (b4) |
| `button` | single-choice buttons | required |
| `radio` / gender | radio/button group | required |

**Sample spec fields (`assets/id-schema/ui-spec-sample.json`). The real deployment's spec replaces this at sync time.**

Process **NEW** (New Registration):

| Screen | Field id | Control | Required | Validator (regex) / rule |
|---|---|---|---|---|
| Consent | consentText | html | yes | template |
| Consent | consent | checkbox | yes | must accept |
| Consent | preferredLang | dropdown (dynamic) | yes | |
| Demographic (pre-reg fetch) | fullName | textbox | yes | `^(?=.{3,50}$).*` |
| | dateOfBirth | ageDate | yes | `^(1869\|18[7-9][0-9]\|19[0-9][0-9]\|20[0-9][0-9])/([0][1-9]\|1[0-2])/([0][1-9]\|[1-2][0-9]\|3[01])$` |
| | gender | dropdown (dynamic) | yes | |
| | addressLine1 | textbox | yes | `^(?=.{0,50}$).*` |
| | addressLine2 | textbox | yes | `^(?=.{3,50}$).*` |
| | addressLine3 | textbox | yes | `^(?=.{3,50}$).*` |
| | maritalStatus | dropdown (dynamic) | no | |
| | applicantLanguage | dropdown (dynamic) | no | |
| | nicIssueDate | date | no | |
| | modeOfdelivery | dropdown (dynamic) | no | |
| | residenceStatus | dropdown (dynamic) | no | |
| | region → province → city → zone → postalCode | dropdown (location cascade) | yes | region/province/city `^(?=.{0,50}$).*`; postal `^[(?i)A-Z0-9]{5}$\|^NA$` |
| | phone | textbox | yes | `^[+]*([0-9]{1})([0-9]{9})$` |
| | email | textbox | yes | `^[A-Za-z0-9_\-]+(\.[A-Za-z0-9_]+)*@[A-Za-z0-9_-]+(\.[A-Za-z0-9_]+)*(\.[a-zA-Z]{2,})$` |
| | introducerName | textbox | if ageGroup INFANT/MINOR | visible only then |
| | introducerRID | textbox | if minor and no introducerUIN | `^([0-9]{29})$` |
| | introducerUIN | textbox | if minor and no introducerRID | `^([0-9]{10})$` |
| Documents | proofOfAddress (POA) | fileupload | yes | |
| | proofOfIdentity (POI) | fileupload | yes | |
| | proofOfRelationship (POR) | fileupload | if minor | |
| | proofOfDateOfBirth (POB) | fileupload | yes | |
| | proofOfException (POE) | fileupload | no | |
| Biometrics | individualBiometrics | biometrics | yes | leftEye, rightEye, 10 fingers, face; conditional by age group |
| | introducerBiometrics | biometrics | if minor | same attributes |

Process **UPDATE** (Update UIN): same consent; UIN/VID input + "Select all the attributes that need to be updated"; demographic fields `fullName, dateOfBirth, gender, permanentAddress, temporaryAddress, maritalStatus, applicantLanguage, residenceStatus, region…postalCode, phone, rNumber, email, introducerName/RID/UIN` (introducer RID/UIN regex `^([0-9]{10,30})$`); biometrics `individualBiometrics` (if Biometrics group selected) or `individualAuthBiometrics` (otherwise); documents required based on which fields are being updated. Auto-selected groups: Consent, PreferredLanguage, Documents, Biometrics.

Process **LOST** (Lost UIN): all demographic fields optional, extended set (printed/lay names, birth certificate group, NIC number `^([0-9]{9}[x|X|v|V]|[0-9]{12})$`, NIC serial `^([A-Z]{1}[0-9]{7})$`, dual citizenship group `^[0-9]{3,20}$`, profession, father/mother/spouse/sibling1-2/child1-2 details); biometrics required.

Process **BIOMETRIC_CORRECTION**: consent + biometrics + exception proof.

**Other registration inputs:**

| Field | Validation | Error |
|---|---|---|
| Pre-registration Application ID | digits only, exactly `mosip.kernel.prid.length` | "Please Enter Application ID" / "Application ID must be exactly {length} digits." / "Application ID does not exist!" |
| UIN (update) | digits only; exactly `mosip.kernel.uin.length` (default 10); VID uses `mosip.kernel.vid.length` | "Please enter a valid UIN" |
| Additional Info Request ID | free text | n/a |
| Data entry languages | count within min/max; mandatory langs locked | dialog copy |
| Bio exception | Exception Type: Permanent / Temporary; Comments text | per attribute |
| Operator auth before submit | password | b1 rules |

### b4. Biometric capture parameters

| Param (global) | Meaning |
|---|---|
| `mosip.registration.{leftslap_fingerprint,rightslap_fingerprint,thumbs_fingerprint,iris,face}_threshold` | min quality per modality |
| `mosip.registration.num_of_{fingerprint,iris,face}_retries` | attempts per modality |
| `mosip.registration.capture_time_out` | SBI capture timeout |
| `mosip.registration.allowed-bioattributes`, age-group config | which attributes are required |

### b5. Supervisor approval

| Field | Required | Validation | Error |
|---|---|---|---|
| Reject reason | yes (reject only) | one of `getReasonList(lang)` | "Please select a reason..." |
| Supervisor username | yes | non-empty, ≤ 50, **must equal the logged-in user id** | "Username is required!" / "Username should not exceed 50 characters!" / "Username invalid!" |
| Supervisor password | yes | non-empty | "Password is required!" |

### b6. Settings

| Field | Validation |
|---|---|
| Local config value | only keys in `getPermittedConfigurationNames()` |
| Job cron expression | native `isValidCronExpression` (Quartz-style) |
| Device selection | from `getListOfDevices(modality)` |

### b7. Error and status copy (verbatim)

Login error codes → message: `REG_TRY_AGAIN` "Login Failed. Try Again!", `REG_INVALID_REQUEST` "Password incorrect!", `REG_MACHINE_NOT_FOUND` "This device has not been onboarded yet. Please reach out to your administrator for assistance!", `REG_NETWORK_ERROR` "Login failed. Check network connection!", `REG_CRED_EXPIRED` "Credentials not found or are expired. Please try online login!", `REG_MACHINE_INACTIVE` "Machine is not active!", `REG_CENTER_INACTIVE` "Center is not active!", `REG_LOGIN_LOCKED` "Maximum login attempt limit reached. Try again later!", `KER_SYN_AUTH_001` "Unable to get Authentication Token!", fallback "Some error occurred!".

Registration pre-check codes: listed in a6 step 2 with copy in `assets/l10n/app_en.arb` key `errors`.

Sync: "Policy Key Sync Failed", "Global Params Sync Failed", "User Details Sync Failed", "IDSchema Sync Failed", "Master Data Sync Failed", "CA Certificates Sync Failed", "Sync. Failed / Please connect with the system admin or supervisor for help."

Liveness copy: `lib/ui/face_liveness/liveness_copy.dart` (to be mirrored verbatim).

Locales shipped: en, ar (RTL), fr, hi, kn, ta.

---

## c) Backend contract

### c1. Two layers
1. **UI ↔ native** (what the mobile UI calls): Pigeon host APIs in `pigeon/*.dart` plus MethodChannels `io.mosip.registration_client/face_liveness` (+ `/events`), `/demo_account`, `/sync_restart`. Full list in c5.
2. **Native ↔ MOSIP server** (Retrofit, `SyncRestService.java`). This is the real backend.

### c2. Transport and auth
- **Base URL:** `BuildConfig.BASE_URL` from `android/build.gradle` `serverBaseURL` (sample `https://api-internal.sandbox.xyz.net`). Health: `/v1/syncdata/actuator/health`; info: `/v1/syncdata/actuator/info`.
- **Timeouts:** read/write 60 000 ms default, overridable by `mosip.registration.HTTP_API_READ_TIMEOUT` / `_WRITE_TIMEOUT`.
- **JSON:** Gson with `LOWER_CASE_WITH_UNDERSCORES` field naming (DTO fields with explicit `@SerializedName` keep camelCase).
- **Session auth:** header `Cookie: Authorization=<token>` on every call once logged in (`RestAuthInterceptor`).
- **Login request** (`SyncRestUtil.getAuthRequest`): a JWT-like string, signed with the **machine's private key** held in Android Keystore (alias `SIGNV`):
  - header `{"kid" : "<SHA-256 fingerprint of machine public key>"}`
  - payload `{"userId": "...", "password": "...", "authType":"NEW", "timestamp": "<ISO UTC>"}`
  - `base64(header).base64(payload).<signature>` wrapped as `{ id, version, requesttime, metadata, request: "<that string>" }`.
- **Token:** response `response` is a Keycloak JWT. Client rejects if expired (15 s leeway) or `realm_access.roles` has none of `REGISTRATION_SUPERVISOR`, `REGISTRATION_OFFICER`, `REGISTRATION_OPERATOR`. Also reads `Default` role, `preferred_username`, `email`, name claim.
- **Machine registration is mandatory:** server code `KER-SNC-155` → `REG_MACHINE_NOT_FOUND`. The admin registers the machine using the public keys from "Device Credentials".

### c3. Envelopes
```
Request:  { id?, version?, requesttime, metadata?, request: T }
Response: { id, version, responsetime, metadata, response: T, errors: [{ errorCode, message }] }
RegProc:  { id, version, responsetime, response: T, errors: [{ errorCode, message }] }
```
Errors arrive with HTTP 200 and a non-empty `errors` array; the client takes `errors[0]`. Non-2xx → generic retry; transport failure → `REG_NETWORK_ERROR`.

### c4. Endpoints

| # | Method | Path | Purpose | Body / params |
|---|---|---|---|---|
| 1 | POST | `/v1/syncdata/authenticate/useridpwd` | login | signed request (c2) → `response: token` |
| 2 | GET | `/v1/syncdata/v2/clientsettings` (v1: `/v1/syncdata/clientsettings`) | master data | query map (keyindex, lastupdated, version…) |
| 3 | GET | `/v1/syncdata/getCertificate` | policy key | `applicationId, referenceId, version` |
| 4 | GET | `/v1/syncdata/latestidschema` | ID schema + UI spec | `version, domain` |
| 5 | GET | `/v1/syncdata/v2/userdetails` (v1 variant) | centre users | `keyindex, version` |
| 6 | GET | `/v1/syncdata/configs/{key_index}` (v1: `{machine_name}`) | global params | `version` |
| 7 | GET | `/v1/syncdata/getcacertificates` | CA certs | `lastupdated, version` |
| 8 | POST | `/registrationprocessor/v1/registrationstatus/syncV2` (v1: `/sync`) | register RIDs before upload | headers `timestamp`, `Center-Machine-RefId`; body = **encrypted** string |
| 9 | POST (multipart) | `/registrationprocessor/v1/packetreceiver/registrationpackets` | upload packet | one part, `application/zip`, the **encrypted packet** |
| 10 | POST | `/registrationprocessor/v1/registrationstatus/packetexternalstatus` (v1: `/search`) | server status | `PacketStatusRequest` |
| 11 | GET | `/registrationprocessor/v1/registrationstatus/getCertificate?applicationId=IDA&referenceId=INTERNAL` | IDA cert | |
| 12 | POST | `/registrationprocessor/v1/registrationstatus/auth` | operator biometric auth (onboarding) | headers `Authorization`, `Signature`, `Cookie`; body map |
| 13 | GET | `{url}` | download MVEL/scripts | header map, `keyindex` |
| 14 | GET | `/preregistration/v1/sync/{preRegId}/{stationId}` | pre-reg data | `version` |
| 15 | POST | `/preregistration/v1/sync` | pre-reg id list | `PreRegistrationDataSyncDto` |

Version switch: the client stores `mosip.registration.server_version`; `1.1.5*` servers use the v1 variants and need screen headers saved locally.

**Upload format:** packets are **multipart** zip files, never base64 JSON. Biometrics and documents are inside the encrypted packet. Documents are raw bytes per page; size limit `mosip.registration.document_size` (default 2 MB). There is no image upload endpoint.

**Rate limits:** none in the client. Throttling is local: login lockout (d), packet count/time limits (a6).

### c5. UI ↔ native API surface (to mirror in the TS client)

`AuthResponseApi` login/logout/forgotPasswordUrl/getIdleTime/getAutoLogoutPopupTimeout/getRolesByUserId/getPasswordLength · `UserApi.validateUser` · `MachineApi.getMachineDetails/getCenterName` · `SyncApi` (per-type syncs, last/next sync time, jobs, cron, reason list, pre-reg ids, cleanup) · `DashBoardApi` (users, packet counts, updated time) · `ProcessSpecApi` (UI schema, process spec, languages, settings spec, global param) · `RegistrationDataApi` (startRegistration, MVEL visible/required, preview, submit → RID, application id, additional req id, location) · `DemographicsApi` · `DocumentApi` · `DocumentCategoryApi` · `DynamicResponseApi` (field values, location hierarchy, languages, pre-reg fetch) · `BiometricsApi` (discover SBI, capture, attempts, exceptions, onboarding, devices) · `PacketAuthApi` (supervisor auth, sync/upload, list packets, status, review) · `GlobalConfigSettingsApi` · `CommonDetailsApi` (templates, help URLs) · `TransliterationApi` · `AuditResponseApi` · `SecureScreenApi` (FLAG_SECURE) · Face liveness channel (isEnabled, getMaxAttempts, start → textureId, retry, cancel, setSource; event stream).

Packet data shapes exposed to UI: packet list rows are JSON strings parsed in Dart (`approve_packets_provider.dart`, `export_packet_provider.dart`).

---

## d) Business rules and states

**Roles:** `REGISTRATION_OFFICER`, `REGISTRATION_OPERATOR`, `REGISTRATION_SUPERVISOR`, `Default`. Supervisor sees Pending Approval. Settings tabs filtered by role.

**User states:** Active - Not Onboarded · Onboarded · User Inactive. Locked: after more than `mosip.registration.invalid_login_count` (default 50) failed attempts, locked for `mosip.registration.invalid_login_time` minutes (default 2). Counter resets on success.

**Machine/centre:** active or inactive (both checked at login).

**Packet client status:** `CREATED → APPROVED | REJECTED → SYNCED → UPLOADED`, or `EXPORTED`.
**Packet server status:** `PROCESSED, ACCEPTED, RESEND, REJECTED, REREGISTER, UPLOAD_PENDING`; UI filter labels: Received ("Packet has reached Packet Receiver"), Processing, Accepted, Deletion ("Considered for deletion").
**Packet task status:** `SYNC_STARTED/COMPLETED/FAILED/ALREADY_COMPLETED`, `UPLOAD_STARTED/COMPLETED/FAILED/ALREADY_COMPLETED`.
**Review status:** pending / approved / rejected (+ comment = reason).

**Registration limits:** max packets pending approval (`reg_pak_max_cnt_apprv_limit`), max approval age (`reg_pak_max_time_apprv_limit`), max time since last export/upload, offline packet count, min disk space, max time since last sync, GPS distance from centre.

**Face liveness (default config):**

| | resident | operator | supervisor |
|---|---|---|---|
| enabled | **true** | false | false |
| passiveThreshold | 0.90 | 0.90 | 0.92 |
| padRejectThreshold | 0.30 | 0.30 | 0.30 |
| challenges | blink, smile, turn L/R | blink, turn L/R | blink, smile, turn L/R |
| challengeCount / timeout | 2 / 8 s | 2 / 8 s | 2 / 8 s |
| maxRetries | 3 | 3 | 3 |
| minFaceSize / maxFaces | 0.25 / 1 | 0.25 / 1 | 0.25 / 1 |
Quality: brightness 50–220, sharpness ≥ 25 (Laplacian variance), motion ≤ 0.15. Pass token TTL 60 s, single use. Fail-closed on any error. Config arrives as global param `mosip.registration.face.liveness.config` with SHA-256 integrity; bundled fallback.

States: `IDLE, POSITIONING, PASSIVE_CHECK, CHALLENGE, SUCCESS, LIVENESS_FAILED, PAD_FAILED, CHALLENGE_TIMEOUT, NO_FACE, MULTIPLE_FACES, POOR_QUALITY, DEVICE_UNAVAILABLE, CAMERA_ERROR, INVALID_FRAMES, ENGINE_ERROR, MAX_RETRIES`. Hints: `LIGHTING, FAR, BLURRY`. Unknown state from native maps to ENGINE_ERROR.

**Session:** idle timeout + grace popup; FLAG_SECURE on sensitive screens (screenshots blocked); auto logout returns to login.

---

## e) Env / config the client needs

| Name | Purpose | Default / source | Secret? |
|---|---|---|---|
| `MOSIP_BASE_URL` | server base URL | none (sample sandbox URL) | no |
| `MOSIP_HEALTH_PATH` | health check | `/v1/syncdata/actuator/health` | no |
| `MOSIP_INFO_PATH` | actuator info | `/v1/syncdata/actuator/info` | no |
| `MOSIP_HTTP_READ_TIMEOUT_MS` / `_WRITE_` | timeouts | 60000 | no |
| `MOSIP_SIGNUP_ADMIN_EMAIL` | fallback for request-access email (normally a global param) | unset | no |
| Machine signing key pair | sign login and packets | generated on first run, stored in OS keychain | **yes, never in env or repo** |
| Auth token | session | from login, OS secure storage | **yes** |
| `MOSIP_SBI_PORT_RANGE` (desktop) | SBI discovery on localhost | 4501–4600 (assumption, SBI spec) | no |
| `DEMO_MODE` | enable local demo accounts and mock data | dev only | no |

Note: the Android repo has `debugPassword = "your-debug-password"` placeholder in `android/build.gradle`; do not copy it.

Everything else (thresholds, timeouts, lengths, URLs for help pages) is a **server global param** fetched at sync, not client env.

---

## f) Open questions and assumptions

**Q1. Product framing (blocker for copy and IA).** The brief describes consumer face verification. The app is an operator enrolment client with liveness, not face matching.
*Assumption:* we build what the code does. Website copy describes "secure, offline-capable identity registration with on-device face liveness". We do **not** claim face matching, 1:1 verification, accuracy figures, or certification (docs say APCER/BPCER are unmeasured and it is not ISO 30107 certified).

**Q2. How the browser website talks to MOSIP.** Login needs a registered machine's private key, sync bodies and packets are encrypted with server keys, and CORS on MOSIP internal APIs is not open to browsers.
*Assumption:* the website's functional pages (auth, enrolment, liveness, results, settings) run in **demo mode** with a typed mock API that follows the exact contract, unless a gateway/BFF is provided. The typed client is written against the real paths so a BFF can be plugged in through `MOSIP_BASE_URL`.

**Q3. How far the web demo goes against a real server.** Electron (Node) can hold a machine key pair (in the OS keychain via `safeStorage`) and sign login requests, so **login, sync of config/master data/UI spec/users, packet status** are realistic. Building **encrypted MOSIP packets** (CBEFF biometrics, policy-key encryption, signing, packetmanager format) is a large port of `packetmanager` and is not realistic in this scope.
*Assumption:* the web demo does real login + sync + status against a configured server, and runs registration in full UI with packet **submission mocked** (clearly labelled), behind a `PacketService` interface that can be implemented later.

**Q4. Biometric devices on desktop.** Mobile uses Android SBI intents. Desktop MOSIP SBI devices expose HTTP on localhost (`MOSIPDISC`, `MOSIPDINFO`, `CAPTURE`, `RCAPTURE`, ports 4501–4600).
*Assumption:* desktop implements SBI discovery over localhost with a Mock MDS fallback; webcam (getUserMedia) is used **only** for the liveness step, as on mobile.

**Q5. Liveness engine on desktop/web.** Mobile uses MediaPipe Face Landmarker + MiniFASNetV2 (ONNX) natively.
*Assumption:* web/desktop use MediaPipe Tasks for Web (WASM) for landmarks/challenges and onnxruntime-web for MiniFASNetV2, same thresholds and state machine, models bundled locally (no runtime download). If this proves too heavy for the timeline, ship challenge-only with the passive PAD stage behind a flag, and say so in the UI. Models are Apache-2.0 per `DEPENDENCIES.md`.

**Q6. UI spec source.** Fields differ per deployment.
*Assumption:* the renderer is spec-driven; the bundled sample spec is used in demo mode. MVEL expressions (`requiredOn`, `visible`) are evaluated natively on mobile; we implement the subset used in the sample (`identity.get('x') == 'Y'`, `||`, `&&`, `!`, `== nil`, `== empty`, `contains`) and treat unknown expressions as "required/visible" (fail safe), logged as a warning.

**Q7. Languages.** Six locales ship, including Arabic (RTL).
*Assumption:* English first; string catalogue copied from `app_en.arb`; structure ready for the others and RTL.

**Q8. Stats on the landing page.** The brief asks for 4 count-up stat tiles.
*Assumption:* only numbers that are true from the code: 3 liveness workflows, 4 challenge types, 13 biometric attributes per resident, 6 UI languages, 16 liveness states, 0 frames stored. No user counts or accuracy rates.

**Q9. "Join the waitlist" banner.** There is no waitlist backend.
*Assumption:* the banner links to the request-access page.

### Inconsistencies noticed in the existing code (for the final report)
1. `username_exceed` and `password_exceed` messages hard-code "50" while the password limit is a server param.
2. Login lockout default of **50** failed attempts (`UserDetailRepository`) is very permissive.
3. Dart idle default comment says "5 minutes" but the value is 900 s (15 min).
4. `introducerRID` regex is `^([0-9]{29})$` in NEW but `^([0-9]{10,30})$` in UPDATE/LOST; `introducerUIN` is exactly 10 digits in NEW.
5. Sample spec labels have typos and copy-paste errors ("Notification Langauge", `bcertificateDistrict` labelled "Full Name", `sibling1RID` labelled "Full Name", `child2CivilStatus` labelled "Full Name").
6. Server status filter values are English display strings ("Packet has reached Packet Receiver") rather than the `PacketServerStatus` enum.
7. Liveness copy is English-only constants, not in ARB files (acknowledged in docs).
8. `isEnabled` fails closed to `true` but the default operator/supervisor policy is `false`; a broken native call turns liveness on for login.
