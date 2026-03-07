# Plan Implementacji MVP: Wyświetlanie Tętna Polar H10 (Architektura Heksagonalna)

> **Data:** 2026-03-07
> **Scope:** Tylko MVP — wyświetlenie HR na ekranie telefonu, połączenie BLE.
> **Architektura:** Hexagonal (Ports & Adapters) wg Davi Vieiry.

---

## 1. Cel dokumentu

Doprowadzić aplikację Android do stanu, w którym łączy się z Polar H10 i wyświetla tętno, przy zachowaniu ścisłego podziału na trzy warstwy heksagonalne.

**Kluczowa zasada:** Zależności wskazują zawsze do wewnątrz. Framework zależy od Aplikacji, Aplikacja zależy od Domeny. Domena nie zależy od niczego.

---

## 2. Struktura pakietów (wg Davi Vieiry)

```text
com.example.androidapp/
├── domain/                                     # 1. DOMAIN (Serce, brak zależności)
│   └── model/
│       └── HrData.kt                           # Value Object / Entity
│
├── application/                                # 2. APPLICATION (Orkiestracja)
│   ├── usecase/
│   │   ├── ConnectDeviceUseCase.kt             # Input Port (Interfejs wejściowy logika)
│   │   └── GetHeartRateStreamUseCase.kt        # Input Port (Interfejs wejściowy logika)
│   └── port/
│       └── output/
│           └── HeartRateMonitorPort.kt         # Output Port (Interfejs dla Infrastruktury)
│
└── infrastructure/                             # 3. FRAMEWORK (Detale techniczne)
    ├── adapter/
    │   ├── input/
    │   │   └── ui/                             # Driving Adapter (Android UI)
    │   │       ├── MainActivity.kt
    │   │       ├── HrScreen.kt
    │   │       └── HrViewModel.kt
    │   └── output/
    │       └── polar/                          # Driven Adapter (Polar SDK)
    │           └── PolarBleAdapter.kt          # Implementacja HeartRateMonitorPort
    └── app/                                    # Konfiguracja / DI
        └── PolarApplication.kt
```

---

## 3. Kroki implementacji

### Krok 1 — Warstwa Domeny (Domain)

**Cel:** Zdefiniować język domenowy niezależny od technologii.
**Pliki:** `domain/model/HrData.kt`

```kotlin
data class HrData(
    val bpm: Int,
    val rrIntervals: List<Int> // interwały w ms
)
```

---

### Krok 2 — Warstwa Aplikacji (Application)

**Cel:** Zdefiniować przypadki użycia (Use Cases) oraz interfejsy dla świata zewnętrznego (Ports).
**Pliki:** `HeartRateMonitorPort.kt`, `ConnectDeviceUseCase.kt`, `GetHeartRateStreamUseCase.kt`

#### 2a. Output Port (`port/output`)
Interfejs, który "obiecuje" dostarczanie danych, ale nie mówi jak.

```kotlin
interface HeartRateMonitorPort {
    fun connect(deviceId: String)
    fun disconnect(deviceId: String)
    fun getHeartRateStream(deviceId: String): Flow<HrData>
}
```

#### 2b. Use Cases (`usecase`)
Logika aplikacyjna. W MVP są proste, ale kluczowe dla architektury.

```kotlin
class ConnectDeviceUseCase(private val monitorPort: HeartRateMonitorPort) {
    fun connect(deviceId: String) = monitorPort.connect(deviceId)
    fun disconnect(deviceId: String) = monitorPort.disconnect(deviceId)
}

class GetHeartRateStreamUseCase(private val monitorPort: HeartRateMonitorPort) {
    operator fun invoke(deviceId: String): Flow<HrData> = monitorPort.getHeartRateStream(deviceId)
}
```

---

### Krok 3 — Warstwa Infrastruktury: Adapter Wyjściowy (Polar)

**Cel:** Implementacja portu przy użyciu konkretnej biblioteki (Polar SDK).
**Lokalizacja:** `infrastructure/adapter/output/polar`

1.  Dodaj zależność `kotlinx-coroutines-rx3` w `build.gradle.kts`.
2.  Utwórz `PolarBleAdapter` implementujący `HeartRateMonitorPort`.
3.  W środku mapuj `PolarBleApi` (RxJava) na `Flow` i obiekty `PolarHrData` na domenowe `HrData`.

---

### Krok 4 — Warstwa Infrastruktury: Adapter Wejściowy (UI)

**Cel:** Interfejs dla użytkownika. Traktujemy UI jako "wtyczkę" sterującą aplikacją.
**Lokalizacja:** `infrastructure/adapter/input/ui`

#### 4a. ViewModel (`HrViewModel`)
ViewModel komunikuje się **WYŁĄCZNIE** z warstwą Application (Use Cases), nigdy bezpośrednio z Adapterem Polara.

```kotlin
class HrViewModel(
    private val connectUseCase: ConnectDeviceUseCase,
    private val streamUseCase: GetHeartRateStreamUseCase
) : ViewModel() { ... }
```

#### 4b. Ekran i Uprawnienia (`MainActivity`, `HrScreen`)
1.  **Uprawnienia (Android 12+):** W `MainActivity` (entry point infrastruktury) obsłuż `BLUETOOTH_SCAN` i `BLUETOOTH_CONNECT` używając `ActivityResultContracts`.
2.  Dopiero po uzyskaniu uprawnień wyświetl `HrScreen`.
3.  Wstrzyknij zależności:
    ```kotlin
    val adapter = PolarBleAdapter(context) // Infrastructure
    val useCase = GetHeartRateStreamUseCase(adapter) // Application
    val viewModel = HrViewModelFactory(useCase).create() // UI Adapter
    ```

---

## 4. Testowanie

Architektura heksagonalna ułatwia testowanie kluczowej logiki bez Androida.

1.  **Unit Tests (`test/`):** Testuj `ConnectDeviceUseCase` i `GetHeartRateStreamUseCase` mockując `HeartRateMonitorPort`. To są najważniejsze testy logiki.
2.  **Integration Tests (`androidTest/`):** Testuj `PolarBleAdapter` (tylko jeśli masz jak mockować BLE) lub `HrScreen` używając `FakeHeartRateAdapter` (który symuluje dane).

## 5. Definicja ukończenia

- [ ] Struktura katalogów zgodna z sekcją 2.
- [ ] Warstwa `domain` nie ma `import android.*`.
- [ ] Warstwa `application` zależy tylko od `domain`.
- [ ] Działa połączenie z paskiem i wyświetlanie tętna na telefonie.

