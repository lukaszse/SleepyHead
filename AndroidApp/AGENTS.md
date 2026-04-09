# AGENTS.md â€” SleepyHead

## Overview

Android (Kotlin) sleep apnea pre-screening app using Polar H10 BLE chest strap.
Strict **hexagonal architecture** (Ports & Adapters, Davi Vieira style). No DI framework â€” manual wiring in `AppDependencies`.

## Architecture â€” Dependency Direction

```
domain/  â†  application/  â†  framework/
(pure Kotlin)  (use cases + ports)  (Android, BLE, files, UI)
```

- **`domain/`** â€” Zero Android imports. `data class` models + `object` services. Never depends on anything outside `domain/`.
- **`application/port/output/`** â€” Driven port **interfaces** (e.g. `HeartRateMonitorPort`, `HrvSessionRepositoryPort`).
- **`application/port/input/`** â€” Driving port **classes** that implement `application/usecase/` interfaces and orchestrate logic.
- **`application/usecase/`** â€” Use case **interfaces** only (e.g. `StartHrvSessionUseCase`). Input ports implement these.
- **`framework/adapter/output/`** â€” Driven adapters: `PolarBleAdapter` (BLE), `HrvSessionFileAdapter` (JSONL), `HrvServiceController` (ForegroundService).
- **`framework/adapter/input/`** â€” Single `HrViewModel` â€” the driving adapter. Depends on use case interfaces, never on adapters directly.
- **`framework/bootstrap/`** â€” `AppDependencies` (composition root), `SleepyHeadApplication` (singleton holder).

**Key rule:** New sensor = new output adapter implementing existing port. Domain + application untouched.

## Build & Test

```bash
./gradlew assembleDebug          # build
./gradlew test                   # run all unit tests (~83)
```

- JDK 11+, Android SDK 36, min SDK 24.
- `testOptions.unitTests.isReturnDefaultValues = true` â€” Android stubs return defaults in unit tests.

## Testing Patterns

- **JUnit 4** + **MockK** + **Turbine** (Flow assertions).
- Test naming: `` fun `descriptive name in backticks`() ``
- Tests mirror source tree: `src/test/kotlin/com/example/androidapp/{domain,application,framework}/`.
- ViewModel tests: `Dispatchers.setMain(UnconfinedTestDispatcher())` in `@Before`, `resetMain()` in `@After`.
- Use case tests: `mockk<OutputPort>(relaxed = true)` + `coEvery`/`coVerify` for suspend functions.
- `MonitoringServicePort` is **nullable** in `HrViewModel` â€” pass `null` in tests to skip foreground service calls.
- `timeProvider: () -> Long` parameter in `HrViewModel` â€” inject fake clock in tests.

## Async & RxJava Boundary

- **Coroutines + Flow** everywhere except `PolarBleAdapter.kt`.
- `PolarBleAdapter` is the **only** file using RxJava â€” bridges via `kotlinx-coroutines-rx3` `.asFlow()`.
- Never expose `Flowable`/`Observable` outside that adapter.
- `StateFlow` for all UI-observable state in ViewModel.

## Persistence â€” JSONL (not Room)

Sessions stored as `.jsonl` files in `filesDir/hrv_sessions/`:
- Line 1: `{"type":"header","id":"...","startTime":...,"endTime":...}`
- Lines 2+: `{"type":"snapshot","timestamp":...,"rmssdMs":...,...}`
- Serialization DTOs (`HeaderLine`, `SnapshotLine`) are `private` to `HrvSessionFileAdapter` â€” never leak to domain.
- `kotlinx-serialization-json` with `@Serializable`.

## Key Conventions

- All code identifiers (classes, methods, variables) in **English**; KDoc in **English**.
- `data class` for value objects/aggregates; `object` for stateless domain services.
- One class per file (except sealed classes).
- Communication language with user: **Polish**.

## Key Files to Understand the System

| Purpose | File |
|---|---|
| Composition root (DI) | `framework/bootstrap/AppDependencies.kt` |
| Main ViewModel (all monitoring logic) | `framework/adapter/input/HrViewModel.kt` |
| BLE â†” Flow bridge | `framework/adapter/output/polar/PolarBleAdapter.kt` |
| JSONL persistence | `framework/adapter/output/file/HrvSessionFileAdapter.kt` |
| RMSSD algorithm | `domain/service/HrvCalculator.kt` |
| Output port contracts | `application/port/output/HeartRateMonitorPort.kt`, `HrvSessionRepositoryPort.kt` |

## Design Documents

Read before making architectural changes:
- `docs/design/TDR-001-HRV-Monitoring.md` â€” HRV feature design (phases Aâ€“F)
- `docs/achitecture/ADR-001-Polar-H10-Integration.md` â€” why Polar H10
- `docs/concept/CONCEPT-001-Sleep-Apnea-Screening.md` â€” full screening concept

## AI Agent Definitions

This project uses specialized AI agents for specific tasks. Their definition files are located in `.github/agents/`:

| Agent | Definition File | Role |
|---|---|---|
| **Architecture Agent** | `.github/agents/architecture-agent.md` | Manages architectural decisions, ADRs, and structural integrity. |
| **Doc Agent** | `.github/agents/doc-agent.md` | Organizes markdown documentation, bug templates, and references `paths.json`. |
| **Diagram Agent** | `.github/agents/diagram-agent.md` | Handles creation and updates of visual diagrams (e.g., Mermaid). |
| **QA Agent** | `.github/agents/qa-agent.md` | Discovers bugs, tests edge cases, and delegates bug reports to the Doc Agent. |

> **Note:** Whenever you generate documentation or logs, verify the expected paths in [`paths.json`](paths.json).



