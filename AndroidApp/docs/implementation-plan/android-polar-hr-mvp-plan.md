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
│   ├── port/
│   │   ├── input/
│   │   │   ├── ConnectDeviceUseCase.kt         # Input Port = Interfejs UseCase
│   │   │   └── GetHeartRateStreamUseCase.kt    # Input Port = Interfejs UseCase
│   │   └── output/
│   │       └── HeartRateMonitorPort.kt         # Output Port = Interfejs dla Frameworku
│   └── usecase/
│       ├── ConnectDeviceService.kt             # Implementacja Input Portu (UseCase)
│       └── GetHeartRateStreamService.kt        # Implementacja Input Portu (UseCase)
│
└── framework/                                  # 3. FRAMEWORK (Detale techniczne)
    ├── adapter/
    │   ├── input/
    │   │   └── ui/                             # Driving Adapter (Android UI)
    │   │       ├── MainActivity.kt
    │   │       ├── HrScreen.kt
    │   │       └── HrViewModel.kt              # zależy od interfejsów z port/input/
    │   └── output/
    │       └── polar/                          # Driven Adapter (Polar SDK)
    │           └── PolarBleAdapter.kt          # Implementacja HeartRateMonitorPort
    └── app/                                    # Konfiguracja / DI
        └── PolarApplication.kt
```

---

## 3. Kroki implementacji

### ✅ Krok 1 — Warstwa Domeny (Domain)

**Cel:** Zdefiniować język domenowy niezależny od technologii.
**Pliki:** `domain/model/HrData.kt`
**Status:** Ukończony (2026-03-10)

```kotlin
data class HrData(
    val bpm: Int,
    val rrIntervals: List<Int> // interwały w ms
)
```

---

### ✅ Krok 2 — Warstwa Aplikacji (Application)

**Cel:** Zdefiniować Przypadki Użycia (Interfejsy) oraz ich realizację przez Porty Wejściowe (Implementacja).
**Status:** Ukończony (2026-03-10)

#### 2a. Output Port (`port/output`)
Interfejs dla "tylnych drzwi" aplikacji (wyjście do sprzętu).

```kotlin
interface HeartRateMonitorPort {
    fun connect(deviceId: String)
    fun disconnect(deviceId: String)
    fun getHeartRateStream(deviceId: String): Flow<HrData>
}
```

#### 2b. Input Ports — Use Cases (`port/input`)
**Interfejsy** definiujące "przednie drzwi" aplikacji. Framework zależy od tych interfejsów, nigdy od implementacji.

```kotlin
// application/port/input/ConnectDeviceUseCase.kt
interface ConnectDeviceUseCase {
    fun connect(deviceId: String)
    fun disconnect(deviceId: String)
}

// application/port/input/GetHeartRateStreamUseCase.kt
interface GetHeartRateStreamUseCase {
    operator fun invoke(deviceId: String): Flow<HrData>
}
```

#### 2c. Use Case Implementations (`usecase`)
**Implementacje** interfejsów. Orkiestrują logikę, delegując do Output Portów.

```kotlin
// application/usecase/ConnectDeviceService.kt
class ConnectDeviceService(
    private val monitorPort: HeartRateMonitorPort
) : ConnectDeviceUseCase {
    override fun connect(deviceId: String) = monitorPort.connect(deviceId)
    override fun disconnect(deviceId: String) = monitorPort.disconnect(deviceId)
}

// application/usecase/GetHeartRateStreamService.kt
class GetHeartRateStreamService(
    private val monitorPort: HeartRateMonitorPort
) : GetHeartRateStreamUseCase {
    override fun invoke(deviceId: String): Flow<HrData> =
        monitorPort.getHeartRateStream(deviceId)
}
```

---

### ✅ Krok 3 — Warstwa Framework: Adapter Wyjściowy (Polar)

**Cel:** Implementacja portu przy użyciu konkretnej biblioteki (Polar SDK).
**Lokalizacja:** `framework/adapter/output/polar`
**Status:** Ukończony (2026-03-10)

1.  Dodaj zależność `kotlinx-coroutines-rx3` w `build.gradle.kts`.
2.  Utwórz `PolarBleAdapter` implementujący `HeartRateMonitorPort`.
3.  W środku mapuj `PolarBleApi` (RxJava) na `Flow` i obiekty `PolarHrData` na domenowe `HrData`.

---

### ✅ Krok 4 — Warstwa Framework: Adapter Wejściowy (UI)

**Cel:** Interfejs dla użytkownika. Traktujemy UI jako "wtyczkę" sterującą aplikacją.
**Lokalizacja:** `framework/adapter/input/ui`
**Status:** Ukończony (2026-03-10)

#### 4a. ViewModel (`HrViewModel`)
ViewModel komunikuje się **WYŁĄCZNIE** z warstwą Application (Use Cases), nigdy bezpośrednio z Adapterem Polara.

```kotlin
class HrViewModel(
    private val connectUseCase: ConnectDeviceUseCase,
    private val streamUseCase: GetHeartRateStreamUseCase
) : ViewModel() { ... }
```

#### 4b. Ekran i Uprawnienia (`MainActivity`, `HrScreen`)
1.  **Uprawnienia (Android 12+):** W `MainActivity` (entry point frameworku) obsłuż `BLUETOOTH_SCAN` i `BLUETOOTH_CONNECT` używając `ActivityResultContracts`.
2.  Dopiero po uzyskaniu uprawnień wyświetl `HrScreen`.
3.  Wstrzyknij zależności (DI łączy warstwy — ViewModel widzi tylko interfejs):
    ```kotlin
    // Framework/app — jedyne miejsce gdzie warstwy są sklejone
    val polarAdapter = PolarBleAdapter(context)          // Framework → implementuje HeartRateMonitorPort
    val streamService = GetHeartRateStreamService(polarAdapter) // Application → implementuje GetHeartRateStreamUseCase
    val connectService = ConnectDeviceService(polarAdapter)     // Application → implementuje ConnectDeviceUseCase
    // ViewModel otrzymuje INTERFEJSY (UseCase), nie konkretne klasy
    val viewModel = HrViewModel(connectService, streamService)
    ```

---

## 4. Testowanie

Architektura heksagonalna ułatwia testowanie kluczowej logiki bez Androida.

1.  **Unit Tests (`test/`):** Testuj `ConnectDeviceUseCase` i `GetHeartRateStreamUseCase` mockując `HeartRateMonitorPort`. To są najważniejsze testy logiki.
2.  **Integration Tests (`androidTest/`):** Testuj `PolarBleAdapter` (tylko jeśli masz jak mockować BLE) lub `HrScreen` używając `FakeHeartRateAdapter` (który symuluje dane).

## 5. Definicja ukończenia

- [x] Struktura katalogów zgodna z sekcją 2.
- [x] Warstwa `domain` nie ma `import android.*`.
- [x] Warstwa `application` zależy tylko od `domain`.
- [ ] Działa połączenie z paskiem i wyświetlanie tętna na telefonie.

