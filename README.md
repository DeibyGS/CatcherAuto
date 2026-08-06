<div align="center">

# CatcherAuto

**Automated delivery order acceptance on Android — pixel scanning and ML Kit OCR through the Accessibility Service.**

Reads GO! buttons, order details, and white/blacklist rules in real time, then taps accept automatically. Built with Kotlin and Material 3, it filters valid orders by city, distance, and restaurant so only matching deliveries pass through.

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)](.)
[![Android](https://img.shields.io/badge/Android-7.0%2B-34A853?logo=android&logoColor=white)](.)
[![ML Kit](https://img.shields.io/badge/ML_Kit-OCR-4285F4?logo=google&logoColor=white)](.)

[How It Works](#how-it-works) • [Tech Stack](#tech-stack) • [Features](#features) • [How to Run](#how-to-run) • [Built with AI](#built-with-ai)

</div>

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

## Built with AI

CatcherAuto was engineered by a human developer working with AI as a **pair programming partner**. AI accelerated implementation — the Android architecture, Accessibility Service design, and engineering decisions stayed human.

### How we worked together

| Human-owned | AI implemented, always human-reviewed |
|-------------|-------------------------------------|
| Automation flow design | Kotlin code generation |
| Pixel detection & OCR logic | Gradle setup, scaffolding |
| Order filtering rules | Refactoring, type improvements |
| Code review & final acceptance | Auxiliary testing, docs |

**Workflow:** `Idea → Spec → AI implementation → Human review → Test → Refine → Merge`

### AI Development Principles

- AI never made product decisions.
- Every implementation started from a written specification.
- Documentation was treated as executable context for AI.
- All generated code required human review.
- Architecture was preserved over implementation speed.

<details>
<summary><strong>Supporting metrics</strong></summary>
<br>

| Metric | Value |
|--------|-------|
| AI sessions | 1 logged |
| Avg session efficiency | 95/100 |
| Primary model | Claude (Claude Code) |

_Measured with [ClaudeStat](https://github.com/DeibyGS/claudestat). Approximate values; most work pre-dates exhaustive session logging, so metrics reflect 1 logged session only._

</details>

---

## Author

**[Deiby Gorrin](https://deiby.dev)** — Full Stack Developer

- Portfolio: [deiby.dev](https://deiby.dev)
- LinkedIn: [in/deibygorrin](https://www.linkedin.com/in/deibygorrin)
- GitHub: [@DeibyGS](https://github.com/DeibyGS)