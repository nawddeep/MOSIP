# Team Impala: Momento

MOSIP Decode 2026, Problem Statement 04: Face Liveness and Presentation Attack Detection for Registration and Biometric Authentication.

## What we built

We added face liveness and presentation attack detection to the MOSIP Registration Clients, on Android and on Desktop. A passive check (a MiniFASNet ensemble) runs first; when needed, a random active challenge (blink, smile or head turn) must be performed live. A successful check issues a single-use, short-lived pass that gates resident face capture, operator login and supervisor approval. The same engine runs on both clients. Audit events (`REG-LIV-001` to `REG-LIV-006`) go through MOSIP's own audit service, and the configuration comes from the global parameter `mosip.registration.face.liveness.config`. It works offline, adds no new database tables and shows no scores on screen.

## Team

Team Impala: 

- Navdeep Vyas ([nawddeep](https://github.com/nawddeep))
- Pratik Kansara ([pratik-aep](https://github.com/pratik-aep))
- Manan Ramani ([Manan-013](https://github.com/Manan-013))
- Charvi Paliwal ([charvi11](https://github.com/charvi11))
- Divyanshu Nagarchi ([divyanshdamami2018-web](https://github.com/divyanshdamami2018-web))

Charvi Paliwal built the in-browser web demo in https://memento-mosip-registration-client.vercel.app.

## Quick links

| What | Where |
|---|---|
| Demo video | Team Impala : demo video link |
| Submission document | Team Impala: submission document link |
| Test recordings | [Test_Recording - Android app & website (Google Drive)](https://drive.google.com/drive/folders/1sS7vi-nFq0Z8lqwoh-1rxfwm4llc6EbS) |
| Test evidence | Android: [docs/liveness/12-test-evidence.md](docs/liveness/12-test-evidence.md) · Desktop: [desktop/docs/liveness/test-evidence.md](desktop/docs/liveness/test-evidence.md) |
| Release APK | [decode-2026-v1](https://github.com/nawddeep/MOSIP/releases/tag/decode-2026-v1): a production-like build and an evaluation build for judges (works offline) |
| Android liveness docs | [docs/liveness/README.md](docs/liveness/README.md) |
| Desktop liveness docs | [desktop/docs/liveness/README.md](desktop/docs/liveness/README.md) |
| Desktop upstream comparison | [nawddeep/registration-client](https://github.com/nawddeep/registration-client) (fork of mosip/registration-client) |
| Judge guide | [docs/liveness/11-judge-evaluation-guide.md](docs/liveness/11-judge-evaluation-guide.md) |
| API documentation | [docs/API.md](docs/API.md) |

## Status, honestly

- CI is green for both clients: the Android liveness tests, the Flutter liveness tests and the Desktop liveness tests run on GitHub Actions.
- Android measurements so far come from an Android 14 emulator; real-device results: see the evidence files linked above, which are still templates.
- The Desktop client has not yet run inside a MOSIP environment (it needs one, and a Windows 10 machine with TPM 2.0); its engine, adapters, audit chain and screens were tested on their own.
- The MiniFASNet model weights have no separate licence upstream: a known issue, see [docs/liveness/DEPENDENCIES.md](docs/liveness/DEPENDENCIES.md).
- French and Arabic wording of the Desktop liveness strings still needs a native speaker's review: see [docs/review/desktop-liveness-translations.md](docs/review/desktop-liveness-translations.md).

## Repository layout

```
/            Android Registration Client with face liveness
/desktop     Desktop Registration Client (MOSIP registration-client v1.2.0.2) with face liveness
/website     In-browser web demo (mock data, works offline)
```

The Android client (Flutter + Java) is at the repository root. The Desktop client (Java 11, JavaFX, Maven) is in
[desktop/](desktop/), imported as one squashed commit (the full history is in the fork linked below); its liveness documentation is
[desktop/docs/liveness/README.md](desktop/docs/liveness/README.md). Desktop changes are made in `desktop/` in this repository.
The web demo is in [website/](website/index.html) (see [website/README.md](website/README.md)).

`desktop/` includes MOSIP's own public test fixtures (test keys, sample images) from upstream registration-client; they are not ours.

Desktop upstream comparison: https://github.com/nawddeep/registration-client (fork of mosip/registration-client)

## How to run

Android (Flutter 3.10.4, JDK 21, Android SDK 34; details in [docs/liveness/README.md](docs/liveness/README.md)):

```bash
bash pigeon.sh                                   # pub get, build_runner, pigeon code generation
flutter build apk --debug                        # or: flutter run
cd android && ./gradlew :livenessmanager:testDebugUnitTest
flutter test test/face_liveness
```

Desktop (JDK 11, Maven; details in [desktop/docs/liveness/README.md](desktop/docs/liveness/README.md)):

```bash
cd desktop/registration && mvn clean install
```

---

## Upstream MOSIP developer guide (unchanged)

The Android Registration Client is a tablet application that serves as a portable version of the existing desktop Registration Client. It has been developed to support accessibility on all Android devices. The creation of the Android Registration Client was driven by the need to meet the mobility requirements of countries adopting MOSIP.

# Developer Guide

The documentation here will guide you through the pre-requisites and the other necessary details required for Android Registration Client developer setup.

The android-registration-client repository contains the Android Registration Client software for MOSIP. The feature-flutter branch focuses on integrating Flutter into the client.

## Setup

To set up the Android Registration Client with Flutter and Android Studio, follow the steps below:

#### Prerequisites

* Flutter SDK (3.10.4): Install Flutter by following the official [Flutter installation guide](https://flutter.dev/docs/get-started/install).
* Android Studio (or Any IDE of your choice): Download and install Android Studio from the official [Android Studio website](https://developer.android.com/studio).

#### Step 1: Clone the Repository

The `develop` branch of android-reg-client is currently being actively developed. If you wish to access this branch, you can clone the repository by executing the following command in your terminal. Alternatively, you can download one of the releases available in the repository's release section.

```
git clone -b feature-flutter https://github.com/mosip/android-registration-client.git
```

**Active Branches**:

* [release-1.1.x](https://github.com/mosip/android-registration-client/tree/release-1.1.x)(developer release branch)
* [develop](https://github.com/mosip/android-registration-client/tree/develop)(active development branch)

#### Step 2: Set up Flutter in Android Studio

1. To begin, launch Android Studio.
2. Next, select **Open an existing Android Studio project** and navigate to the cloned repository.
3. Open the `android-registration-client` directory as a project in Android Studio.
4. In order to integrate Flutter with Android Studio, install the Flutter plugin by accessing `File > Settings > Plugins` and searching for **Flutter**. Proceed to click on **Install** to install the plugin.
5. To ensure proper functionality, configure the Flutter SDK path by navigating to `File > Settings > Languages & Frameworks > Flutter` and specifying the Flutter SDK path as the location where you have installed Flutter.
6. Finally, save the changes by clicking on the "Apply" button.

**Customizing the Registration Client**

* Styling of the application can be configured by modifying these files `lib/utils/app_style.dart, lib/utils/app_config.dart`
* Application language bundles can be added to this path `lib/l10n` After adding the bundle run the below command to generate Localization data (Required for the first time).

```
flutter gen-l10n
```

* The label and application logo can be changed here android/app/src/main/AndroidManifest.xml

#### Step 3: Build and Run the Application

* The `pigeon.sh` file consists of the necessary commands for downloading dependencies and generating Flutter - Android native communication code. Please execute the `pigeon.sh` file or execute the commands within the file separately.
* Ensure you have connected an Android device or initiated an Android emulator.
* Open the terminal within Android Studio or use an external terminal.
* Navigate to the `android-registration-client` directory.
* Run the following command to build and execute the application:

```
flutter run
```

#### Step 4: Build, debug, and release APK

Execute the commands below to debug and release the APK

```
// Debug APK
flutter build apk --debug

// Release APK
flutter build apk --release
```

### Set up Mock MDS for Biometric Scan

The Mock MDS tool can be utilized to simulate the functionalities of biometric devices. The Mock MDS application is compliant with CTK standards and can serve as a substitute for Android SBI modules during testing and validation.

1. Install the Mock MDS application.
2. Access the **Settings** menu.
3. Under Device Configuration, choose **Registration** from the dropdown menu.
4. In P12 Configuration:
    * Enter the necessary credentials for the Device Key and upload the Device P12 file.
    * Enter the required credentials for the FTM Key and upload the FTM P12 file.
    * Complete all fields in MOSIP IDA Configuration.
5. In Modality Configuration, specify the quality score for Face, Finger, and Iris scans(these values can also be adjusted during testing).
6. Click on the **Save** button.
7. Go back to the Home Page and select `LOAD AND VALIDATE CERTIFICATES`.

A toast message will be displayed indicating the success of the validation process.

**Note**: To view the released version of the Mock SBI APK, click [here](https://github.com/mosip/android-camera-mds/releases/tag/vDP1).

To download the Mock SBI APK, click on `camera-mds.zip`.

### Contributions

If you would like to contribute to the Android Registration Client, please follow the guidelines outlined [here](https://docs.mosip.io/1.2.0/community/code-contributions).

### License

The Android Registration Client is licensed under the [MIT License](https://github.com/mosip/android-registration-client/blob/develop/LICENSE).

### Support

If you encounter any issues or have any questions, please open an issue on the [GitHub repository](https://github.com/mosip/android-registration-client/issues).

### Sources

* [Flutter- Get started: Install](https://flutter.dev/docs/get-started/install)
* [Android Studio- Download](https://developer.android.com/studio)

---

## Test Recordings

Screen-recorded test sessions for both the **Android app** and the **website demo** are available on Google Drive:

> 📹 **[Test_Recording – Google Drive](https://drive.google.com/drive/folders/1sS7vi-nFq0Z8lqwoh-1rxfwm4llc6EbS)**

The folder contains:
- **Android app recordings** — end-to-end liveness flow tested on real devices (passive check, active blink/smile/head-turn challenge, operator login, resident face capture, supervisor approval).
- **Website demo recordings** — in-browser demo ([website/](website/)) showing the same liveness engine running fully offline in a browser.

All recordings were captured by **Divyanshu Nagarchi** ([divyanshdamami2018-web](https://github.com/divyanshdamami2018-web)) on **2026-10-24** as part of the final submission for MOSIP Decode 2026.

---

## Testing Log

**Tested by:** Divyanshu Nagarchi ([divyanshdamami2018-web](https://github.com/divyanshdamami2018-web))  
**Test Date:** 2026-10-24  
**Overall Status:** All Passed on all devices  

---

### Mobile Devices Tested

| # | Device | Model No. | OS | Processor | RAM | Storage | Display | Result |
|---|--------|-----------|----|-----------|-----|---------|---------|--------|
| 1 | Nothing CMF Phone 1 | CMF Phone 1 | Android 14 | MediaTek Dimensity 7300 5G (4nm, 8-core) | 6 GB LPDDR4X | 128 GB UFS 2.2 | 6.67 inch AMOLED 120Hz FHD+ | Passed |
| 2 | Samsung Galaxy S23 | SM-S911B | Android 13 to 14 (One UI 5.1) | Snapdragon 8 Gen 2 (4nm, Octa-core) | 8 GB LPDDR5 | 128 GB UFS 3.1 | 6.1 inch Dynamic AMOLED 2X 120Hz FHD+ | Passed |
| 3 | Redmi 12 | 23053RN02I | Android 13 (MIUI 14) | MediaTek Helio G88 (12nm, Octa-core) | 6 GB LPDDR4X | 128 GB eMMC 5.1 | 6.79 inch IPS LCD 90Hz FHD+ | Passed |
| 4 | Oppo K13 | Oppo K13 5G | Android 14 (ColorOS 14) | Snapdragon 6 Gen 1 (4nm, Octa-core) | 8 GB LPDDR4X | 128 GB UFS 2.2 | 6.67 inch AMOLED 120Hz FHD+ | Passed |
| 5 | Google Pixel 10 | GR1YH | Android 15 | Google Tensor G5 (3nm, 9-core) | 12 GB LPDDR5X | 128 GB UFS 3.1 | 6.3 inch OLED 120Hz FHD+ | Passed |
| 6 | OnePlus 12R | CPH2609 | Android 14 (OxygenOS 14) | Snapdragon 8 Gen 1 (4nm, Octa-core) | 8 GB LPDDR5 | 128 GB UFS 3.1 | 6.78 inch AMOLED 120Hz QHD+ | Passed |
| 7 | Motorola Edge 50 | XT2429-2 | Android 14 (Hello UI) | Snapdragon 7s Gen 2 (4nm, Octa-core) | 8 GB LPDDR4X | 256 GB UFS 2.2 | 6.7 inch pOLED 144Hz FHD+ | Passed |

---

### Laptop and Desktop Devices Tested

| # | Device | Model | OS | Processor | RAM | Storage | GPU | Result |
|---|--------|-------|----|-----------|-----|---------|-----|--------|
| 1 | MacBook Pro 14 inch | MacBook Pro M3 (Late 2023) | macOS Sonoma 14.5 | Apple M3 Pro - 3nm, 11-core CPU (5E+6P), up to 4.05GHz | 18 GB Unified Memory | 512 GB SSD | 14-core Apple GPU | Passed |
| 2 | MacBook Air 13 inch | MacBook Air M2 (2022) | macOS Sequoia 15.0 | Apple M2 - 5nm, 8-core CPU (4E+4P), up to 3.49GHz | 8 GB Unified Memory | 256 GB SSD | 8-core Apple GPU | Passed |
| 3 | HP Victus 15 | HP Victus 15-fb0xxx | Windows 11 Home 23H2 | AMD Ryzen 5 5600H - 7nm, 6-core 12-thread, up to 4.2GHz | 16 GB DDR4 3200MHz | 512 GB PCIe NVMe SSD | NVIDIA GTX 1650 4GB GDDR6 | Passed |
| 4 | HP Victus 16 | HP Victus 16-d1xxx | Windows 11 Home 22H2 | Intel Core i5-12500H - Intel 7 (10nm), 12-core 16-thread, up to 4.5GHz | 16 GB DDR4 3200MHz | 512 GB PCIe NVMe SSD | NVIDIA RTX 3050 4GB GDDR6 | Passed |
| 5 | Dell Inspiron 15 | Dell Inspiron 3511 | Windows 11 Pro 23H2 | Intel Core i5-1135G7 - 10nm, 4-core 8-thread, up to 4.2GHz | 8 GB DDR4 3200MHz | 256 GB PCIe SSD | Intel Iris Xe Graphics | Passed |
| 6 | Lenovo IdeaPad Slim 5 | 82LN00FBIN | Windows 11 Home 23H2 | AMD Ryzen 5 5500U - 7nm, 6-core 12-thread, up to 4.0GHz | 16 GB DDR4 3200MHz | 512 GB PCIe NVMe SSD | AMD Radeon RX Vega 7 | Passed |
| 7 | ASUS VivoBook 15 | ASUS X515EA | Windows 11 Home 23H2 | Intel Core i3-1115G4 - 10nm, 2-core 4-thread, up to 4.1GHz | 8 GB DDR4 2666MHz | 256 GB PCIe SSD | Intel UHD Graphics | Passed |

---

### What Was Tested (All Devices)

| # | Test Area | What Was Checked | Result |
|---|-----------|-----------------|--------|
| 1 | Face Liveness Detection | Passive liveness check (MiniFASNet ensemble) - real face vs spoof and photo | Passed |
| 2 | Active Liveness Challenge | Blink, smile and head-turn challenges triggered correctly | Passed |
| 3 | Presentation Attack Detection | Rejected printed photo, screen replay and mask attacks | Passed |
| 4 | Login Flow | Operator login with liveness gate, credential validation, error handling | Passed |
| 5 | Supervisor Approval | Supervisor liveness check before approval | Passed |
| 6 | Resident Face Capture | Liveness pass issued correctly before resident face capture | Passed |
| 7 | Audit Events | Events REG-LIV-001 to REG-LIV-006 logged via MOSIP audit service | Passed |
| 8 | UI Components | All screens render correctly - liveness screen, login, signup | Passed |
| 9 | RTL Layout | Right-to-Left layout (Arabic) renders correctly | Passed |
| 10 | Localization | Multi-language strings parity verified (ARB files) | Passed |
| 11 | Offline Mode | App works fully offline - no network dependency | Passed |
| 12 | Mock MDS Biometric Scan | Biometric scan simulated using Mock MDS - face, finger, iris | Passed |
| 13 | Password Visibility Toggle | Show/hide password toggle works on login screen | Passed |
| 14 | Camera Compatibility | Front camera liveness detection across varying camera quality | Passed |
| 15 | Low Light Liveness | Face detection under low-light conditions | Passed |
| 16 | Multiple Attempts | App handles failed liveness attempts and allows retry gracefully | Passed |
| 17 | Single-use Pass Expiry | Liveness pass expires and cannot be reused after timeout | Passed |
| 18 | Global Config Param | mosip.registration.face.liveness.config applied correctly | Passed |
