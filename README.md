# CatcherAuto

[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white)](.)
[![Android](https://img.shields.io/badge/Android-34A853?style=flat&logo=android&logoColor=white)](.)
[![ML Kit](https://img.shields.io/badge/ML_Kit-4285F4?style=flat&logo=google&logoColor=white)](.)

> Android automation app using Google ML Kit to automatically accept delivery orders. Uses real-time OCR and screen analysis via Accessibility Service.

## How It Works

1. **Screen capture** every 300ms while scanning is active
2. **Pixel detection** to find GO! buttons in the delivery app
3. **OCR with ML Kit** to read city, distance, and restaurant name
4. **Filtering**: only accepts valid orders (correct city, distance ≤ 3km, restaurant in whitelist)
5. **Auto-accept** with post-acceptance verification (confirmed, failed, or retry)

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin 1.9 |
| Build | Gradle KTS, AGP 8.2 |
| UI | Material Design 3, dark theme with cyan accents |
| OCR | Google ML Kit Text Recognition 16.0 |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 34 (Android 14) |

## Features

- ON/OFF toggle to enable/disable automatic scanning
- 8 individually configurable restaurants with Material switches
- Multi-button detection (multiple orders on screen simultaneously)
- Visual status indicator: INACTIVE / ACTIVE / PAUSED
- Animated pulse ring during active scanning
- Post-acceptance verification: confirmation, failure, or blank screen detection
- Vibration on order acceptance
- Direct shortcut to system accessibility settings

## How to Run

1. Open the project in **Android Studio**
2. Sync Gradle and run on a physical device (API 30+ required for screen capture)
3. Enable **CatcherAuto** as an Accessibility Service in system Settings
4. Configure desired restaurants and activate scanning

## Author

**[Deiby Gorrin](https://deiby.dev)** — Full Stack Developer

- Portfolio: [deiby.dev](https://deiby.dev)
- LinkedIn: [in/deibygorrin](https://www.linkedin.com/in/deibygorrin)
- GitHub: [@DeibyGS](https://github.com/DeibyGS)
