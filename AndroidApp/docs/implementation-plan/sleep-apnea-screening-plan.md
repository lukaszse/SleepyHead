# Plan Implementacji: Sleep Apnea Screening

**Status:** Draft
**Data:** 2026-04-11
**Autor:** Lukasz Seremak
**Powiązane:** CONCEPT-001, TDR-001, ADR-001, ADR-002

---

## 1. Podsumowanie

Dokument opisuje plan implementacji rozszerzenia aplikacji **SleepyHead** o funkcję
**screeningu bezdechu sennego** zgodnie z **modularną architekturą multi-sensor**:

- **Polar H10 jako samodzielny system (Milestone 1)** — EKG 130 Hz + akcelerometr + RR interwały → CVHR + EDR + wysiłek oddechowy + pozycja ciała → eAHI bez pulsoksymetru
- **Pulsoksymetr BLE PLX jako opcjonalne rozszerzenie (Milestone 2)** — SpO₂ + Pulse Rate (standard `0x1822`) → podniesienie dokładności eAHI z ~r=0.65–0.80 do ~r=0.80–0.92
- **System aktywnie sugeruje** podłączenie pulsoksymetru, gdy działa w trybie H10-only

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

### Milestones

**✅ Milestone 1 — Polar H10 jako samodzielny system pre-screeningu**

Polar H10 dostarcza cztery kanały diagnostyczne: RR interwały, surowe EKG (130 Hz),
akcelerometr (25–50 Hz) i HR. Kombinacja CVHR + EDR + wysiłek oddechowy (ACC) + pozycja ciała
tworzy system pre-screeningu bezdechu **bez pulsoksymetru**. Szacowana korelacja z PSG: r ≈ 0.65–0.80.
Realizowany przez fazy: **A → B → C → D → F → G → H**.

**➕ Milestone 2 — Rozszerzenie o pulsoksymetr BLE PLX**

Dodanie kanału SpO₂ (ODI, desaturacje) podnosi dokładność eAHI do r ≈ 0.80–0.92.
Pulsoksymetr jest **opcjonalny** — system działa bez niego i informuje użytkownika
o dostępności rozszerzenia. Realizowany przez fazy: **Milestone 1 + E → I**.

### 1.2 Docelowa funkcjonalność (ten plan)

| Komponent | Faza | Priorytet | Milestone |
|---|---|---|---|
| Modele domenowe (SpO₂, EKG, ACC, apnea events) | A | ★★★★★ | M1 + M2 |
| Algorytmy DSP (Pan-Tompkins, EDR, CVHR, ODI, scoring) | B | ★★★★★ | M1 + M2 |
| Porty i use case'y | C | ★★★★★ | M1 + M2 |
| Rozszerzenie Polar adaptera (ECG + ACC) | D | ★★★★★ | **M1** |
| Adapter BLE PLX (pulsoksymetr) | E | ★★★☆☆ | M2 |
| Persistence JSONL (raporty nocne) | F | ★★★★☆ | M1 + M2 |
| ViewModel — orkiestracja H10-only (+ opcjonalnie dual-device) | G | ★★★★★ | **M1** |
| UI — ekrany screeningu (H10-only + sugestia pulsoksymetru) | H | ★★★★☆ | **✅ M1 gotowy** |
| *(opcja)* Adapter Wellue SleepU + rozszerzenie UI o SpO₂ | I | ★★☆☆☆ | ✅ M2 gotowy |

---

## 2. DAG zależności między fazami

```
A ──► B ──► C ──┬──► D ──┐
                │         │
                ├──► E ───┤  (E wymagane tylko dla Milestone 2)
                │         │
                ├──► F ───┤
                │         ▼
                └───────► G ──► H ──────────────────► [✅ Milestone 1]
                           │
                           └──► I (+ E) ─────────────► [✅ Milestone 2]
```

**Legenda:**
- Fazy D, E, F mogą być realizowane **równolegle** (po zakończeniu C)
- **Milestone 1:** Fazy A → B → C → D → F → G → H — Polar H10 jako samodzielny system (bez pulsoksymetru)
- **Milestone 2:** Milestone 1 + Fazy E → I — system rozszerzony z pulsoksymetrem BLE PLX
- Faza G (Milestone 1) wymaga B + C + D + F
- Faza I (Milestone 2) wymaga G + E
- Faza E jest **opcjonalna dla Milestone 1** — wymagana dopiero dla Milestone 2

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

### Faza D — Rozszerzenie PolarBleAdapter (ECG + ACC)

**Warstwa:** `framework/adapter/output/polar/`
**Zależy od:** Faza C
**Szacowany czas:** 3–4 dni
**Milestone:** ★ M1 — krytyczna ścieżka

Modyfikacja `PolarBleAdapter.kt`:
- Dodanie SDK Feature: `FEATURE_POLAR_ONLINE_STREAMING`
- Wdrożenie implementacji `EcgStreamPort` i `AccStreamPort` operując `rx3 asFlow()`
- Wszystkie nowe strumienie bridgowane przez `kotlinx-coroutines-rx3` — RxJava nie wycieka poza ten adapter

#### Kryteria akceptacji

- [ ] `startEcgStreaming()` dostarcza `Flow<EcgSample>` (130 Hz)
- [ ] `startAccStreaming()` dostarcza `Flow<AccSample>` (25–50 Hz)
- [ ] Żaden typ RxJava nie wycieka poza `PolarBleAdapter.kt`
- [ ] Testy jednostkowe z MockK + Turbine

---

### Faza E — Adapter BLE PLX (pulsoksymetr)

> **Faza opcjonalna dla Milestone 1 — wymagana dla Milestone 2.**
> Faza E może być realizowana równolegle z D i F, ale nie blokuje Milestone 1.

**Warstwa:** `framework/adapter/output/oximeter/`
**Zależy od:** Faza C
**Szacowany czas:** 3–5 dni
**Milestone:** M2

#### Nowe pliki

- `BlePlxAdapter.kt` — Implementacja klasyczna `PulseOximeterPort`, subskrypcja na Service `0x1822` (Notify `0x2A5F`)
- `SfloatParser.kt` — Dekodowanie protokołu IEEE 11073-20601 SFLOAT

#### Kryteria akceptacji

- [ ] Obsługa reconnections na poziomie GATT
- [ ] Prawidłowy rzut danych SFLOAT (70-100% SpO₂, tętno 30-250)

---


### Faza F — Persistence JSONL (raporty nocne)

**Warstwa:** `framework/adapter/output/file/`
**Zależy od:** Faza A, C
**Szacowany czas:** 2–3 dni

Utworzenie `ApneaSessionFileAdapter.kt` do zapisu danych raportów, formacie rozszerzonym i odpornym na utratę danych (append na systemie plików co 60 sekund).

---

### Faza G — ViewModel (orkiestracja multi-sensor)

**Warstwa:** `framework/adapter/input/`
**Zależy od Milestone 1:** Fazy B, C, D, F
**Zależy od Milestone 2:** Dodatkowo Faza E
**Szacowany czas:** 5–7 dni

Budowa **niezależnego** `ApneaViewModel.kt` orkiestrującego strumienie danych:

- **Tryb H10-only (Milestone 1):** Agreguje 3 strumienie (EKG, ACC, RR) → eAHI bez SpO₂.
  Wyświetla użytkownikowi kartę sugestii: *"Podłącz pulsoksymetr, aby zwiększyć dokładność o ~15–20%"*.
- **Tryb dual-device (Milestone 2):** Agreguje 4 strumienie (EKG, ACC, RR, SpO₂) → pełna fuzja kanałów.
- Wykrywa aktywną konfigurację sensorów i dynamicznie dostosowuje reguły scorera (patrz CONCEPT-001 §8.1.1).

---

### Faza H — UI (ekrany screeningu)

**Warstwa:** `framework/infra/ui/`
**Zależy od:** Faza G
**Szacowany czas:** 4–6 dni

- `ApneaSetupScreen.kt` — Konfiguracja Polar H10 (+ opcjonalnie pulsoksymetr).
  Tryb H10-only jest w pełni funkcjonalny; ekran pokazuje opcję dodania pulsoksymetru.
- `SensorSuggestionCard.kt` — Karta sugestii wyświetlana, gdy brak pulsoksymetru:
  *"Zwiększ dokładność: podłącz pulsoksymetr BLE (+~15–20% korelacja z PSG)"*
- `ApneaMonitoringScreen.kt` — Główny wjazd + Horizontal Pager
- `LiveApneaPage.kt` — Live monitoring (tryb adaptowany do dostępnych sensorów)
- `LiveSpO2Page.kt` — Dostępna tylko gdy pulsoksymetr podłączony (Milestone 2)
- `NightReportScreen.kt` i `ApneaHistoryScreen.kt`

Integracja z powtarzalnym layoutem wykorzystując `AppNavigation.kt`.

---

### Faza I *(opcjonalna — Milestone 2)* — Rozszerzenie UI o SpO₂ i adapter Wellue SleepU

**Zależy od:** Faza G + Faza E
Implementacja pełnego widoku SpO₂ (ODI, desaturacje, T90%), rozszerzenie raportów nocnych
o dane pulsoksymetru oraz opcjonalny adapter Viatom Technology z parsowaniem formatu
zamkniętego (`SleepuBleAdapter.kt`) i dyskretna rejestracja w `AppDependencies`.
