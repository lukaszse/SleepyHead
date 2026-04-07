# SleepyHead 🛌💤

**Sleep apnea pre-screening** using Polar H10 chest strap + BLE pulse oximeter.

Android app (Kotlin) that collects biometric data in real-time via Bluetooth Low Energy
and analyses it for signs of sleep-disordered breathing.

> ⚠️ **Disclaimer:** SleepyHead is **not a medical device**. It does not diagnose
> sleep apnea. Results are informational only. Diagnosis requires polysomnography (PSG)
> conducted in a certified sleep lab under physician supervision.

---

## Features (MVP)

- 🫀 **Real-time HR + RR intervals** from Polar H10 via BLE (Polar SDK)
- 📊 **Live HRV (RMSSD)** — 5-minute sliding window, per-minute snapshots
- 📈 **Live chart** — Compose Canvas-based RMSSD chart with session average line
- 💾 **Session persistence** — crash-proof JSONL files (one line per snapshot)
- 🔋 **Background operation** — ForegroundService + WakeLock for overnight recording
- ⏱ **Auto-end** — session terminates after 2h data gap (strap removed)
- 📜 **Session history** — expandable cards with inline charts, min/max/avg stats
- ✅ **83 unit tests** — domain, application, adapter layers fully covered

## Planned Features

- 🩸 BLE pulse oximeter integration (SpO₂, ODI) — standard BLE PLX profile (`0x1822`)
- 🔬 Raw ECG streaming (130 Hz) — R-peak detection, ECG-Derived Respiration (EDR)
- 📡 Accelerometer streaming (200 Hz) — respiratory effort, body position
- 💓 CVHR detector — cyclic variation of heart rate as apnea marker
- 🧠 Apnea Scorer — multi-channel fusion → estimated AHI + night report

---

## Architecture

**Hexagonal / Ports & Adapters** — domain logic is 100% pure Kotlin with zero Android dependencies.

```
com.example.androidapp/
│
├── domain/                          # Pure Kotlin — no Android imports
│   ├── model/
│   │   ├── HrData.kt               # HR + RR intervals value object
│   │   ├── HrvSnapshot.kt          # RMSSD snapshot value object
│   │   ├── HrvSession.kt           # Session aggregate (with averageRmssd)
│   │   └── FoundDevice.kt          # Discovered BLE device
│   └── service/
│       └── HrvCalculator.kt        # RMSSD computation (Task Force 1996)
│
├── application/                     # Use cases + port interfaces
│   ├── port/
│   │   ├── input/                   # Driving ports (UI → app)
│   │   │   ├── ConnectDeviceInputPort.kt
│   │   │   ├── ScanForDevicesInputPort.kt
│   │   │   ├── GetHeartRateStreamInputPort.kt
│   │   │   ├── StartHrvSessionInputPort.kt
│   │   │   ├── StopHrvSessionInputPort.kt
│   │   │   ├── RecordHrvSnapshotInputPort.kt
│   │   │   └── GetSessionHistoryInputPort.kt
│   │   └── output/                  # Driven ports (app → infra)
│   │       ├── HeartRateMonitorPort.kt
│   │       ├── HrvSessionRepositoryPort.kt
│   │       └── MonitoringServicePort.kt
│   └── usecase/
│       ├── ConnectDeviceUseCase.kt
│       ├── ScanForDevicesUseCase.kt
│       ├── GetHeartRateStreamUseCase.kt
│       ├── StartHrvSessionUseCase.kt
│       ├── StopHrvSessionUseCase.kt
│       ├── RecordHrvSnapshotUseCase.kt
│       └── GetSessionHistoryUseCase.kt
│
└── framework/                       # Android-specific (adapters)
    ├── adapter/
    │   ├── input/
    │   │   └── HrViewModel.kt      # Driving adapter (UI ViewModel)
    │   └── output/
    │       ├── polar/
    │       │   └── PolarBleAdapter.kt   # Polar BLE SDK → HeartRateMonitorPort
    │       ├── file/
    │       │   └── HrvSessionFileAdapter.kt  # JSONL → HrvSessionRepositoryPort
    │       └── service/
    │           └── HrvServiceController.kt   # ForegroundService → MonitoringServicePort
    ├── bootstrap/
    │   ├── AppDependencies.kt       # Manual DI wiring
    │   └── SleepyHeadApplication.kt # Application subclass
    └── infra/
        ├── MainActivity.kt
        └── ui/
            ├── AppNavigation.kt
            ├── HrScreen.kt          # HorizontalPager (HR + HRV pages)
            ├── LiveHrPage.kt        # BPM + RR intervals
            ├── LiveHrvPage.kt       # Live RMSSD chart
            └── HrvHistoryScreen.kt  # Past sessions with charts
```

### Dependency Direction

```
UI (Framework) → Application (Use Cases) → Domain (Models + Services)
                         ↓
              Output Ports (interfaces)
                         ↓
          Framework Adapters (implementations)
```

Domain knows nothing about Android, BLE, files, or UI.
Swapping Polar H10 for another sensor = new adapter, zero domain changes.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Async | Coroutines + Flow |
| BLE | Polar BLE SDK 5.5.0 + RxJava ↔ Flow bridge |
| Persistence | JSONL files (kotlinx-serialization) |
| Background | ForegroundService + PARTIAL_WAKE_LOCK |
| Navigation | Jetpack Navigation Compose |
| Tests | JUnit 4 + MockK + Turbine |
| Min SDK | 24 (Android 7.0) |

---

## Tests

83 unit tests across all layers:

| Layer | Test file | Tests |
|---|---|---|
| Domain | `HrvCalculatorTest` | 12 |
| Domain | `HrvSessionTest` | 5 |
| Application | `StartHrvSessionInputPortTest` | 6 |
| Application | `StopHrvSessionInputPortTest` | 5 |
| Application | `RecordHrvSnapshotInputPortTest` | 3 |
| Application | `GetSessionHistoryInputPortTest` | 4 |
| Application | `ConnectDeviceInputPortTest` | — |
| Application | `GetHeartRateStreamInputPortTest` | — |
| Framework | `HrvSessionFileAdapterTest` | 8 |
| Framework | `HrViewModelTest` | 24+ |

---

## Hardware

| Device | Role | Price |
|---|---|---|
| **Polar H10** | HR, RR intervals, ECG 130 Hz, ACC 200 Hz | ~€80 |
| **BLE Pulse Oximeter** (planned) | SpO₂, Pulse Rate | ~€20 |

---

## Scientific References

This project is grounded in peer-reviewed research:

1. **Task Force (1996)** — HRV measurement standards (ESC/NASPE)
2. **Penzel et al. (2002)** — ECG-based apnea detection algorithms (87% accuracy)
3. **De Chazal et al. (2003)** — Single-lead ECG classification of OSA (92.3% accuracy)
4. **Varon et al. (2015)** — PCA-based EDR + LS-SVM for apnea detection
5. **Gilgen-Ammann et al. (2019)** — Polar H10 RR validation vs. ECG Holter (r = 0.99)

---

## Documentation

| Document | Description |
|---|---|
| [`ADR-001`](docs/achitecture/ADR-001-Polar-H10-Integration.md) | Architecture decision: Polar H10 + Android |
| [`ADR-002`](docs/achitecture/ADR-002-Testing-Stack.md) | Testing stack decision |
| [`TDR-001`](docs/design/TDR-001-HRV-Monitoring.md) | HRV monitoring technical design (phases A–F) |
| [`CONCEPT-001`](docs/concept/CONCEPT-001-Sleep-Apnea-Screening.md) | Sleep apnea screening concept (~1300 lines) |

---

## Building

```bash
git clone https://github.com/lukaszse/SleepyHead.git
cd SleepyHead
./gradlew assembleDebug
./gradlew test          # run all 83 unit tests
```

Requires: Android Studio, JDK 11+, Android SDK 36.

---

## License

TBD

---

## Author

**Łukasz Seremak** — [LinkedIn](https://www.linkedin.com/in/lukasz-seremak/) · [GitHub](https://github.com/lukaszse)

