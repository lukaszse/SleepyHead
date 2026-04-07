# ADR-001: Polar H10 Sensor Integration (BLE) — SaaS Client Architecture

**Status:** Proposed
**Date:** 2026-03-06
**Author:** Lukasz Seremak

## 1. Context
The goal of the **SleepyHead** project is to collect biometric data (HR heart rate, HRV/RR-intervals heart rate variability) from the **Polar H10** chest strap in real-time and transmit it to a remote backend.

The project is intended to be a **SaaS** application — biometric data flows directly from the client device to a **remote cloud backend** (under consideration: AWS Lambda or similar serverless service). There is no need to maintain a local intermediary process on the user's laptop.

The preferred programming language is **Kotlin** (both for the Android client and the JVM backend).

### 1.1 Target Architecture (SaaS)
```
[Polar H10] --BLE--> [Android App (Kotlin)] --HTTPS/WSS--> [Cloud Backend (AWS Lambda / Kotlin)]
```
The user's phone is the **sole data entry point**. The backend processes and stores data. No software is required on the user's computer.

## 2. Technology Options Considered

### ✅ Option A: Android App (Kotlin) — SELECTED

Native Android application in Kotlin using the official **Polar BLE SDK for Android**.

*   **Pros:**
    *   Android BLE API is mature, stable, and well-documented.
    *   Official **Polar BLE SDK** (`com.polar.sdk`) dramatically simplifies BLE code — `api.startHrStreaming(deviceId)` instead of manual GATT parsing.
    *   Entire stack in **Kotlin** — shared data models with backend (`data class`, JSON/Protobuf).
    *   User's phone = only required device. No laptop dependency.
    *   Direct data transmission to cloud (HTTPS/WebSocket) without intermediaries.
    *   **Estimated time to MVP:** 3–7 days (with Polar SDK).
*   **Cons:**
    *   Requires learning the Android ecosystem (lifecycle, runtime permissions, Gradle).
    *   Background operation during sleep requires `ForegroundService`.
    *   Android Studio (~10 GB) — heavier environment than a simple console application.

### ❌ Option B: Web Bluetooth API (Browser)
Rejected. Requires open browser, no background operation, not suitable for data logging during sleep.

### ❌ Option C: Node.js Bridge (Laptop as intermediary)
Rejected. Requires running laptop for each session — conflicts with SaaS and mobile-first assumptions.

### ❌ Option D: Laptop + Ktor Server (Local network)
Rejected. Requires laptop on local network and additional user-side software — conflicts with SaaS architecture.

---

### Comparison Table

| Criterion | Option A (Android) | Option B (Web BT) | Option C (Node.js) | Option D (Laptop) |
|---|---|---|---|---|
| **Language** | Kotlin | JavaScript | Kotlin + JS | Kotlin |
| **Requires laptop** | ❌ No | ⚠️ Computer | ✅ Yes | ✅ Yes |
| **Runs in background** | ✅ ForegroundService | ❌ No | ✅ Yes | ✅ Yes |
| **SaaS compatible** | ✅ Yes | ❌ No | ❌ No | ❌ No |
| **BLE stability** | ✅ Very good | ✅ Good | ✅ Good | ✅ Good |
| **Time to MVP** | 3–7 days | 0.5–1 day | 1–3 days | 2–5 days |

## 3. Decision

**Option A Selected: Android App (Kotlin).**

This is the only approach consistent with the SaaS project assumption — the user only needs an Android phone. Data flows from Polar H10 via BLE directly to the cloud backend.

### Technology Stack

| Layer | Technology |
|---|---|
| BLE (data reading) | Polar BLE SDK for Android (`com.polar.sdk`) |
| Client application | Android (Kotlin, min. API 26 / Android 8.0) |
| Cloud transport | HTTPS (Retrofit / Ktor Client) or WebSocket (OkHttp) |
| Backend | AWS Lambda (Kotlin/JVM) or Ktor Server |
| Android permissions | `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `FOREGROUND_SERVICE` |

### Key Implementation Decisions

1.  **`ForegroundService`** — required for background operation during sleep (Android kills background processes). User sees "Sleep session active" notification.
2.  **Polar BLE SDK** instead of raw Android BLE API — less code, official Polar support, RR-intervals handling out-of-the-box.
3.  **Transport:** HTTPS POST every N seconds (simpler, buffering in case of network loss) or WebSocket (real-time streaming). Decision in separate ADR.

## 4. Implementation Details

### Polar H10 Service UUIDs
*   **Heart Rate Service:** `0000180d-0000-1000-8000-00805f9b34fb` (Short: `180D`)
*   **Heart Rate Measurement Characteristic:** `00002a37-0000-1000-8000-00805f9b34fb` (Short: `2A37`)

> **Note:** When using Polar BLE SDK, the above UUIDs are handled automatically by the SDK — no need for manual GATT management.

### Data Protocol (HRBL — for reference / fallback without SDK)
Data arrives as a byte array. Parsing must consider:
1.  **Byte 0 (Flags):**
    *   Bit 0: Heart rate format (0 = UINT8, 1 = UINT16).
    *   Bit 4: RR intervals presence (0 = absent, 1 = present).
2.  **Byte 1 (Heart Rate):** BPM value.
3.  **Byte 2+ (RR Intervals):** If RR flag is set, subsequent byte pairs (Little Endian uint16) represent intervals in units of 1/1024 second.

### Data Conversion (Kotlin)
```kotlin
// Convert raw RR interval to milliseconds
val rawRrInterval = 845 // value read from 2 bytes
val milliseconds = (rawRrInterval / 1024.0) * 1000.0
```
