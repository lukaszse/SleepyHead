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
  User  │  startSession()                         stopSession() │  User
  ──────►──────────────────────────────────────────────────────►──────
        │                                                     │
        │   Data gap ≥ 2 h  →  auto-end                       │
        │   ─────────────────────────────►  stopSession()     │
        └─────────────────────────────────────────────────────┘
```

**Rules:**
- Session starts **manually** — user taps "Start".
- Session ends **manually** — user taps "Stop"; or
- Session **auto-ends** if no RR data is received for ≥ **2 hours** (7,200,000 ms).
- After auto-end, a new session must be started manually.
- Session ID is a UUID generated at start.

### 3.3 BLE Reconnection Strategy

BLE disconnections are treated as **transient gaps** — they do NOT end the session.

```
[Connected] → [Disconnected] → [Retry every 5 s] → [Reconnected] → session continues
                                                                     (gap excluded)
```

- Gap intervals are **not added** to the 5-minute sliding window.
- The session average is computed only from actual snapshot data.
- If reconnection fails for ≥ 2 hours, the auto-end rule applies.

### 3.4 Session Average

The session average is a **simple mean of all per-minute RMSSD snapshots** collected
during the session:

```
sessionAvg = Σ(snapshot.rmssd) / snapshotCount
```

This is updated live — every time a new snapshot is added.

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
│  HrvSessionRoomAdapter  ─── implements ──►  HrvSessionRepositoryPort │
│  (Room / SQLite)                                                     │
└──────────────────────────────────────────────────────────────────────┘
```

### 5.1 New Files Overview

| Layer | File | Responsibility |
|---|---|---|
| Domain | `HrvSnapshot.kt` | Value object |
| Domain | `HrvSession.kt` | Aggregate |
| Domain | `HrvCalculator.kt` | RMSSD computation |
| App / Input Port | `StartHrvSessionInputPort.kt` | Interface |
| App / Input Port | `StopHrvSessionInputPort.kt` | Interface |
| App / Input Port | `GetSessionHistoryInputPort.kt` | Interface |
| App / Output Port | `HrvSessionRepositoryPort.kt` | Interface |
| App / Use Case | `StartHrvSessionUseCase.kt` | Impl of input port |
| App / Use Case | `StopHrvSessionUseCase.kt` | Impl of input port |
| App / Use Case | `RecordHrvSnapshotUseCase.kt` | Orchestrates per-minute save |
| App / Use Case | `GetSessionHistoryUseCase.kt` | Impl of input port |
| Framework Input | `HrvViewModel.kt` | Drives all HRV use cases, session state, live chart data |
| Framework Output | `HrvSessionRoomAdapter.kt` | Room implementation of repository port |
| Framework DB | `HrvSessionEntity.kt` | Room entity |
| Framework DB | `HrvSnapshotEntity.kt` | Room entity |
| Framework DB | `HrvSessionDao.kt` | Room DAO |
| Framework UI | `HrvMonitorScreen.kt` | Live RMSSD + chart + session avg |
| Framework UI | `HrvHistoryScreen.kt` | Past sessions list + detail |

---

## 6. HrvViewModel — Key State & Logic

```kotlin
class HrvViewModel(...) : ViewModel() {

    // --- Live state ---
    val currentRmssd: StateFlow<Double?>          // latest 5-min RMSSD
    val sessionAverage: StateFlow<Double?>        // running average of all snapshots
    val sessionSnapshots: StateFlow<List<HrvSnapshot>>  // data for chart
    val sessionActive: StateFlow<Boolean>
    val latestRrIntervals: StateFlow<List<Int>>   // raw RR from last HrData

    // --- Session management ---
    fun startSession()
    fun stopSession()

    // --- Internal ---
    // 1. Collect HrData from GetHeartRateStreamUseCase
    // 2. Append RR intervals into a timestamped deque (5-min sliding window)
    // 3. Every 60 s: compute RMSSD → save snapshot via RecordHrvSnapshotUseCase
    // 4. Watch lastReceivedTimestamp: if gap > 2h → auto-end
    // 5. On BLE disconnect: trigger reconnect loop (retry every 5 s)
}
```

### 6.1 Auto-end Implementation

```kotlin
// Pseudocode — runs in viewModelScope while session is active
private fun watchForAutoEnd() = viewModelScope.launch {
    while (sessionActive.value) {
        delay(60_000L) // check every minute
        val gap = System.currentTimeMillis() - lastReceivedTimestamp
        if (gap >= 2 * 60 * 60 * 1000L) {  // 2 hours
            stopSession()
        }
    }
}
```

### 6.2 BLE Reconnect Loop

```kotlin
// Pseudocode
private fun startStreamWithRetry(deviceId: String) = viewModelScope.launch {
    while (sessionActive.value) {
        try {
            heartRateStream(deviceId).collect { hrData ->
                lastReceivedTimestamp = System.currentTimeMillis()
                processHrData(hrData)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stream lost: ${e.message}. Retrying in 5 s...")
            delay(5_000L)
            reconnect(deviceId)
        }
    }
}
```

---

## 7. Persistence — Room Database

### 7.1 Entities

```kotlin
@Entity(tableName = "hrv_sessions")
data class HrvSessionEntity(
    @PrimaryKey val id: String,           // UUID
    val startTime: Long,
    val endTime: Long?
)

@Entity(
    tableName = "hrv_snapshots",
    foreignKeys = [ForeignKey(
        entity = HrvSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class HrvSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val timestamp: Long,
    val rmssdMs: Double,
    val rrIntervalCount: Int,
    val windowDurationMs: Long
)
```

### 7.2 Storage Estimates

**Assumptions:**
- Average sleep session: **8 hours** → 480 snapshots (1/minute).
- SQLite row overhead: ~80 bytes (B-tree, header, rowid alignment).
- `HrvSnapshotEntity` pure data: `8+8+8+8+4+8 = 44 bytes`.
- Realistic per snapshot row: **~124 bytes**.
- `HrvSessionEntity` row: **~80 bytes** (negligible).

| Period | Sessions | Snapshots | Storage |
|---|---|---|---|
| 1 night (8 h) | 1 | 480 | **~58 KB** |
| 1 week | 7 | 3,360 | **~406 KB** |
| 1 month (30 d) | 30 | 14,400 | **~1.7 MB** |
| 1 year | 365 | 175,200 | **~20.7 MB** |

> **Context:** A single JPEG photo from a modern Android phone is 3–5 MB.
> One year of HRV data equals the storage of roughly **4–5 photos**.
> No cleanup strategy is required at this scale.

---

## 8. UI — Live Chart & Display

### 8.1 What is displayed on HrvMonitorScreen

```
┌────────────────────────────────────────────────┐
│  HRV Monitor                 Session: 2h 14m   │
│                                                │
│  Live RMSSD (5-min window)                     │
│  ┌────────────────────────────────────────┐    │
│  │  52 │        ╭──╮                      │    │
│  │  46 │  ╭─╮  ╭╯  ╰──╮   ← snapshots    │    │
│  │  40 │──┼──────────────── ← session avg │    │
│  │  34 │╭╯                               │    │
│  │     └────────────────────             │    │
│  └────────────────────────────────────────┘    │
│                                                │
│  Now: 44.2 ms    Session avg: 41.8 ms          │
│  RR:  832, 814, 841 ms                         │
│                                                │
│  [         Stop Session         ]              │
└────────────────────────────────────────────────┘
```

### 8.2 Chart Library

Use **Vico** (`com.patrykandpatrick.vico`) — a Compose-first chart library for Android.
Lightweight (~150 KB), actively maintained, supports line charts with annotations.

```kotlin
// build.gradle.kts
implementation("com.patrykandpatrick.vico:compose-m3:1.14.0")
```

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

### Phase C — Room Persistence ⏱️ ~1 day

- [ ] `HrvSessionEntity.kt`, `HrvSnapshotEntity.kt`
- [ ] `HrvSessionDao.kt` — `insert`, `getSessionById`, `getSnapshotsForSession`, `updateEndTime`
- [ ] `AppDatabase.kt` — migration version bump
- [ ] `HrvSessionRoomAdapter.kt` — implements `HrvSessionRepositoryPort`

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

