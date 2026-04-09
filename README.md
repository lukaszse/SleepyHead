# SleepyHead

**Sleep apnea pre-screening platform** — from raw biometric signals to actionable health insights.

SleepyHead is a multi-module project aiming to provide affordable, home-based sleep apnea screening using consumer-grade BLE sensors (Polar H10 chest strap + pulse oximeter). The long-term goal is a full SaaS platform: mobile data collection, cloud-based analysis, and a web dashboard for reviewing results over time.

> ⚠️ **SleepyHead is not a medical device.** Results are informational only and do not constitute a medical diagnosis. Sleep apnea diagnosis requires polysomnography (PSG) in a certified sleep lab.

---

## Repository Structure

| Module | Path | Status | Description |
|---|---|---|---|
| **Android App** | `AndroidApp/` | ✅ Active | Native Android client (Kotlin). Connects to Polar H10 + pulse oximeter via BLE. Real-time HRV (RMSSD), live charts, overnight recording. Hexagonal architecture (Ports & Adapters). |
| **Cloud Backend** | `backend/` | 📅 Planned | Serverless backend (AWS Lambda / Kotlin-JVM). Receives biometric sessions, runs apnea scoring algorithms, stores results. REST API. |
| **Web Dashboard** | `web/` | 📅 Planned | Web UI for reviewing multi-night reports, trends, and screening results. |
| **ML / Algorithms** | `ml/` | 📅 Planned | Signal processing and ML models: ECG-Derived Respiration (EDR), CVHR detection, Apnea Scorer. Initially developed and validated in Python/Jupyter, then ported to Kotlin for on-device or cloud execution. |

---

## Current State

### AndroidApp ✅

The Android module is the active focus. It currently provides:

- Real-time HR + RR interval streaming from Polar H10 (BLE)
- Live HRV (RMSSD) computation with 5-minute sliding window
- Canvas-based live chart in Jetpack Compose
- Session persistence in crash-proof JSONL files
- Overnight background operation (ForegroundService + WakeLock)
- Session history with expandable cards and inline charts
- 83 unit tests across all architectural layers

**Planned additions** (see [CONCEPT-001](AndroidApp/docs/concept/CONCEPT-001-Sleep-Apnea-Screening.md)):

- BLE pulse oximeter integration (SpO2, ODI)
- Raw ECG streaming (130 Hz) → R-peak detection, EDR
- Accelerometer → respiratory effort, body position
- CVHR detector + Apnea Scorer with estimated AHI

Full details: [`AndroidApp/README.md`](AndroidApp/README.md)

---

## Architecture Vision

```
Phase 1 (current):
  [Polar H10] --BLE--> [Android App] --> local JSONL files
  [Pulsoksymetr] --BLE--> [Android App]

Phase 2 (planned):
  [Android App] --HTTPS--> [Cloud Backend (AWS Lambda)]
                                 |
                           [Apnea Scoring]
                                 |
                           [Web Dashboard]

Phase 3 (future):
  [Cloud Backend] --> [Multi-night trend analysis]
                  --> [PDF report generation]
                  --> [Physician portal]
```

---

## Tech Stack Overview

| Component | Technology |
|---|---|
| Android App | Kotlin, Jetpack Compose, Coroutines + Flow, Polar BLE SDK |
| Backend (planned) | Kotlin/JVM, AWS Lambda, API Gateway |
| Web (planned) | TBD (React / Next.js / Kotlin-JS) |
| ML (planned) | Python (prototyping), Kotlin (production) |
| Architecture | Hexagonal / Ports & Adapters (all modules) |

---

## Documentation

Key documents (in `AndroidApp/docs/`):

| Document | Description |
|---|---|
| [ADR-001](AndroidApp/docs/achitecture/ADR-001-Polar-H10-Integration.md) | Why Polar H10 + Android |
| [ADR-002](AndroidApp/docs/achitecture/ADR-002-Testing-Stack.md) | Testing stack rationale |
| [TDR-001](AndroidApp/docs/design/TDR-001-HRV-Monitoring.md) | HRV monitoring design (phases A-F, complete) |
| [CONCEPT-001](AndroidApp/docs/concept/CONCEPT-001-Sleep-Apnea-Screening.md) | Sleep apnea screening concept (~1300 lines) |

---

## Scientific Background

The project is grounded in peer-reviewed research:

- **Task Force (1996)** — HRV measurement standards (ESC/NASPE)
- **Penzel et al. (2002)** — ECG-based apnea detection (87% accuracy)
- **De Chazal et al. (2003)** — Single-lead ECG OSA classification (92.3%)
- **Varon et al. (2015)** — PCA-based EDR for apnea detection
- **Gilgen-Ammann et al. (2019)** — Polar H10 validation (r = 0.99 vs ECG Holter)

---

## Getting Started

```bash
git clone https://github.com/lukaszse/SleepyHead.git
cd SleepyHead/AndroidApp
./gradlew assembleDebug
./gradlew test
```

Requires: Android Studio, JDK 11+, Android SDK 36.

---

## Author

**Lukasz Seremak** — [LinkedIn](https://www.linkedin.com/in/lukasz-seremak/) — [GitHub](https://github.com/lukaszse)
