# GitHub Copilot — Project Instructions for SleepyHead

## Response Language

- Always respond in the same language the user is writing in.
- Default communication language: **Polish**.

## Response Formatting

- **Write clearly.** Avoid walls of text — split responses into short sections with headings.
- Use **Markdown headings** (`##`, `###`) to organize sections.
- Format tables properly — with full names, no truncated text.
- Use bullet lists instead of long paragraphs.
- Always specify the language in code blocks (e.g., ` ```kotlin `).
- Use blank lines between sections for readability.
- Keep sentences short and concrete — don't ramble.

## Project Overview

- **App name:** SleepyHead
- **Purpose:** Sleep apnea pre-screening using Polar H10 + BLE pulse oximeter
- **Platform:** Android (min SDK 24)
- **Language:** Kotlin
- **Repo:** https://github.com/lukaszse/SleepyHead

## Architecture — Hexagonal / Ports & Adapters

The project follows strict hexagonal architecture. **Never violate dependency direction.**

```
domain/          → Pure Kotlin. NO Android imports. NO framework dependencies.
                   Models (value objects, aggregates) + domain services.

application/     → Use cases + port interfaces.
  port/input/    → Driving ports (UI calls these).
  port/output/   → Driven ports (interfaces that adapters implement).
  usecase/       → Implementations of input ports. Depend on output ports.

framework/       → Android-specific. Adapters + UI + bootstrap.
  adapter/input/ → Driving adapters (ViewModels).
  adapter/output/→ Driven adapters (Polar BLE SDK, file storage, services).
  bootstrap/     → DI wiring (AppDependencies), Application subclass.
  infra/         → MainActivity, Compose UI screens.
```

### Rules

1. **Domain** must NEVER import anything from `application`, `framework`, or Android SDK.
2. **Application** depends on domain. Defines ports. Must NOT import framework.
3. **Framework** implements ports. Depends on application + domain. May import Android SDK.
4. New sensors → new adapter implementing existing output port. Domain untouched.
5. All port interfaces live in `application/port/`. Never in `domain/` or `framework/`.

## Code Style

- Class, method, and variable names — **in English**.
- KDoc comments — **in English**.
- One class per file (except sealed classes / small related value objects).
- Data classes for value objects and aggregates.
- `object` for stateless domain services (e.g., `HrvCalculator`).

## Async & Concurrency

- **Coroutines + Flow** — primary async mechanism.
- **RxJava** — ONLY in Polar SDK adapter layer (`PolarBleAdapter.kt`), bridged to Flow via `kotlinx-coroutines-rx3`.
- Never expose RxJava types outside the adapter.
- Use `StateFlow` for UI state in ViewModels.
- Use structured concurrency — child coroutines tied to parent Job.

## Testing

- **JUnit 4** + **MockK** + **Turbine** (for Flow testing).
- Every layer must have tests: domain, application (use cases), framework (adapters, ViewModel).
- Test classes mirror source structure: `src/test/kotlin/com/example/androidapp/...`
- Use `UnconfinedTestDispatcher` or `StandardTestDispatcher` for coroutine tests.
- `testOptions.unitTests.isReturnDefaultValues = true` is set in `build.gradle.kts`.
- Naming convention: `fun \`descriptive test name in backticks\`()`.
- Run all tests: `./gradlew test`

## Persistence

- **JSONL files** — one file per session, one JSON line per snapshot.
- Header line (type: "header") + snapshot lines (type: "snapshot").
- Stored in `context.filesDir/hrv_sessions/`.
- `kotlinx-serialization-json` for serialization.
- Room/SQLite is NOT used (intentional decision — see TDR-001 §7.1).

## BLE

- **Polar BLE SDK** (`com.polar.sdk`) for Polar H10 communication.
- Currently active features: `FEATURE_HR`, `FEATURE_POLAR_SDK_MODE`.
- Planned: `FEATURE_POLAR_ONLINE_STREAMING` (ECG 130 Hz, ACC 200 Hz).
- Planned: Generic BLE PLX adapter (`0x1822`) for pulse oximeters.

## Background Operation

- `ForegroundService` with persistent notification for overnight monitoring.
- `PARTIAL_WAKE_LOCK` (10h timeout) to prevent CPU sleep.
- Service is a keep-alive shell — monitoring logic remains in ViewModel.
- `MonitoringServicePort` is nullable in ViewModel (optional for unit tests).

## Key Documents

Read these before making architectural decisions:

- `docs/achitecture/ADR-001-Polar-H10-Integration.md` — why Polar H10 + Android
- `docs/achitecture/ADR-002-Testing-Stack.md` — testing stack rationale
- `docs/design/TDR-001-HRV-Monitoring.md` — HRV feature design (phases A–F, all complete)
- `docs/concept/CONCEPT-001-Sleep-Apnea-Screening.md` — sleep apnea screening concept

## Dependencies

Key dependencies (see `app/build.gradle.kts` for full list):

- `com.github.polarofficial:polar-ble-sdk:5.5.0`
- `org.jetbrains.kotlinx:kotlinx-coroutines-rx3:1.8.1`
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3`
- `io.mockk:mockk:1.13.13`
- `app.cash.turbine:turbine:1.2.0`
- Jetpack Compose BOM `2024.12.01`
