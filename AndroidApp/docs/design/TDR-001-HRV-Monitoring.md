# TDR-001: HRV Monitoring — Local Session Recording with Live RMSSD

**Status:** Proposed
**Date:** 2026-04-05
**Author:** Lukasz Seremak
**Relates to:** ADR-001 (Polar H10 Integration), `android-implementation-plan.md` Faza 1b

---

## 1. Problem Statement

The SleepyHead application collects RR intervals from the Polar H10 in real time.
The goal of this feature is to:

1. Compute **live HRV (RMSSD)** from incoming RR intervals.
2. **Persist per-minute snapshots** to local storage for offline charting.
3. Manage the recording as a **session** — with manual start/stop and automatic
   termination after a ≥ 2-hour measurement gap.
4. Display a **live chart** of per-minute RMSSD values with a running session average.

---

## 2. Clinical & Scientific Foundations

### 2.1 What is HRV?

Heart Rate Variability (HRV) is the beat-to-beat variation in time between
consecutive R-waves of the cardiac cycle. It is measured from **RR intervals**
(also called NN intervals in HRV literature when ectopic beats are excluded).

> *"Heart rate variability represents one of the most promising quantitative markers
> of autonomic nervous system activity."*
> — Task Force of the European Society of Cardiology (ESC) & the North American
> Society of Pacing and Electrophysiology (NASPE), 1996.

### 2.2 Authoritative Reference — Task Force 1996

**Citation:**  
Task Force of the European Society of Cardiology and the North American Society
of Pacing and Electrophysiology. (1996).  
**"Heart rate variability: standards of measurement, physiological interpretation,
and clinical use."**  
*European Heart Journal, 17*(3), 354–381.
DOI: [10.1093/oxfordjournals.eurheartj.a014868](https://doi.org/10.1093/oxfordjournals.eurheartj.a014868)

This document is **the** clinical gold standard for HRV methodology. It defines:
- All time-domain metrics (SDNN, RMSSD, pNN50, SDANN).
- Minimum recording duration for each metric.
- Physiological interpretation.

### 2.3 RMSSD — Chosen Metric

**RMSSD** = **Root Mean Square of Successive Differences**
(pol. *pierwiastek kwadratowy ze średniej kwadratów kolejnych różnic interwałów RR*)

**Formula:**

```
dRR[i]  = RR[i+1] − RR[i]                     // successive differences (ms)
RMSSD   = √( Σ(dRR[i]²) / (N−1) )             // root mean square of those differences
```

**Why RMSSD?**

| Property | RMSSD | SDNN |
|---|---|---|
| Reflects | Parasympathetic (vagal) tone | Overall ANS variability |
| Minimum recording | **5 min** (validated from ~20 intervals) | **5 min** (clinical); **24 h** (full standard) |
| Used by wearables | Apple Watch, Garmin, Oura, Whoop, Polar Flow | Garmin (long-term) |
| Robust to | Respiration artefacts, outliers | — |
| Suitability for live | ✅ Best choice | ❌ Less stable short-term |

RMSSD computed on a **5-minute sliding window** is recommended by the Task Force
for short-term HRV assessment and is the most widely validated metric for consumer
wearables.

### 2.4 Polar H10 — Validated Research-Grade Sensor

The Polar H10 has been independently validated against ECG in multiple peer-reviewed studies:

- **Gilgen-Ammann et al. (2019):** *"RR interval signal quality of a heart rate
  monitor and an ECG Holter at rest and during exercise."*
  *European Journal of Applied Physiology, 119*(9), 1991–1999.
  → Demonstrated near-perfect agreement between Polar H10 RR intervals and ECG.

- **Hautala et al. (2003):** *"Accurate detection of coronary artery disease by
  integrated analysis of inter-beat intervals..."*
  → Used Polar monitors as validated RR recorders.

The Polar H10 is widely accepted in academic research as a **medical-grade**
RR-interval recorder, making it suitable for clinical-quality HRV analysis.

### 2.5 Significance of Nocturnal HRV

Recording HRV during sleep removes the major confounders present in daytime
measurements (physical activity, posture, emotional stress, food intake). Key findings:

- **Flatt & Esco (2016):** *"Agreement between a smartphone application and a
  criterion device to measure RR intervals at rest."*
  *Journal of Sports Sciences, 34*(14), 1324–1328.
  → Validated smartphone-based RR collection for HRV analysis.

- **Plews et al. (2014):** *"Heart rate variability and training intensity
  distribution in elite rowers."*
  *International Journal of Sports Physiology and Performance.*
  → Showed nocturnal RMSSD correlates reliably with next-day training readiness.

---

## 3. Design Decisions

### 3.1 Sliding Window: 5 minutes

Raw RR intervals are accumulated in a **rolling buffer of the last 5 minutes**.
RMSSD is computed from this buffer and recorded as a snapshot **once per minute**.

```
t = 0:00  → buffer: empty               → no RMSSD yet
t = 0:01  → buffer: last 60 s            → no RMSSD yet
t = 5:00  → buffer: last 300 s (~300 RR) → first RMSSD snapshot emitted
t = 5:01  → buffer: [0:01–5:01]          → second snapshot, first minute dropped
```

| Parameter | Value | Rationale |
|---|---|---|
| Sliding window | 5 min (300 s) | Task Force 1996 minimum for RMSSD |
| Snapshot frequency | 1 per minute | Balance between granularity and noise |
| Minimum intervals for emit | 20 | Avoids meaningless values at start |

### 3.2 Session Lifecycle

```
        ┌─────────────────────────────────────────────────────┐
        │                     SESSION                         │
        │                                                     │
  BLE   │  deviceConnected()                  deviceDisconnected() │  BLE
  ──────►──────────────────────────────────────────────────────►──────
        │                           ↑ reconnect retried         │
        │   Data gap ≥ 2 h  →  auto-end                       │
        │   ─────────────────────────────►  stopSession()     │
        └─────────────────────────────────────────────────────┘
```

**Rules:**
- Session starts **automatically** when BLE device connects — no extra "Start" button needed.
  The existing `connect()` flow in `HrViewModel` triggers `startSession()` internally.
- Session ends when the user taps **"Disconnect"** — `disconnect()` triggers `stopSession()`.
- Session **auto-ends** if no RR data is received for ≥ **2 hours** (7,200,000 ms).
- BLE drops are treated as transient gaps — `reconnect()` is retried every 5 s.
  The session is NOT ended on a transient disconnect.
- After auto-end, a new session starts automatically on the next successful reconnect.
- Session ID is a UUID generated at the moment of each new session start.

> **UX rationale:** The user already interacts with Connect/Disconnect.
> Adding a separate "Start/Stop HRV session" button would be redundant and confusing.

---

## 4. Data Model

### 4.1 Domain Objects

```kotlin
/** Immutable snapshot of RMSSD computed at a given point in time. */
data class HrvSnapshot(
    val timestamp: Long,          // epoch milliseconds
    val rmssdMs: Double,          // RMSSD value in milliseconds
    val rrIntervalCount: Int,     // number of RR intervals used in this window
    val windowDurationMs: Long    // actual duration of the window used
)

/** Aggregate representing one recording session. */
data class HrvSession(
    val id: String,               // UUID
    val startTime: Long,          // epoch milliseconds
    val endTime: Long?,           // null while session is active
    val snapshots: List<HrvSnapshot>
) {
    val averageRmssd: Double?
        get() = if (snapshots.isEmpty()) null
                else snapshots.map { it.rmssdMs }.average()
}
```

### 4.2 Domain Service — HrvCalculator

```kotlin
object HrvCalculator {

    /** 5-minute sliding window as recommended by Task Force (1996). */
    const val WINDOW_MS = 5 * 60 * 1000L

    /** Minimum RR intervals required before emitting a value. */
    const val MIN_INTERVALS = 20

    /**
     * Computes RMSSD from a list of RR intervals (milliseconds).
     * Returns null if fewer than [MIN_INTERVALS] values are provided.
     */
    fun rmssd(rrIntervals: List<Int>): Double? {
        if (rrIntervals.size < MIN_INTERVALS) return null
        val differences = rrIntervals.zipWithNext { a, b -> (b - a).toDouble() }
        return sqrt(differences.map { it * it }.average())
    }
}
```

---

## 5. Architecture — Hexagonal Decomposition

Following the Ports & Adapters architecture established in ADR-001.

```
┌──────────────────────────────────────────────────────────────────────┐
│  FRAMEWORK (Infrastructure)                                          │
│                                                                      │
│  HrvMonitorScreen ◄── HrvViewModel ──► StartHrvSessionInputPort      │
│  HrvHistoryScreen ◄── HrvViewModel ──► StopHrvSessionInputPort       │
│                                    ──► GetSessionHistoryInputPort    │
└────────────────────────────────────────────────────────────────────┬─┘
                                                                     │
┌────────────────────────────────────────────────────────────────────▼─┐
│  APPLICATION (Use Cases + Ports)                                     │
│                                                                      │
│  StartHrvSessionUseCase                                              │
│  StopHrvSessionUseCase                                               │
│  RecordHrvSnapshotUseCase   ◄──── HrvSessionRepositoryPort (output) │
│  GetSessionHistoryUseCase                                            │
└────────────────────────────────────────────────────────────────────┬─┘
                                                                     │
┌────────────────────────────────────────────────────────────────────▼─┐
│  DOMAIN                                                              │
│                                                                      │
│  HrvSession  ·  HrvSnapshot  ·  HrvCalculator                       │
└──────────────────────────────────────────────────────────────────────┘
                                                                     │
┌────────────────────────────────────────────────────────────────────▼─┐
│  FRAMEWORK (Driven Adapters)                                         │
│                                                                      │
│  HrvSessionFileAdapter  ── implements ──►  HrvSessionRepositoryPort  │
│  (JSONL files, one per session)                                      │
└──────────────────────────────────────────────────────────────────────┘
```

> **Dependency direction:** The adapter depends on the domain — never the reverse.
> `HrvSessionFileAdapter` knows `HrvSession` (domain object). `HrvSession` knows
> nothing about files, Android, or any framework class.
>
> **Swap note:** Replacing `HrvSessionFileAdapter` with a Room- or cloud-backed
> adapter requires **zero changes** in the domain or application layers.

### 5.1 New Files Overview

| Layer | File | Responsibility |
|---|---|---|
| Domain | `HrvSnapshot.kt` | Value object |
| Domain | `HrvSession.kt` | Aggregate |
| Domain | `HrvCalculator.kt` | RMSSD computation |
| App / Input Port | `StartHrvSessionInputPort.kt` | Interface |
| App / Input Port | `StopHrvSessionInputPort.kt` | Interface |
| App / Input Port | `GetSessionHistoryInputPort.kt` | Interface |
| App / **Output Port** | `HrvSessionRepositoryPort.kt` | **Persistence contract — domain objects only** |
| App / Use Case | `StartHrvSessionUseCase.kt` | Impl of input port |
| App / Use Case | `StopHrvSessionUseCase.kt` | Impl of input port |
| App / Use Case | `RecordHrvSnapshotUseCase.kt` | Orchestrates per-minute save |
| App / Use Case | `GetSessionHistoryUseCase.kt` | Impl of input port |
| Framework Input | `HrvViewModel.kt` | Drives all HRV use cases, session state, live chart data |
| **Framework Output** | **`HrvSessionFileAdapter.kt`** | **JSONL file implementation of the output port** |
| Framework UI | `HrvMonitorScreen.kt` | Live RMSSD + chart + session avg |
| Framework UI | `HrvHistoryScreen.kt` | Past sessions list + detail |

---

## 6. HrvViewModel — Integration with Existing HrViewModel

Rather than a standalone `HrvViewModel`, HRV state is **added to the existing `HrViewModel`**.
This avoids duplicating BLE state, coroutine scopes, and device lifecycle management.

```kotlin
class HrViewModel(
    private val connectUseCase: ConnectDeviceUseCase,
    private val streamUseCase: GetHeartRateStreamUseCase,
    private val scanUseCase: ScanForDevicesUseCase,
    private val startSessionUseCase: StartHrvSessionUseCase,   // NEW
    private val recordSnapshotUseCase: RecordHrvSnapshotUseCase, // NEW
    private val stopSessionUseCase: StopHrvSessionUseCase        // NEW
) : ViewModel() {

    // --- Existing state (unchanged) ---
    val hrData: StateFlow<HrData?>
    val isConnected: StateFlow<Boolean>
    val error: StateFlow<String?>
    val foundDevices: StateFlow<List<FoundDevice>>
    val isScanning: StateFlow<Boolean>

    // --- NEW: HRV state ---
    val currentRmssd: StateFlow<Double?>          // latest RMSSD from 5-min window
    val sessionAverage: StateFlow<Double?>        // running mean of all snapshots
    val sessionSnapshots: StateFlow<List<HrvSnapshot>>  // data for chart

    // --- Internal ---
    // On connect:  startSession() → rrBuffer cleared → snapshotTimer started
    // On HrData:   rrBuffer.addAll(data.rrIntervals) → trim to 5 min window
    //              every 60 s: compute RMSSD → recordSnapshot() → update StateFlows
    // On gap > 2h: stopSession() → auto-end
    // On disconnect (transient): retry loop, session continues
    // On disconnect (user): stopSession() → finaliseSession()
}
```

### 6.1 RR Buffer — Sliding Window

```kotlin
// Timestamped RR buffer — each entry is (receivedAt: Long, rrMs: Int)
private val rrBuffer = ArrayDeque<Pair<Long, Int>>()

private fun addRrIntervals(hrData: HrData) {
    val now = System.currentTimeMillis()
    hrData.rrIntervals.forEach { rrBuffer.addLast(now to it) }
    // Evict entries older than 5 minutes
    val cutoff = now - HrvCalculator.WINDOW_MS
    while (rrBuffer.isNotEmpty() && rrBuffer.first().first < cutoff) {
        rrBuffer.removeFirst()
    }
}
```

### 6.2 Per-Minute Snapshot Timer

```kotlin
private fun startSnapshotTimer() = viewModelScope.launch {
    while (isConnected.value) {
        delay(60_000L)
        val rmssd = HrvCalculator.rmssd(rrBuffer.map { it.second })
        if (rmssd != null) {
            val snapshot = HrvSnapshot(
                timestamp = System.currentTimeMillis(),
                rmssdMs = rmssd,
                rrIntervalCount = rrBuffer.size,
                windowDurationMs = HrvCalculator.WINDOW_MS
            )
            recordSnapshotUseCase(currentSessionId, snapshot)
            _sessionSnapshots.value = _sessionSnapshots.value + snapshot
            _sessionAverage.value = _sessionSnapshots.value.map { it.rmssdMs }.average()
            _currentRmssd.value = rmssd
        }
    }
}
```

### 6.3 Auto-end Watcher

```kotlin
private fun watchForAutoEnd() = viewModelScope.launch {
    while (isConnected.value) {
        delay(60_000L)
        val gap = System.currentTimeMillis() - lastReceivedTimestamp
        if (gap >= 2 * 60 * 60 * 1000L) {
            stopMonitoring(currentDeviceId)   // triggers stopSession() internally
        }
    }
}
```

### 6.4 BLE Reconnect Loop

```kotlin
private fun startStreamWithRetry(deviceId: String) = viewModelScope.launch {
    while (_isConnected.value) {
        try {
            streamUseCase(deviceId).collect { hrData ->
                lastReceivedTimestamp = System.currentTimeMillis()
                _hrData.value = hrData
                addRrIntervals(hrData)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stream lost: ${e.message}. Retrying in 5 s...")
            delay(5_000L)
            try { connectUseCase.connect(deviceId) } catch (_: Exception) {}
        }
    }
}
```

---

## 7. Persistence — JSONL File Storage

### 7.1 Decision: Files over Room

Room (SQLite) was considered and rejected for this feature.
See rationale below.

| Criterion | JSONL Files ✅ | Room / SQLite ❌ |
|---|---|---|
| Implementation cost | `HrvSessionFileAdapter` (~60 lines) | Adapter + Entity ×2 + DAO + DB migration (5+ files) |
| Dependencies | None (stdlib only) | `room-runtime`, `room-compiler` (kapt/ksp) |
| Schema migrations | Not needed | Required on every model change |
| Complex queries | Not needed at this scale | Would justify Room |
| Export / share | Trivial — file is already JSON | Requires conversion |
| Raw data readability | ✅ Open in any text editor | ❌ Binary SQLite |
| Swap to Room later | ✅ Zero domain/app changes (port contract) | — |

> YAGNI: Room will be reconsidered only if cross-session SQL queries become necessary
> (e.g. "all sessions where avg RMSSD > 40 in the last 30 days").

### 7.2 File Format — JSONL (JSON Lines)

One file per session. Each line is one complete `HrvSnapshot` JSON object.
The first line is the session header.

**File naming:**
```
hrv_2026-04-05T23-14-00.jsonl
```

**File content:**
```jsonl
{"type":"header","id":"uuid-...","startTime":1712358840000,"endTime":null}
{"type":"snapshot","timestamp":1712359140000,"rmssdMs":42.3,"rrIntervalCount":312,"windowDurationMs":300000}
{"type":"snapshot","timestamp":1712359200000,"rmssdMs":44.1,"rrIntervalCount":308,"windowDurationMs":300000}
{"type":"snapshot","timestamp":1712359260000,"rmssdMs":38.7,"rrIntervalCount":301,"windowDurationMs":300000}
```

**Why JSONL (not a single JSON array)?**
- Each snapshot is appended as a single `FileWriter(append = true)` call.
- If the app crashes mid-session, all previously written lines are intact.
- No need to read and rewrite the entire file on each append.

### 7.3 Adapter Skeleton

```kotlin
class HrvSessionFileAdapter(
    private val context: Context              // infrastructure dependency
) : HrvSessionRepositoryPort {               // depends on port (application layer)

    private val dir: File
        get() = context.filesDir.resolve("hrv_sessions").also { it.mkdirs() }

    /** Appends one snapshot line to the session file. */
    override suspend fun appendSnapshot(sessionId: String, snapshot: HrvSnapshot) {
        withContext(Dispatchers.IO) {
            fileFor(sessionId).appendText(
                Json.encodeToString(snapshot.toJsonLine()) + "\n"
            )
        }
    }

    /** Reads and deserialises all sessions (one file each). */
    override suspend fun loadAll(): List<HrvSession> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.extension == "jsonl" }
            ?.map { parseFile(it) }
            ?.sortedByDescending { it.startTime }
            ?: emptyList()
    }

    /** Writes or updates the header line (e.g. to set endTime). */
    override suspend fun finaliseSession(session: HrvSession) { /* rewrite header */ }

    private fun fileFor(sessionId: String) =
        dir.listFiles { f -> f.name.contains(sessionId) }?.firstOrNull()
            ?: dir.resolve("hrv_${sessionId}.jsonl")
}
```

### 7.4 Storage Estimates

**Assumptions:**
- Average sleep session: **8 hours** → 480 snapshots (1/minute).
- JSONL line per snapshot: ~90 bytes (JSON text).
- Header line: ~80 bytes.

| Period | Sessions | Snapshots | Storage |
|---|---|---|---|
| 1 night (8 h) | 1 | 480 | **~43 KB** |
| 1 week | 7 | 3,360 | **~301 KB** |
| 1 month (30 d) | 30 | 14,400 | **~1.3 MB** |
| 1 year | 365 | 175,200 | **~15.5 MB** |

> **Context:** A single JPEG photo from a modern Android phone is 3–5 MB.
> One year of HRV data equals the storage of roughly **3–4 photos**.
> No cleanup strategy is required at this scale.

---

## 8. UI — Layout & Navigation

### 8.1 Screen Structure

The app has **one entry point** — `HrScreen`. No separate HRV screen navigation is needed.

```
HrScreen
   │
   ├── NOT connected  →  ScanScreen (existing, unchanged)
   │
   └── connected      →  HorizontalPager (2 pages, swipe left/right)
                              │
                              ├── Page 0: LiveHrPage    (BPM + RR intervals)
                              └── Page 1: LiveHrvPage   (RMSSD chart + session avg)
```

Swipe gestures are handled by `HorizontalPager` from
`androidx.compose.foundation.pager` — no additional navigation library needed.

### 8.2 Page 0 — LiveHrPage (existing MonitorScreen content)

```
┌────────────────────────────────────────────────┐
│                      72                        │
│                     BPM                        │
│                                                │
│  RR: 832, 814, 841 ms                          │
│  Avg RR: 829 ms                                │
│                                                │
│  ○ ●   ← page indicator (swipe for HRV →)     │
│                                                │
│  [         Disconnect         ]                │
└────────────────────────────────────────────────┘
```

### 8.3 Page 1 — LiveHrvPage (new)

```
┌────────────────────────────────────────────────┐
│  HRV (RMSSD)               Session: 2h 14m     │
│                                                │
│  ┌────────────────────────────────────────┐    │
│  │  52 │        ╭──╮                      │    │
│  │  46 │  ╭─╮  ╭╯  ╰──╮   ← snapshots    │    │
│  │  40 │──┼──────────────── ← session avg │    │
│  │  34 │╭╯                               │    │
│  │     └────────────────────             │    │
│  └────────────────────────────────────────┘    │
│                                                │
│  Now: 44.2 ms    Session avg: 41.8 ms          │
│                                                │
│  ● ○   ← page indicator (← swipe for HR)      │
│                                                │
│  [         Disconnect         ]                │
└────────────────────────────────────────────────┘
```

### 8.4 Implementation — HorizontalPager

```kotlin
@Composable
private fun MonitorScreen(viewModel: HrViewModel, deviceId: String) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    HorizontalPager(state = pagerState) { page ->
        when (page) {
            0 -> LiveHrPage(viewModel = viewModel, deviceId = deviceId)
            1 -> LiveHrvPage(viewModel = viewModel, deviceId = deviceId)
        }
    }
}
```

`HorizontalPager` requires:
```kotlin
// build.gradle.kts — already bundled with compose-foundation, no extra dep needed
implementation("androidx.compose.foundation:foundation")
```

### 8.5 History Screen

Session history (`HrvHistoryScreen`) is a **separate screen** accessible from the app's
top-level navigation (e.g. top bar icon), not part of the pager. It shows past sessions
with charts and is only relevant when NOT in an active monitoring session.

---

## 9. Implementation Plan

### Phase A — Domain + Calculator ⏱️ ~1 day

- [ ] `HrvSnapshot.kt` — value object
- [ ] `HrvSession.kt` — aggregate with `averageRmssd`
- [ ] `HrvCalculator.kt` — `rmssd(List<Int>): Double?` with 5-min window support
- [ ] Unit tests: `HrvCalculatorTest` — edge cases (empty list, < 20 intervals, known values)

### Phase B — Application Layer (Ports + Use Cases) ⏱️ ~1 day

- [ ] `HrvSessionRepositoryPort.kt` — output port interface
- [ ] `StartHrvSessionInputPort.kt`, `StopHrvSessionInputPort.kt`, `GetSessionHistoryInputPort.kt`
- [ ] `StartHrvSessionUseCase.kt`, `StopHrvSessionUseCase.kt`, `RecordHrvSnapshotUseCase.kt`, `GetSessionHistoryUseCase.kt`
- [ ] Unit tests with MockK for each use case

### Phase C — File Persistence ⏱️ ~0.5 day

- [ ] `HrvSessionFileAdapter.kt` — implements `HrvSessionRepositoryPort`
  - [ ] `appendSnapshot()` — JSONL append with `Dispatchers.IO`
  - [ ] `loadAll()` — read + deserialise all session files
  - [ ] `finaliseSession()` — update header line with `endTime`
- [ ] Add `kotlinx-serialization-json` dependency if not already present
- [ ] Unit test: `HrvSessionFileAdapterTest` — write + read round-trip using a temp dir

### Phase D — ViewModel ⏱️ ~2 days

- [ ] `HrvViewModel.kt`
  - [ ] 5-min sliding RR buffer (`ArrayDeque<Pair<Long, Int>>` — timestamp + value)
  - [ ] Per-minute snapshot coroutine
  - [ ] Auto-end watcher (2-hour gap detection)
  - [ ] BLE reconnect loop
  - [ ] StateFlow: `currentRmssd`, `sessionAverage`, `sessionSnapshots`, `sessionActive`, `latestRrIntervals`
- [ ] `HrvViewModelTest.kt` — Turbine + MockK + `UnconfinedTestDispatcher`

### Phase E — UI ⏱️ ~2 days

- [ ] Add Vico dependency
- [ ] `HrvMonitorScreen.kt` — live chart, current RMSSD, session avg, RR intervals, Stop button
- [ ] `HrvHistoryScreen.kt` — list of past sessions, tap to see chart
- [ ] Navigation: integrate both screens into `NavHost`
- [ ] Update `AppDependencies.kt` — wire up new use cases and ViewModel

### Phase F — ForegroundService Integration ⏱️ ~1 day

- [ ] Ensure HRV collection survives screen-off (required for sleep monitoring)
- [ ] Move `HrvViewModel` coroutine scope to a `ForegroundService` scope
- [ ] "Sleep session active" persistent notification

---

## 10. Risks & Open Questions

| Risk | Mitigation |
|---|---|
| BLE stack instability on some Android OEMs (Xiaomi, Motorola) | Exponential backoff on retry; existing troubleshooting doc covers Motorola Edge 30 fix |
| App killed by Android OS during sleep (Doze mode) | `ForegroundService` + `WakeLock` (already planned in ADR-001) |
| RR intervals contain ectopic beats (artefacts) | Optional: add NN50 outlier filter (RR < 300 ms or > 2000 ms rejected) |
| 5-min window produces no output for first 5 minutes | Show "Warming up... (Xm remaining)" in UI |

---

## 11. Related Documents

- `ADR-001-Polar-H10-Integration.md` — Polar BLE SDK, ForegroundService decision
- `ADR-002-Testing-Stack.md` — JUnit 4 + MockK + Turbine
- `android-implementation-plan.md` — overall roadmap; this TDR covers a new Phase 1c
- Task Force (1996) — primary clinical reference for all HRV decisions in this project

