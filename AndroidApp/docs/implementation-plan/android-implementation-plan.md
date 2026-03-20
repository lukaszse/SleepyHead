# Plan Wdrożenia Aplikacji Android — SleepyHead

> **Uwaga:** Ten plan opisuje POC z Basic Auth, z myślą o docelowej architekturze wieloużytkownikowej z Apache Kafka.
> Aplikacja służy jednocześnie jako projekt do nauki Apache Kafka.

---

## 1. Prerequisites i Środowisko Deweloperskie

### Czy można uniknąć Android Studio?

Technicznie **tak**. IntelliJ IDEA z Android Plugin wystarcza dla POC — BLE wymaga fizycznego telefonu i tak, więc emulator nie jest potrzebny. Brakuje tylko Compose Preview i AVD Manager.

| Komponent | Wymagane? | Uwagi |
|---|---|---|
| **IntelliJ IDEA** z Android Plugin | ✅ Obowiązkowe | Alternatywa dla Android Studio |
| **JDK 21** (Temurin / Corretto) | ✅ Obowiązkowe | Ustawiane jako Gradle JDK w projekcie |
| **Android SDK** (API 34) | ✅ Obowiązkowe | SDK Manager w IDEA (Android 14.0 "UpsideDownCake") |
| **Android SDK Build-Tools 34** | ✅ Obowiązkowe | Bundled z SDK |
| **Gradle 8.6+** (Wrapper) | ✅ Obowiązkowe | `gradle-wrapper.properties` |
| **Kotlin 2.0+** | ✅ Obowiązkowe | Plugin Kotlin w Gradle |
| **Docker Desktop** | ✅ Obowiązkowe (Faza 2) | Kafka + PostgreSQL |
| **Android Studio** | ⚠️ Opcjonalne | Wygoda: Compose Preview, AVD |
| **Emulator AVD** | ❌ Pomiń | BLE **nie działa** na emulatorze |
| **Fizyczny telefon Android 14+** | ✅ Obowiązkowe | USB Debugging / Wireless ADB |

**Wersje:**
```
AGP: 8.3.x | Kotlin: 2.0.x | Min SDK: 26 | Target/Compile SDK: 34 | Gradle: 8.6+
```

> ℹ️ **Uwaga SDK:** Pobierz tylko **Android 14.0 (API 34)** w zakładce SDK Platforms. Nie musisz pobierać API 26 — `minSdk: 26` w `build.gradle` to tylko deklaracja kompatybilności wstecznej w kodzie.

---

## 2. Architektura Aplikacji Android

### Struktura pakietów

```
com.sleepyhead.app/
├── ui/
│   ├── pairing/        # PairingScreen + PairingViewModel
│   ├── monitor/        # MonitorScreen + MonitorViewModel
│   └── debuglog/       # DebugLogScreen + DebugLogViewModel
├── service/
│   └── BleService.kt   # ForegroundService + WakeLock
├── ble/
│   └── PolarManager.kt # Wrapper na Polar BLE SDK
├── repository/
│   ├── SessionRepository.kt
│   └── PacketRepository.kt
├── network/
│   ├── ApiService.kt        # Retrofit interface
│   └── AuthInterceptor.kt   # Basic Auth header
├── db/
│   ├── AppDatabase.kt       # Room
│   └── PacketDao.kt
└── model/
    ├── HrPacket.kt
    └── Session.kt
```

### Architektura MVVM

```
[Compose UI] ← StateFlow ← [ViewModel] ← [Repository]
                                              ↓
                              [BleService]  [Room DB]  [Retrofit/Network]
                                  ↓
                            [PolarManager]
```

### Developer Mode UI — 3 ekrany

| Ekran | Zawartość |
|---|---|
| **PairingScreen** | Skanowanie BLE, lista wykrytych urządzeń, parowanie z Polar H10, status połączenia |
| **MonitorScreen** | Live HR (bpm), RR-intervals (ms), status BLE, status uploadu (błędy, bufor) |
| **DebugLogScreen** | Lista ostatnich N pakietów z timestampem, błędy i retry'e, eksport CSV |

### ForegroundService + Uprawnienia

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Runtime flow: `App start → sprawdź uprawnienia → ActivityResultLauncher → uruchom BleService`

---

## 3. Komunikacja z Backendem

### 3.1 POC (teraz) — Basic Auth + HTTPS REST

Najprostsza implementacja:
- Retrofit + `OkHttpClient` z interceptorem `Authorization: Basic base64(user:pass)`
- Credentials w `BuildConfig` (z `local.properties` — nie trafia do Git)
- Batch upload co 30s lub gdy bufor osiągnie 50 pakietów
- Room DB jako lokalny bufor (kasowanie po ACK z serwera)

```
POST /api/sessions/{sessionId}/packets
Authorization: Basic <base64>

{ "packets": [{ "timestamp": 1234567890, "hr": 65, "rr": [845, 832, 851] }] }
```

### 3.2 Docelowa architektura komunikacji — porównanie

| Opcja | Opis | Nauka Kafki | Rekomendacja |
|---|---|---|---|
| **A: REST → Backend → Kafka** | Android wysyła REST, backend jest Kafka Producer | ⭐⭐⭐ Najlepsza | ✅ **Wybrana** |
| B: Android → Kafka REST Proxy | Android bezpośrednio do Confluent REST Proxy | ⭐ Anty-pattern | ❌ Credentials na telefonie |
| C: WebSocket → Backend → Kafka | Stałe połączenie WS, backend produkuje real-time | ⭐⭐ Dobra | ❌ Zbędna złożoność w POC |

**Opcja A** — REST POST z batching — pozwala jasno uczyć się gdzie dane wchodzą do Kafki, jak działają topiki, consumer groups i offset management.

### 3.3 Flow danych z Kafką (Faza 2+)

**Topiki:**
```
sleepyhead.hr.raw        # surowe pakiety HR+RR (klucz: userId / sessionId)
sleepyhead.sessions      # zdarzenia start/stop sesji
sleepyhead.hrv.computed  # (przyszłość) wyniki analizy HRV
```

**Consumer Groups:**
```
storage-group         → sleepyhead.hr.raw → PostgreSQL/TimescaleDB
hrv-analyzer-group    → sleepyhead.hr.raw → RMSSD/SDNN
notification-group    → sleepyhead.hrv.computed → alerty
```

> Room DB = lokalny bufor na telefonie (offline resilience). Kafka = kolejkowanie po stronie chmury. Uzupełniają się, nie zastępują.

---

## 4. Architektura Backendu Chmurowego

### 4.1 Hosting — porównanie

| Opcja | Koszt/mies | Kafka | Cold start | Rekomendacja |
|---|---|---|---|---|
| **Hetzner CAX11** (ARM) | ~3.29 EUR | ✅ Docker | ❌ Brak | ✅ **Najlepsza** |
| DigitalOcean Droplet (1GB) | ~6 USD | ✅ Docker | ❌ Brak | ✅ Alternatywa |
| Fly.io / Railway | ~0–5 USD | ⚠️ Zewnętrzna | ⚠️ Tak | ❌ Cold start JVM |
| AWS Lambda | Pay-per-use | ⚠️ MSK ~200 USD | ⚠️ 3–10s JVM | ❌ Za drogie |

**Rekomendacja: Hetzner CAX11** — Coolify lub Dokku jako PaaS, Docker Compose z Kafką, PostgreSQL na tym samym VPS.

### 4.2 Minimalna architektura backendu

**Stack:** Ktor Server (Kotlin) — spójny z Android, lekki, async

```
[Android] → POST /api/packets → [Ktor Server]
                                      ↓ (Faza 2)
                                [Kafka Producer]
                                      ↓
                             [Topic: sleepyhead.hr.raw]
                                      ↓
                           [Consumer: storage-service]
                                      ↓
                               [PostgreSQL]
```

**Endpointy MVP:**
```
POST  /api/sessions                  # Rozpocznij sesję
POST  /api/sessions/{id}/packets     # Wyślij batch HR/RR
PUT   /api/sessions/{id}/end         # Zakończ sesję
GET   /api/sessions/{id}/status      # Status (debug)
```

**Docker Compose (Faza 2):**
```yaml
services:
  kafka:      # bitnami/kafka z KRaft (bez Zookeeper!)
  kafka-ui:   # provectuslabs/kafka-ui
  postgres:   # postgres:16
  backend:    # Ktor jar
```

### 4.3 Droga do produkcji (multi-user SaaS)

1. Basic Auth → JWT (`ktor-auth-jwt`, `Bearer` token w Room DB)
2. `POST /auth/register`, `POST /auth/login`
3. Multi-tenancy: jeden topik, `userId` jako klucz partycji
4. Kafka Streams: RMSSD/SDNN w tumbling window 5 min

---

## 5. Roadmap

### Faza 0: Środowisko ⏱️ 1–2 dni
- [x] JDK 21 (ustawione w projekcie), Android Plugin w IntelliJ, SDK API 34
- [x] Nowy projekt Android (Empty Compose Activity, Kotlin DSL, Min SDK 26)
- [x] `./gradlew assembleDebug` → APK buduje się
- [ ] `adb devices` → telefon widoczny — **do weryfikacji**

### Faza 1a: MVP — Wyświetlanie HR na telefonie ✅ Ukończona
> Architektura heksagonalna (Ports & Adapters). Szczegóły w `android-polar-hr-mvp-plan.md`.

- [x] Zależności: `polar-ble-sdk`, `kotlinx-coroutines`, `kotlinx-coroutines-rx3` dodane w `build.gradle.kts`
- [x] Warstwa domenowa: `HrData` (bpm, rrIntervals)
- [x] Warstwa aplikacji: `ConnectDeviceUseCase`, `GetHeartRateStreamUseCase`, `HeartRateMonitorPort`
- [x] Implementacje use case: `ConnectDeviceService`, `GetHeartRateStreamService`
- [x] `PolarBleAdapter` (Driven Adapter) — `connect()` + `getHeartRateStream()` + callbacki BLE
- [x] `HrViewModel` — StateFlow: `hrData`, `error`, `isConnected`
- [x] `HrScreen` — wyświetla BPM (96sp), RR-interwały, przycisk Connect/Disconnect
- [x] `MainActivity` — obsługa uprawnień BLE (Android 12+), ręczne DI
- [ ] Test end-to-end na urządzeniu fizycznym z Polar H10 — **do weryfikacji**

### Faza 1b: Backend Integration — Android → Ktor → (Kafka) ⏱️ 3–5 dni
> Następny krok po weryfikacji Fazy 1a na fizycznym urządzeniu.

- [ ] `BleService` jako `ForegroundService` z WakeLock (zbieranie HR w tle)
- [ ] `PacketRepository` — zapis do Room DB jako lokalny bufor offline
- [ ] `BatchUploader` — wysyłka co 30s lub gdy bufor ≥ 50 pakietów
- [ ] `AuthInterceptor` — Basic Auth header (`BuildConfig.POC_USERNAME/PASSWORD`)
- [ ] Retrofit `ApiService` — `POST /api/sessions/{id}/packets`
- [ ] Ktor backend (lokalnie): przyjmuje batch HR i loguje do konsoli
- [ ] Test end-to-end: HR w logach backendu ✅

### Faza 2: Kafka ⏱️ 3–5 dni
- [ ] Docker Compose: Kafka (KRaft) + kafka-ui + PostgreSQL
- [ ] Backend: `KafkaProducer` w endpoincie
- [ ] Topik `sleepyhead.hr.raw` (3 partycje, 1 replika)
- [ ] `ConsumerService` → zapis do PostgreSQL
- [ ] Kafka UI: obserwuj offset, lag, throughput

### Faza 3: Developer Mode UI ⏱️ 3–5 dni
- [ ] `PairingScreen`, `MonitorScreen`, `DebugLogScreen` w Jetpack Compose
- [ ] `NavHost` łączący ekrany
- [ ] Status uploadu / Kafki w DebugLogScreen

### Faza 4: Multi-user SaaS ⏱️ Przyszłość
- [ ] JWT auth, ekrany logowania/rejestracji
- [ ] Dashboard webowy (np. Next.js)
- [ ] Kafka Streams — analiza HRV

---

## 6. Pierwsze 3 kroki do działającego POC

### Krok 1 — "Widzę Polar H10 w Logcat" ⏱️ ~2h

`build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.polar.sdk:polar-ble-sdk:5.9.0")
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
}
```

`MainActivity.kt`:
```kotlin
val api = PolarBleApiDefaultImpl.defaultImplementation(
    this, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)
)
api.setApiCallback(object : PolarBleApiCallback() {
    override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
        Log.d("SleepyHead", "Połączono: ${polarDeviceInfo.deviceId}")
    }
})
api.searchForDevice()
```

### Krok 2 — Backend Ktor z Basic Auth ⏱️ ~2h

```kotlin
install(Authentication) {
    basic("poc-auth") {
        validate { credentials ->
            if (credentials.name == "sleepyhead_poc" && credentials.password == "secret123")
                UserIdPrincipal(credentials.name) else null
        }
    }
}
authenticate("poc-auth") {
    post("/api/sessions/{id}/packets") {
        val packets = call.receive<PacketsRequest>()
        println("Otrzymano ${packets.packets.size} pakietów HR")
        call.respond(HttpStatusCode.OK)
    }
}
```

### Krok 3 — Android wysyła dane → end-to-end ⏱️ ~3h

`AuthInterceptor.kt`:
```kotlin
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Chain): Response {
        val credentials = Credentials.basic(
            BuildConfig.POC_USERNAME,
            BuildConfig.POC_PASSWORD
        )
        return chain.proceed(
            chain.request().newBuilder()
                .header("Authorization", credentials)
                .build()
        )
    }
}
```

**Pełny flow:**
```
Polar H10 --BLE--> PolarManager.startHrStreaming()
    → HrPacket(hr=65, rr=[845,832])
    → Room DB (bufor)
    → BatchUploader (co 30s)
    → Retrofit POST /api/sessions/{id}/packets
    → Ktor: "Otrzymano 15 pakietów HR" ✅
```

---

## Powiązane dokumenty

- `ADR-001-Polar-H10-Integration.md` — decyzja o Android + Polar BLE SDK
- `ADR-002-Transport-Strategy.md` — do utworzenia: REST vs WebSocket vs MQTT
- `ADR-003-Kafka-Architecture.md` — do utworzenia: topiki, consumer groups, multi-tenancy

