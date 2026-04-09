# Plan Implementacji: Sleep Apnea Screening

**Status:** Draft
**Data:** 2026-04-09
**Autor:** Lukasz Seremak
**Powiązane:** CONCEPT-001, TDR-001, ADR-001, ADR-002

---

## 1. Podsumowanie

Dokument opisuje plan implementacji rozszerzenia aplikacji **SleepyHead** o funkcję
**screeningu bezdechu sennego** na podstawie danych z dwóch sensorów BLE:

- **Polar H10** — EKG 130 Hz, akcelerometr 25–50 Hz, HR + RR interwały
- **Pulsoksymetr BLE PLX** — SpO₂ + Pulse Rate (standard `0x1822`)

Plan jest podzielony na **9 faz (A–I)** realizowanych iteracyjnie.
Każda faza jest samodzielnie testowalna i wdrażalna.

### 1.1 Bazowa funkcjonalność (już zaimplementowana)

| Komponent | Stan | Źródło |
|---|---|---|
| Połączenie BLE z Polar H10 | ✅ Zaimplementowany | ADR-001 |
| HR + RR streaming (1 Hz, per-beat) | ✅ Zaimplementowany | TDR-001 |
| RMSSD z 5-min sliding window | ✅ Zaimplementowany | TDR-001 |
| Sesje JSONL + ForegroundService | ✅ Zaimplementowany | TDR-001 Phase F |
| HrViewModel + UI (Compose) | ✅ Zaimplementowany | TDR-001 Phase E |
| **Łącznie testów** | **83** | Wszystkie warstwy |

### 1.2 Docelowa funkcjonalność (ten plan)

| Komponent | Faza | Priorytet |
|---|---|---|
| Modele domenowe (SpO₂, EKG, ACC, apnea events) | A | ★★★★★ |
| Algorytmy DSP (Pan-Tompkins, EDR, CVHR, ODI, scoring) | B | ★★★★★ |
| Porty i use case'y | C | ★★★★★ |
| Adapter BLE PLX (pulsoksymetr) | D | ★★★★★ |
| Rozszerzenie Polar adaptera (ECG + ACC) | E | ★★★★☆ |
| Persistence JSONL (raporty nocne) | F | ★★★★☆ |
| ViewModel — orkiestracja dual-device | G | ★★★★★ |
| UI — ekrany screeningu | H | ★★★★☆ |
| *(opcja)* Adapter Wellue SleepU | I | ★★☆☆☆ |

---

## 2. DAG zależności między fazami

```
A ──► B ──► C ──┬──► D ──┐
                │        │
                ├──► E ──┤
                │        │
                ├──► F ──┤
                │        ▼
                └──────► G ──► H
                          │
                          └──► I (opcja)
```

**Legenda:**
- Fazy D, E, F mogą być realizowane **równolegle** (po zakończeniu C)
- Faza G wymaga D + E + F
- Faza H wymaga G
- Faza I jest niezależna (wymaga jedynie C)

---

## 3. Nowe zależności Gradle

```kotlin
// app/build.gradle.kts — sekcja dependencies

// Brak nowych zależności zewnętrznych.
// Istniejące pokrywają wszystkie potrzeby:
// - BLE GATT → Android SDK (wbudowany)
// - Polar ECG/ACC → polar-ble-sdk:5.5.0 (obecny, wymaga dodania feature flag)
// - DSP (filtry, interpolacja) → pure Kotlin w domain/service/ (bez lib)
// - Serialization → kotlinx-serialization-json (obecny)
// - Test → JUnit 4 + MockK + Turbine (obecne)
```

> **Uwaga:** Algorytmy DSP (Butterworth IIR, cubic spline, Pan-Tompkins) będą
> implementowane w **pure Kotlin** w warstwie domenowej. To ~200–300 LOC na filtry +
> ~150 LOC na Pan-Tompkins. Alternatywa (`Apache Commons Math`) została odrzucona
> ze względu na duży JAR i zależność od Javy.

---

## 4. Fazy implementacji

### Faza A — Modele domenowe

**Warstwa:** `domain/model/`
**Zależy od:** —
**Szacowany czas:** 2–3 dni

#### Cel

Zdefiniowanie wszystkich value objectów, enum'ów i agregatów potrzebnych
do reprezentacji danych z nowych strumieni (SpO₂, EKG, ACC) oraz wyników
algorytmów (CVHR, EDR, ODI, scoring apnea).

#### Nowe pliki

| Plik | Typ | Opis |
|---|---|---|
| `SpO2Sample.kt` | `data class` | Próbka SpO₂: timestamp, spo2Percent, pulseRate, perfusionIndex |
| `EcgSample.kt` | `data class` | Próbka EKG: timestamp, voltageUv (130 Hz) |
| `AccSample.kt` | `data class` | Próbka ACC: timestamp, xMg, yMg, zMg |
| `BodyPosition.kt` | `enum class` | SUPINE, LEFT_LATERAL, RIGHT_LATERAL, PRONE, UPRIGHT, UNKNOWN |
| `RespiratoryEffortType.kt` | `enum class` | NORMAL, INCREASED, ABSENT, ONSET_DELAYED |
| `ApneaType.kt` | `enum class` | OBSTRUCTIVE, CENTRAL, MIXED |
| `Confidence.kt` | `enum class` | HIGH, MEDIUM, LOW |
| `CvhrCycle.kt` | `data class` | Cykl CVHR: startTime, endTime, minHr, maxHr, deltaHr, periodMs |
| `DesaturationEvent.kt` | `data class` | Zdarzenie desaturacji: startTime, nadirTime, endTime, baselineSpO2, dropPercent |
| `ApneaEvent.kt` | `data class` | Zdarzenie bezdechu: startTime, endTime, type, confidence, spo2Nadir |
| `ApneaEpoch.kt` | `data class` | Epoka 60 s: epochStartMs, features (Map), eventDetected, apneaEvent |
| `OdiResult.kt` | `data class` | Wynik ODI: odi3, odi4, desaturationCount, totalHours |
| `NightReport.kt` | `data class` | Agregat |

#### Kryteria akceptacji

- [ ] Wszystkie modele są `data class` lub `enum class` w `domain/model/`
- [ ] Żaden import spoza `domain/` (no Android, no framework)
- [ ] Zbudowano ~30 testów (metody pomocnicze i walidacja)
- [ ] Wszystkie testy przechodzą

---

### Faza B — Serwisy domenowe (algorytmy DSP)

**Warstwa:** `domain/service/`
**Zależy od:** Faza A
**Szacowany czas:** 5–8 dni

#### Cel

Implementacja algorytmów przetwarzania sygnałów (DSP) w **pure Kotlin**,
bez zależności od Androida.

#### Nowe pliki

| Plik | Typ | Opis | Złożoność |
|---|---|---|---|
| `SignalFilter.kt` | `object` | Wspólne narzędzia DSP (bandpass, spline, moving avg) | Średnia |
| `PanTompkinsDetector.kt` | `object` | Detekcja R-pików z surowego EKG 130 Hz | Wysoka |
| `EdrExtractor.kt` | `object` | ECG-Derived Respiration (modulacja R-amplitude) | Średnia |
| `CvhrDetector.kt` | `object` | Detekcja cykli bradykardia-tachykardia z RR | Średnia |
| `OdiCalculator.kt` | `object` | ODI₃ i ODI₄ z serii SpO₂ | Niska |
| `BodyPositionClassifier.kt` | `object` | Pozycja ciała z DC składowej ACC | Niska |
| `RespiratoryEffortAnalyzer.kt` | `object` | Wysiłek oddechowy z AC składowej ACC | Średnia |
| `ApneaScorer.kt` | `object` | Fuzja kanałów per-epoch, reguły rule-based | Wysoka |

#### Kryteria akceptacji

- [ ] Wszystkie serwisy to `object` w `domain/service/`
- [ ] Zero importów spoza `domain/`
- [ ] Przygotowano solidny zestaw unit testów (~73 testy dla tej warstwy)
- [ ] Algorytmy zachowane zgodnie ze wskazówkami w dokumentacji KONCEPCYJNEJ (CONCEPT-001)

---

### Faza C — Porty i Use Case'y

**Warstwa:** `application/`
**Zależy od:** Faza A, B
**Szacowany czas:** 3–4 dni

#### Nowe porty output

| Plik | Opis |
|---|---|
| `PulseOximeterPort.kt` | Skan, subskrypcja Flow<SpO2Sample> |
| `EcgStreamPort.kt` | Obsługa surowego Flow<EcgSample> |
| `AccStreamPort.kt` | Obsługa Flow<AccSample> |
| `ApneaSessionRepositoryPort.kt` | Operacja na raportach (create, appendEpoch, finalise) |

#### Kryteria akceptacji

- [ ] Pliki zaimplementowane pod `application/port/` oraz `application/usecase/`
- [ ] ~31 testów weryfikujących odpowiednie mapowania MockK

---

### Faza D — Adapter BLE PLX (pulsoksymetr)

**Warstwa:** `framework/adapter/output/oximeter/`
**Zależy od:** Faza C
**Szacowany czas:** 3–5 dni

#### Nowe pliki

- `BlePlxAdapter.kt` — Implementacja klasyczna `PulseOximeterPort`, subskrypcja na Service `0x1822` (Notify `0x2A5F`)
- `SfloatParser.kt` — Dekodowanie protokołu IEEE 11073-20601 SFLOAT

#### Kryteria akceptacji

- [ ] Obsługa reconnections na poziomie GATT
- [ ] Prawidłowy rzut danych SFLOAT (70-100% SpO₂, tętno 30-250)

---

### Faza E — Rozszerzenie PolarBleAdapter (ECG + ACC)

**Warstwa:** `framework/adapter/output/polar/`
**Zależy od:** Faza C
**Szacowany czas:** 3–4 dni

Modyfikacja `PolarBleAdapter.kt`:
- Dodanie SDK Feature: `FEATURE_POLAR_ONLINE_STREAMING`
- Wdrożenie implementacji `EcgStreamPort` i `AccStreamPort` operując `rx3 asFlow()`

---

### Faza F — Persistence JSONL (raporty nocne)

**Warstwa:** `framework/adapter/output/file/`
**Zależy od:** Faza A, C
**Szacowany czas:** 2–3 dni

Utworzenie `ApneaSessionFileAdapter.kt` do zapisu danych raportów, formacie rozszerzonym i odpornym na utratę danych (append na systemie plików co 60 sekund).

---

### Faza G — ViewModel (orkiestracja dual-device)

**Warstwa:** `framework/adapter/input/`
**Zależy od:** Fazy B, C, D, E, F
**Szacowany czas:** 5–7 dni

Budowa **niezależnego** `ApneaViewModel.kt` aby orkiestrować dwa połączenia i agregację 4 strumieni danych per minuta.

---

### Faza H — UI (ekrany screeningu)

**Warstwa:** `framework/infra/ui/`
**Zależy od:** Faza G
**Szacowany czas:** 4–6 dni

- `ApneaSetupScreen.kt` — Konfiguracja Polar H10 + pulsoksymetru
- `ApneaMonitoringScreen.kt` — Główny wjazd + Horizontal Pager
- `LiveSpO2Page.kt` & `LiveApneaPage.kt` — Live monitoring
- `NightReportScreen.kt` i `ApneaHistoryScreen.kt`

Integracja z powtarzalnym layoutem wykorzystując `AppNavigation.kt`.

---

### Faza I *(opcjonalna)* — Adapter Wellue SleepU

**Zależy od:** Faza C
Implementacja dodatkowego napędu Viatom Technology z uwzględnieniem parsowania w formacie zamkniętym i dyskretna rejestracja wewnątrz struktury `AppDependencies`.
