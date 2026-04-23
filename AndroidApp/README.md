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
│   │   │   ├── ScanForDevicesInputPort.kt
│   │   │   ├── ConnectDeviceInputPort.kt
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
│       ├── ScanForDevicesUseCase.kt
│       ├── ConnectDeviceUseCase.kt
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

## Use Cases

The application layer defines 7 use cases, each represented by an **interface** in `application/usecase/` and implemented by a concrete **input port** in `application/port/input/`. Every input port depends solely on output port interfaces — not on framework adapters.

### 1. ScanForDevicesUseCase

| Aspect | Description |
|---|---|
| **Interface** | `ScanForDevicesUseCase` |
| **Input port** | `ScanForDevicesInputPort` |
| **Output port** | `HeartRateMonitorPort.scanForDevices()` |
| **Trigger** | User opens the device scan screen (UI calls `HrViewModel.startScan()`) |
| **Precondition** | Bluetooth is enabled on the device |
| **Postcondition** | A `Flow<FoundDevice>` is returned, emitting discovered Polar H10 devices |
| **Flow** | Delegates directly to `HeartRateMonitorPort.scanForDevices()`. Each discovered BLE device with a heart-rate service is emitted as a `FoundDevice` domain object. Scanning continues until the calling scope cancels the Flow collection. |

### 2. ConnectDeviceUseCase

| Aspect | Description |
|---|---|
| **Interface** | `ConnectDeviceUseCase` |
| **Input port** | `ConnectDeviceInputPort` |
| **Output port** | `HeartRateMonitorPort.connect()` / `.disconnect()` |
| **Trigger** | User taps a discovered device; UI calls `HrViewModel.connectToDevice()` |
| **Precondition** | The target device was discovered via `ScanForDevicesUseCase` |
| **Postcondition** | Device is connected (`connect`) or disconnected (`disconnect`). On successful connection, the ViewModel starts the foreground service via `MonitoringServicePort.startForegroundMonitoring()`. |
| **Flow (connect)** | `connect(deviceId)` suspends while the BLE stack establishes GATT connection. Throws on timeout or failure. |
| **Flow (disconnect)** | `disconnect(deviceId)` clears the BLE connection synchronously. The foreground service is stopped via `MonitoringServicePort.stopForegroundMonitoring()`. |

### 3. GetHeartRateStreamUseCase

| Aspect | Description |
|---|---|
| **Interface** | `GetHeartRateStreamUseCase` |
| **Input port** | `GetHeartRateStreamInputPort` |
| **Output port** | `HeartRateMonitorPort.getHeartRateStream()` |
| **Trigger** | After BLE connection, the ViewMdel observes `heartRateStream` and the UI renders `LiveHrPage` |
| **Precondition** | Device is connected via `ConnectDeviceUseCase` |
| **Postcondition** | A `Flow<HrData>` delivers HR (beats per minute) and RR interval arrays in real time |
| **Flow** | Delegates to `HeartRateMonitorPort.getHeartRateStream(deviceId)`. Each `HrData` emitted by the sensor (via Polar SDK's `HrData`) is forwarded as a domain model object. The ViewModel accumulates RR intervals in a 5-minute sliding window for HRV calculation. |

### 4. StartHrvSessionUseCase

| Aspect | Description |
|---|---|
| **Interface** | `StartHrvSessionUseCase` |
| **Input port** | `StartHrvSessionInputPort` |
| **Output port** | `HrvSessionRepositoryPort.createSession()` |
| **Trigger** | First RR data arrives after BLE connection; the ViewModel calls `startSession()` |
| **Precondition** | Device is connected and heart-rate stream is active |
| **Postcondition** | A new `HrvSession` is created with a generated UUID and current timestamp, persisted as a JSONL header line |
| **Flow** | 1. Generates `sessionId = UUID.randomUUID().toString()` 2. Sets `startTime = System.currentTimeMillis()` 3. Creates `HrvSession(id, startTime, endTime = null)` 4. Persists via `hrvSessionRepositoryPort.createSession(session)` 5. Returns the new session to the ViewModel |

### 5. RecordHrvSnapshotUseCase

| Aspect | Description |
|---|---|
| **Interface** | `RecordHrvSnapshotUseCase` |
| **Input port** | `RecordHrvSnapshotInputPort` |
| **Output port** | `HrvSessionRepositoryPort.appendSnapshot()` |
| **Trigger** | Every minute, the ViewModel calculates RMSSD from the sliding window of RR intervals and calls `recordSnapshot()` |
| **Precondition** | An active HRV session exists (created by `StartHrvSessionUseCase`) |
| **Postcondition** | A new `HrvSnapshot` (RMSSD value + timestamp) is appended to the session's JSONL file |
| **Flow** | 1. `HrvCalculator` computes RMSSD from the RR-interval buffer 2. `HrvSnapshot` is created with the computed RMSSD and current timestamp 3. `hrvSessionRepositoryPort.appendSnapshot(sessionId, snapshot)` persists the snapshot as a new JSONL line |

### 6. StopHrvSessionUseCase

| Aspect | Description |
|---|---|
| **Interface** | `StopHrvSessionUseCase` |
| **Input port** | `StopHrvSessionInputPort` |
| **Output port** | `HrvSessionRepositoryPort.findById()` / `.finaliseSession()` |
| **Trigger** | User stops monitoring, auto-end fires (2h data gap), or app goes to background |
| **Precondition** | An active HRV session exists |
| **Postcondition** | The session's `endTime` is set, the JSONL header is updated, and the session is considered finalised |
| **Flow** | 1. Loads the session from repository via `findById(sessionId)` 2. Creates a copy with `endTime = System.currentTimeMillis()` 3. Persists the finalised session via `finaliseSession(finalised)` 4. Returns the finalised session. The ViewModel then resets session state and stops the foreground service. |

### 7. GetSessionHistoryUseCase

| Aspect | Description |
|---|---|
| **Interface** | `GetSessionHistoryUseCase` |
| **Input port** | `GetSessionHistoryInputPort` |
| **Output port** | `HrvSessionRepositoryPort.loadAll()` |
| **Trigger** | User navigates to the history screen (`HrvHistoryScreen`) |
| **Precondition** | At least one session has been finalised (or exists in storage) |
| **Postcondition** | A list of all `HrvSession` objects (with their snapshots) is returned, sorted by start time descending |
| **Flow** | 1. ViewModel calls `getSessionHistory()` 2. Delegates to `hrvSessionRepositoryPort.loadAll()` 3. The file adapter reads all JSONL files from `filesDir/hrv_sessions/`, parses each into a `HrvSession` with its snapshots 4. Results are displayed as expandable cards with inline RMSSD charts and min/max/avg statistics |

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
