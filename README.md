# ShimmerENACT

**Native Android application for real-time Shimmer3 wearable sensor data acquisition and recording.**

Developed by **[RFSAT Limited](https://www.rfsat.com)** as part of the **ENACT** project, funded by the European Union under the Horizon Europe programme (Grant Agreement No. 101157151).

[![Build APK](https://github.com/rfsat/ShimmerENACT/actions/workflows/build.yml/badge.svg)](https://github.com/rfsat/ShimmerENACT/actions/workflows/build.yml)
![Version](https://img.shields.io/badge/version-2.0.3-brightgreen)
![Android](https://img.shields.io/badge/Android-API%2026%2B-3DDC84?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin)
![License](https://img.shields.io/badge/License-MIT-blue)

---

## Features

| Feature | Details |
|---|---|
| **Bluetooth Classic** | Direct RFCOMM/SPP connection to Shimmer3 (no BLE, no SDK dependency) |
| **Real-time streaming** | Live multi-signal chart with up to 6 simultaneous signals, configurable |
| **Signal gauges** | Per-signal numeric readout with unit and colour coding |
| **CSV recording** | Timestamped ISO 8601 UTC CSV with metadata header, written to device storage |
| **GPS tagging** | Latitude, longitude, altitude and accuracy appended to every CSV row during recording |
| **File sharing** | Share or export CSV via Android share sheet (email, Drive, etc.) |
| **Graph viewer** | Interactive signal chart with tap/drag value tracing and statistics (min/mean/max) |
| **Data-point markers** | Small circles on every measurement point; red circle highlights the selected sample |
| **Location trace map** | OpenStreetMap panel below the graph showing the GPS track and selected-point marker |
| **Configurable BT IDs** | Per-sensor-type radio ID persisted across sessions |
| **Dark theme** | ENACT green brand palette throughout |

---

## Supported Sensors

| Sensor | Model | Default BT Radio ID | Signals |
|---|---|---|---|
| **GSR+ Unit** | SR48-5-0 | `A096` | GSR (kΩ), PPG (mV), Accel XYZ, Gyro XYZ, Mag XYZ, Temp, Battery |
| **EXG Unit** | SR47-6-0 | `A077` | ExG1 Ch1/Ch2, ExG2 Ch1/Ch2, Accel XYZ, Gyro XYZ, Battery |
| **Custom** | User-defined | Configurable | 4 generic channels + extensible |

The Bluetooth device name is matched as `Shimmer3-{RadioID}` (e.g. `Shimmer3-A096`).

---

## Requirements

- Android **8.0 (API 26)** or higher
- Device with **Bluetooth Classic** (not BLE-only)
- Shimmer3 sensor **paired** via Android Settings → Bluetooth before connecting
- Internet access required for OpenStreetMap tiles in the location trace map

---

## Getting Started

### Download pre-built APK

Download the latest APK from the [**Releases**](https://github.com/rfsat/ShimmerENACT/releases) page on GitHub.
Enable *Install from unknown sources* on your Android device and install.

### Build from source

```bash
git clone https://github.com/rfsat/ShimmerENACT.git
cd ShimmerENACT
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in **Android Studio Hedgehog (2023.1+)** and click **Run**.

---

## GitHub Actions CI / APK Build

Every push to `main` or `develop` automatically builds both a debug and release APK.

### Download artifact APK (no release tag needed)

1. Go to **Actions** → select a workflow run → scroll to **Artifacts**
2. Download `ShimmerENACT-<version>-release` (APK) or `ShimmerENACT-<version>-PlayStore` (AAB)

### Create a versioned release

```bash
git tag v2.0.3
git push origin v2.0.3
```

This triggers the workflow, builds APKs, and publishes a GitHub Release automatically.

### Versioning scheme

`<major>.<minor>.<revision>`

| Increment | When |
|---|---|
| **major** | Breaking or architectural changes |
| **minor** | Significant new features added |
| **revision** | Bug fixes and corrections only |

### Release signing (optional)

To sign the release APK, add these GitHub repository secrets (**Settings → Secrets → Actions**):

| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded `.jks` keystore file |
| `KEY_ALIAS` | Key alias inside the keystore |
| `KEY_PASSWORD` | Key password |
| `STORE_PASSWORD` | Keystore password |

Generate a keystore:
```bash
keytool -genkey -v -keystore release.jks \
  -alias shimmerenact -keyalg RSA -keysize 2048 -validity 10000
base64 -i release.jks | pbcopy   # copy to clipboard (macOS)
```

---

## Architecture

```
com.rfsat.shimmerenact
├── MainActivity.kt                 ← Compose NavHost, bottom navigation
├── ui/
│   ├── Screen.kt                   ← Route definitions
│   ├── theme/                      ← Material3 dark theme, ENACT colours
│   └── screens/
│       ├── HomeScreen.kt           ← Sensor type selection
│       ├── ConnectScreen.kt        ← BT device list, pairing hints
│       ├── DashboardScreen.kt      ← Live chart + signal gauges + recording bar
│       ├── RecordingsScreen.kt     ← CSV file list, share, delete
│       ├── RecordingViewerScreen.kt ← CSV graph viewer with value tracing + OSM map
│       ├── SettingsScreen.kt       ← Per-sensor BT radio ID configuration
│       └── AboutScreen.kt          ← Credits, RFSAT, ENACT funding
├── viewmodel/
│   └── ShimmerViewModel.kt         ← State management, recording coordination
└── data/
    ├── models/
    │   └── ShimmerModels.kt        ← Data classes, signal definitions per sensor type
    ├── bluetooth/
    │   ├── ShimmerBluetoothManager.kt ← RFCOMM connect, streaming, packet reading
    │   └── ShimmerProtocol.kt         ← Protocol constants, packet parser, calibration
    └── repository/
        ├── RecordingRepository.kt     ← CSV file writing with metadata header + GPS columns
        ├── LocationRepository.kt      ← Fused Location Provider wrapper (1 Hz, high accuracy)
        └── PreferencesRepository.kt   ← DataStore persistence of settings
```

### Bluetooth packet handling

The app implements the Shimmer3 BT firmware serial protocol directly (no Shimmer SDK dependency):

1. **Connect** via RFCOMM socket to SPP UUID `00001101-0000-1000-8000-00805F9B34FB`
2. **Inquiry** command (`0x01`) to read sensor bitmap and active channels
3. **Start streaming** command (`0x07`)
4. **Parse packets**: `[0x00][3-byte timestamp][sensor data...]`  
   Data bytes are parsed per the sensor bitmap; all values are calibrated to physical units using factory-default or device-read calibration parameters.

### CSV file format

Every recorded CSV includes GPS columns from v1.8.0 onwards:

```csv
# RFSAT Limited - ENACT Project (Horizon Europe Grant 101157151)
# Device: GSR+ Unit (SR48-5-0)
# Session start: 2026-04-12T09:30:00.000Z
# Generated by ShimmerENACT v2.0.3
timestamp_iso,timestamp_ms,gsr_kohm_kΩ,latitude_deg,longitude_deg,altitude_m,location_accuracy_m
2026-04-12T09:30:00.019Z,1744450200019,45.231,53.3498,-6.2603,12.4,3.1
```

GPS cells are left empty for rows where no fix was available.

### Graph viewer and location map

Opening a recorded CSV from the Files screen displays:

- **Signal graph** — interactive MPAndroidChart line chart with pinch-to-zoom and drag
- **Data-point circles** — a small filled circle (signal colour) at every sample position
- **Selected-point indicator** — a red filled circle (white border) replaces crosshair lines; updates continuously as the user drags a finger across the graph
- **Statistics bar** — min / mean / max / sample count above the graph
- **Value readout** — timestamp and exact signal value for the selected sample
- **Location trace map** — shown below the graph when the CSV contains GPS data; rendered using [Leaflet.js](https://leafletjs.com/) on [OpenStreetMap](https://www.openstreetmap.org/):
  - Cyan polyline connecting all measurement positions
  - Small cyan dot at every measurement position
  - Red circle panning to the GPS position of the currently selected sample

---

## EU Acknowledgement

This project has received funding from the European Union under the Horizon Europe programme.

**Project:** ENACT — Environmental monitoring and health outcomes  
**Grant:** 101157151  
**CORDIS:** [cordis.europa.eu/project/id/101157151](https://cordis.europa.eu/project/id/101157151)

*Views and opinions expressed are those of the author(s) only and do not necessarily reflect those of the European Union. The European Union cannot be held responsible for them.*

---

## Licence

MIT © 2026 RFSAT Limited
