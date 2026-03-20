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
│       ├── HrData.kt                           # Value Object / Entity
│       └── FoundDevice.kt                      # Value Object — wykryte urządzenie BLE
│
├── application/                                # 2. APPLICATION (Orkiestracja)
│   ├── usecase/                                # Interfejsy Use Case (kontrakty)
│   │   ├── ConnectDeviceUseCase.kt             # Interfejs — zarządzanie połączeniem
│   │   ├── GetHeartRateStreamUseCase.kt        # Interfejs — strumień HR
│   │   └── ScanForDevicesUseCase.kt            # Interfejs — skanowanie urządzeń
│   └── port/
│       ├── input/                              # Input Ports (implementacje Use Case'ów)
│       │   ├── ConnectDeviceInputPort.kt       # Implementacja ConnectDeviceUseCase
│       │   ├── GetHeartRateStreamInputPort.kt  # Implementacja GetHeartRateStreamUseCase
│       │   └── ScanForDevicesInputPort.kt      # Implementacja ScanForDevicesUseCase
│       └── output/                             # Output Ports (interfejsy dla adapterów wyjściowych)
│           └── HeartRateMonitorPort.kt         # Interfejs dla adaptera BLE
│
└── framework/                                  # 3. FRAMEWORK (Detale techniczne)
    ├── adapter/
    │   ├── input/
    │   │   └── ui/                             # Driving Adapter (Android UI)
    │   │       ├── MainActivity.kt
    │   │       ├── HrScreen.kt
    │   │       └── HrViewModel.kt              # zależy od interfejsów z usecase/
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
**Status:** Ukończony (2026-03-10) — wszystkie 4 pliki obecne i poprawne

#### 2a. Output Port (`port/output`)
Interfejs dla "tylnych drzwi" aplikacji (wyjście do sprzętu).

```kotlin
interface HeartRateMonitorPort {
    fun connect(deviceId: String)
    fun disconnect(deviceId: String)
    fun getHeartRateStream(deviceId: String): Flow<HrData>
}
```

#### 2b. Use Cases (`usecase/`)
**Interfejsy** definiujące kontrakty aplikacji. Framework (ViewModel) zależy od tych interfejsów, nigdy od implementacji.

```kotlin
// application/usecase/ConnectDeviceUseCase.kt
interface ConnectDeviceUseCase {
    suspend fun connect(deviceId: String)
    fun disconnect(deviceId: String)
}

// application/usecase/GetHeartRateStreamUseCase.kt
interface GetHeartRateStreamUseCase {
    operator fun invoke(deviceId: String): Flow<HrData>
}

// application/usecase/ScanForDevicesUseCase.kt
interface ScanForDevicesUseCase {
    operator fun invoke(): Flow<FoundDevice>
}
```

#### 2c. Input Ports (`port/input/`)
**Implementacje** interfejsów Use Case. Orkiestrują logikę, delegując do Output Portów.
Nazewnictwo wg Davi Vieiry: Input Port = klasa implementująca Use Case.

```kotlin
// application/port/input/ConnectDeviceInputPort.kt
class ConnectDeviceInputPort(
    private val monitorPort: HeartRateMonitorPort
) : ConnectDeviceUseCase {
    override suspend fun connect(deviceId: String) = monitorPort.connect(deviceId)
    override fun disconnect(deviceId: String) = monitorPort.disconnect(deviceId)
}

// application/port/input/GetHeartRateStreamInputPort.kt
class GetHeartRateStreamInputPort(
    private val monitorPort: HeartRateMonitorPort
) : GetHeartRateStreamUseCase {
    override fun invoke(deviceId: String): Flow<HrData> =
        monitorPort.getHeartRateStream(deviceId)
}

// application/port/input/ScanForDevicesInputPort.kt
class ScanForDevicesInputPort(
    private val monitorPort: HeartRateMonitorPort
) : ScanForDevicesUseCase {
    override fun invoke(): Flow<FoundDevice> =
        monitorPort.scanForDevices()
}
```

---

### ✅ Krok 3 — Warstwa Framework: Adapter Wyjściowy (Polar)

**Cel:** Implementacja portu przy użyciu konkretnej biblioteki (Polar SDK).
**Lokalizacja:** `framework/adapter/output/polar`
**Status:** Ukończony (2026-03-20)

1.  ✅ Zależność `kotlinx-coroutines-rx3` dodana w `build.gradle.kts`.
2.  ✅ `PolarBleAdapter` implementuje `HeartRateMonitorPort`.
3.  ✅ Mapowanie: `PolarBleApi` (RxJava Flowable) → Kotlin `Flow`; `PolarHrData` → domenowe `HrData`.
4.  ✅ Callbacks logów BLE (connect / disconnect / battery / DIS info) obsłużone w `init`.

---

### ✅ Krok 4 — Warstwa Framework: Adapter Wejściowy (UI)

**Cel:** Interfejs dla użytkownika. Traktujemy UI jako "wtyczkę" sterującą aplikacją.
**Lokalizacja:** `framework/adapter/input/ui`
**Status:** Ukończony (2026-03-20)

#### 4a. ViewModel (`HrViewModel`) — ✅ Gotowy
ViewModel komunikuje się **WYŁĄCZNIE** z warstwą Application (Use Cases), nigdy bezpośrednio z Adapterem Polara.
Eksponuje trzy `StateFlow`: `hrData`, `error`, `isConnected`.

```kotlin
class HrViewModel(
    private val connectUseCase: ConnectDeviceUseCase,
    private val streamUseCase: GetHeartRateStreamUseCase
) : ViewModel() { ... }
```

#### 4b. Ekran i Uprawnienia (`MainActivity`, `HrScreen`) — ✅ Gotowe
1.  ✅ **Uprawnienia (Android 12+):** `MainActivity` obsługuje `BLUETOOTH_SCAN` i `BLUETOOTH_CONNECT` przez `ActivityResultContracts`.
2.  ✅ `HrScreen` renderuje się dopiero po udzieleniu uprawnień.
3.  ✅ Ręczne DI w `MainActivity.onCreate` — ViewModel widzi tylko interfejsy (Use Cases):
    ```kotlin
    // Framework/app — jedyne miejsce gdzie warstwy są sklejone
    val polarAdapter = PolarBleAdapter(applicationContext)            // Framework → HeartRateMonitorPort
    val connectInputPort = ConnectDeviceInputPort(polarAdapter)      // Input Port → ConnectDeviceUseCase
    val streamInputPort = GetHeartRateStreamInputPort(polarAdapter)  // Input Port → GetHeartRateStreamUseCase
    val scanInputPort = ScanForDevicesInputPort(polarAdapter)        // Input Port → ScanForDevicesUseCase
    val viewModel = HrViewModel(connectInputPort, streamInputPort, scanInputPort)
    ```
4.  ✅ `HrScreen` wyświetla BPM (96sp), RR-interwały i przycisk Connect/Disconnect.

#### ⚠️ Brakujące elementy
- `framework/app/PolarApplication.kt` — folder istnieje, ale plik nie został utworzony.
  Aktualnie DI odbywa się bezpośrednio w `MainActivity` — jest to akceptowalne dla MVP,
  ale `PolarApplication` byłoby właściwym miejscem dla konfiguracji globalnej (np. Hilt/Koin).

---

## 4. Testowanie

Architektura heksagonalna ułatwia testowanie kluczowej logiki bez Androida.

> Uzasadnienie wyboru stacka testowego → `ADR-002-Testing-Stack.md`

### 4.1 Środowisko testowe

#### Obecny stack (w `build.gradle.kts`)

| Zależność | Scope | Opis |
|---|---|---|
| `junit:junit:4.13.2` | `testImplementation` | JUnit 4 — runner testów jednostkowych |
| `androidx.test.ext:junit:1.2.1` | `androidTestImplementation` | JUnit runner dla testów instrumentalnych |
| `espresso-core:3.6.1` | `androidTestImplementation` | Testy UI (View-based) — nieużywane w Compose |
| `compose-ui-test-junit4` | `androidTestImplementation` | Testy UI Compose |

#### Brakujące zależności (do dodania)

| Zależność | Scope | Do czego |
|---|---|---|
| `io.mockk:mockk:1.13.13` | `testImplementation` | Mockowanie `HeartRateMonitorPort` w unit testach |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1` | `testImplementation` | Testowanie `Flow` i coroutines (`runTest`, `Turbine`) |
| `app.cash.turbine:turbine:1.2.0` | `testImplementation` | Asercje na `Flow` (`.test { awaitItem() }`) |

#### Lokalizacje testów

```text
app/src/
├── test/kotlin/com/example/androidapp/          # Unit testy (JVM, bez Androida)
│   ├── ExampleUnitTest.kt                        # ✅ Istniejący przykład (JUnit 4)
│   ├── application/port/input/
│   │   ├── ConnectDeviceInputPortTest.kt         # ✅ Gotowy
│   │   └── GetHeartRateStreamInputPortTest.kt    # ✅ Gotowy
│   └── framework/adapter/input/ui/
│       └── HrViewModelTest.kt                    # ✅ Gotowy
│
└── androidTest/kotlin/com/example/androidapp/    # Testy instrumentalne (na urządzeniu)
    ├── ExampleInstrumentedTest.kt                # ✅ Istniejący przykład
    └── framework/adapter/input/ui/
        └── HrScreenTest.kt                       # ❌ Do napisania (Compose testing)
```

### 4.2 Co testować

1.  **Unit Tests (`test/`)** — uruchamiane na JVM, bez emulatora/telefonu:
    - `ConnectDeviceInputPortTest` — mockuj `HeartRateMonitorPort`, weryfikuj że `connect()`/`disconnect()` delegują do portu.
    - `GetHeartRateStreamInputPortTest` — mockuj `HeartRateMonitorPort`, weryfikuj że `invoke()` zwraca `Flow<HrData>` z portu.
    - `HrViewModelTest` — mockuj oba use case'y, weryfikuj StateFlow (`hrData`, `error`, `isConnected`).
    - Status: ✅ Wszystkie zaimplementowane
2.  **Integration Tests (`androidTest/`)** — uruchamiane na urządzeniu/emulatorze:
    - `HrScreenTest` — użyj `FakeHeartRateMonitorPort` (bez BLE) + Compose test rules.
    - Status: ❌ Nieimplementowane

## 5. Definicja ukończenia

- [x] Struktura katalogów zgodna z sekcją 2.
- [x] Warstwa `domain` nie ma `import android.*`.
- [x] Warstwa `application` zależy tylko od `domain`.
- [x] Kod się kompiluje (`./gradlew assembleDebug` przechodzi).
- [ ] Działa połączenie z paskiem i wyświetlanie tętna na telefonie — **do weryfikacji na urządzeniu fizycznym**.
- [ ] `PolarApplication.kt` — opcjonalne; DI może pozostać w `MainActivity` dla MVP.
- [ ] Testy jednostkowe warstwy `application`.

---

> **Ostatnia aktualizacja:** 2026-03-20
> **Następny krok:** Wdrożenie APK na telefon i test end-to-end z Polar H10.

---

## 6. Co dalej po MVP (poza zakresem tego dokumentu)

Ten dokument kończy się w momencie, gdy HR pojawia się na ekranie telefonu.
Kolejne kroki opisane są w `android-implementation-plan.md` jako **Faza 1b**:

| Element | Opis |
|---|---|
| `BleService` | `ForegroundService` z WakeLock — zbieranie HR w tle |
| `PacketRepository` | Zapis do Room DB jako lokalny bufor offline |
| `BatchUploader` | Wysyłka do backendu co 30s lub przy ≥ 50 pakietach |
| `AuthInterceptor` | Basic Auth header (`BuildConfig.POC_USERNAME/PASSWORD`) |
| Retrofit `ApiService` | `POST /api/sessions/{id}/packets` |
| Ktor backend (lokalnie) | Przyjmuje batch HR i loguje do konsoli |

> Faza 1b zaczyna się **po** potwierdzeniu działającego połączenia BLE na urządzeniu fizycznym.

