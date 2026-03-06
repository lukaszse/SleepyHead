# ADR-001: Integracja Sensora Polar H10 (BLE) — Architektura Klienta SaaS

**Status:** Zaakceptowany
**Data:** 2026-03-06
**Autor:** GitHub Copilot (w imieniu Użytkownika)

## 1. Kontekst
Celem projektu **SleepyHead** jest pobieranie danych biometrycznych (tętno HR, zmienność tętna HRV/RR-intervals) z pasa piersiowego **Polar H10** w czasie rzeczywistym i przesyłanie ich do zdalnego backendu.

Projekt ma być aplikacją **SaaS** — dane biometryczne trafiają bezpośrednio z urządzenia klienckiego do **zdalnego backendu w chmurze** (rozważany: AWS Lambda lub podobny serwis serverless). Nie ma potrzeby utrzymywania lokalnego procesu pośredniczącego na laptopie użytkownika.

Preferowanym językiem programowania jest **Kotlin** (zarówno na kliencie Android, jak i na backendzie JVM).

### 1.1 Architektura Docelowa (SaaS)
```
[Polar H10] --BLE--> [Aplikacja Android (Kotlin)] --HTTPS/WSS--> [Backend w Chmurze (AWS Lambda / Kotlin)]
```
Telefon użytkownika jest **jedynym punktem wejścia danych**. Backend przetwarza i przechowuje dane. Nie jest wymagane żadne oprogramowanie po stronie komputera użytkownika.

## 2. Rozważane Opcje Technologiczne

### ✅ Opcja A: Aplikacja Android (Kotlin) — WYBRANA

Natywna aplikacja Android w Kotlinie korzystająca z oficjalnego **Polar BLE SDK dla Androida**.

*   **Zalety:**
    *   Android BLE API jest dojrzałe, stabilne i dobrze udokumentowane.
    *   Oficjalny **Polar BLE SDK** (`com.polar.sdk`) dramatycznie upraszcza kod BLE — `api.startHrStreaming(deviceId)` zamiast ręcznego parsowania GATT.
    *   Cały stack w **Kotlinie** — wspólne modele danych z backendem (`data class`, JSON/Protobuf).
    *   Telefon użytkownika = jedyne wymagane urządzenie. Brak zależności od laptopa.
    *   Bezpośrednie przesyłanie danych do chmury (HTTPS/WebSocket) bez pośredników.
    *   **Szacowany czas do MVP:** 3–7 dni (z Polar SDK).
*   **Wady:**
    *   Wymaga nauki ekosystemu Android (cykl życia, uprawnienia runtime, Gradle).
    *   Działanie w tle podczas snu wymaga `ForegroundService`.
    *   Android Studio (~10 GB) — cięższe środowisko niż prosta aplikacja konsolowa.

### ❌ Opcja B: Web Bluetooth API (Przeglądarka)
Odrzucona. Wymaga otwartej przeglądarki, brak działania w tle, nie nadaje się do rejestrowania danych podczas snu.

### ❌ Opcja C: Node.js Bridge (Laptop jako pośrednik)
Odrzucona. Wymaga uruchomionego laptopa przy każdej sesji — sprzeczne z założeniem SaaS i mobile-first.

### ❌ Opcja D: Laptop + Ktor Server (Sieć lokalna)
Odrzucona. Wymaga laptopa w sieci lokalnej i dodatkowego oprogramowania po stronie użytkownika — sprzeczne z architekturą SaaS.

---

### Tabela Porównawcza

| Kryterium | Opcja A (Android) | Opcja B (Web BT) | Opcja C (Node.js) | Opcja D (Laptop) |
|---|---|---|---|---|
| **Język** | Kotlin | JavaScript | Kotlin + JS | Kotlin |
| **Wymaga laptopa** | ❌ Nie | ⚠️ Komputer | ✅ Tak | ✅ Tak |
| **Działa w tle** | ✅ ForegroundService | ❌ Nie | ✅ Tak | ✅ Tak |
| **Zgodność z SaaS** | ✅ Tak | ❌ Nie | ❌ Nie | ❌ Nie |
| **Stabilność BLE** | ✅ Bardzo dobra | ✅ Dobra | ✅ Dobra | ✅ Dobra |
| **Czas do MVP** | 3–7 dni | 0.5–1 dzień | 1–3 dni | 2–5 dni |

## 3. Decyzja

**Wybrano Opcję A: Aplikacja Android (Kotlin).**

Jest to jedyne podejście zgodne z założeniem projektu SaaS — użytkownik potrzebuje wyłącznie telefonu z Androidem. Dane trafiają z Polar H10 przez BLE bezpośrednio do backendu w chmurze.

### Stos technologiczny

| Warstwa | Technologia |
|---|---|
| BLE (odczyt danych) | Polar BLE SDK for Android (`com.polar.sdk`) |
| Aplikacja kliencka | Android (Kotlin, min. API 26 / Android 8.0) |
| Transport do chmury | HTTPS (Retrofit / Ktor Client) lub WebSocket (OkHttp) |
| Backend | AWS Lambda (Kotlin/JVM) lub Ktor Server |
| Uprawnienia Android | `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `FOREGROUND_SERVICE` |

### Kluczowe decyzje implementacyjne

1.  **`ForegroundService`** — wymagany do działania w tle podczas snu (Android zabija procesy w tle). Użytkownik widzi powiadomienie "Sesja snu aktywna".
2.  **Polar BLE SDK** zamiast czystego Android BLE API — mniej kodu, oficjalne wsparcie Polar, obsługa RR-intervals out-of-the-box.
3.  **Transport:** HTTPS POST co N sekund (prostsze, buforowanie na wypadek utraty sieci) lub WebSocket (strumieniowanie real-time). Decyzja w osobnym ADR.

## 4. Szczegóły Implementacyjne

### UUID Serwisów Polar H10
*   **Heart Rate Service:** `0000180d-0000-1000-8000-00805f9b34fb` (Skrót: `180D`)
*   **Heart Rate Measurement Characteristic:** `00002a37-0000-1000-8000-00805f9b34fb` (Skrót: `2A37`)

> **Uwaga:** Przy użyciu Polar BLE SDK powyższe UUID są obsługiwane przez SDK automatycznie — nie ma potrzeby ręcznego zarządzania GATT.

### Protokół Danych (HRBL — dla referencji / fallback bez SDK)
Dane przychodzą jako tablica bajtów. Parsowanie musi uwzględniać:
1.  **Byte 0 (Flags):**
    *   Bit 0: Format tętna (0 = UINT8, 1 = UINT16).
    *   Bit 4: Obecność interwałów RR (0 = brak, 1 = obecne).
2.  **Byte 1 (Heart Rate):** Wartość BPM.
3.  **Byte 2+ (RR Intervals):** Jeśli flaga RR jest ustawiona, kolejne pary bajtów (Little Endian uint16) reprezentują interwały w jednostkach 1/1024 sekundy.

### Konwersja danych (Kotlin)
```kotlin
// Konwersja surowego interwału RR na milisekundy
val rawRrInterval = 845 // wartość odczytana z 2 bajtów
val milliseconds = (rawRrInterval / 1024.0) * 1000.0
```
