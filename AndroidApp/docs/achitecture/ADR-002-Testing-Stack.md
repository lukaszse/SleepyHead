# ADR-002: Testing Stack Selection — Android / Kotlin

**Status:** Accepted
**Date:** 2026-03-20
**Author:** Lukasz Seremak

## 1. Context

The SleepyHead project is a native Android application written entirely in **Kotlin** using a hexagonal architecture (Ports & Adapters). The MVP layer is complete — we now need a testing stack to cover:

1. **Application layer** — input ports (`ConnectDeviceInputPort`, `GetHeartRateStreamInputPort`) implementing use case interfaces, with a mocked `HeartRateMonitorPort`.
2. **ViewModel** (`HrViewModel`) — reactive logic based on `StateFlow` and `kotlinx.coroutines.flow.Flow`.
3. **UI** (`HrScreen`) — Compose instrumented tests (on device/emulator).

Key project characteristics influencing this decision:
- Language: **Kotlin** (100% of codebase)
- Asynchrony: **Kotlin Coroutines + Flow** (not RxJava on the application side)
- UI: **Jetpack Compose**
- Build system: **Gradle Kotlin DSL**
- No DI framework (manual injection in `MainActivity`)

## 2. Options Considered

### ✅ Option A: JUnit 4 + MockK + Turbine + coroutines-test — SELECTED

The standard testing stack for Android/Kotlin projects:

| Library | Role |
|---|---|
| `junit:junit:4.13.2` | Test runner (already present in the project) |
| `io.mockk:mockk:1.13.13` | Mocking Kotlin interfaces (ports, use cases) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1` | `runTest`, `TestDispatcher` for coroutines/Flow |
| `app.cash.turbine:turbine:1.2.0` | Flow assertions (`.test { awaitItem() }`) |
| `androidx.compose.ui:ui-test-junit4` | Compose UI testing (already present in the project) |

**Pros:**
- **Kotlin-native** — MockK understands `suspend fun`, `Flow`, `data class`, `object`, sealed classes, extension functions.
- **Zero configuration overhead** — JUnit 4 is already in the project; MockK/Turbine are pure `testImplementation` dependencies.
- **Industry standard** — 90%+ of Android/Kotlin projects use this stack. Extensive knowledge base, Stack Overflow, documentation.
- **Coroutines-first** — `runTest` automatically manages `TestDispatcher`; Turbine simplifies Flow assertions.
- **Existing example** — `ExampleUnitTest.kt` already runs with JUnit 4.

**Cons:**
- Tests are imperative (arrange-act-assert), not BDD-style.
- JUnit 4 is older than JUnit 5, but Android officially supports JUnit 4 as the primary runner.

---

### ❌ Option B: Spock Framework (Groovy) — REJECTED

Spock is a BDD (Behavior-Driven Development) framework based on **Groovy**, popular in the Java/Spring ecosystem.

```groovy
// Example Spock test
def "should delegate connect to monitor port"() {
    given:
    def port = Mock(HeartRateMonitorPort)
    def inputPort = new ConnectDeviceInputPort(port)

    when:
    inputPort.connect("ABC123")

    then:
    1 * port.connect("ABC123")
}
```

**General strengths of Spock:**
- Clean BDD syntax (`given/when/then`).
- Built-in mocking — no need for MockK or Mockito.
- Data-driven testing (`where:` blocks) — elegant parameterised tests.
- Mature framework (10+ years).

**Reasons for rejection in this project:**

| Problem | Details |
|---|---|
| **Groovy ≠ Kotlin** | The project is 100% Kotlin. Spock requires writing tests in Groovy — this adds a **second language** to the project. Groovy does not natively understand `suspend fun`, `Flow`, `sealed class`, `data class`. |
| **No coroutines support** | Spock has no equivalent of `runTest` or `TestDispatcher`. Testing `Flow` and `suspend fun` requires manual workarounds (`runBlocking` + bridging). |
| **No Android/Compose support** | Spock does not integrate with `androidx.test`, `ComposeTestRule`, or the Android instrumented test runner. UI tests would be impossible. |
| **Heavy Gradle configuration** | Requires the `groovy` plugin, a separate source set (`src/test/groovy`), and Groovy compilation alongside Kotlin — additional complexity in `build.gradle.kts`. |
| **Niche in Android** | Spock is popular in Spring/Java backends. In the Android ecosystem it is practically unused — no tutorials, no integration with the Android toolchain. |
| **MockK > Spock mocks for Kotlin** | MockK natively supports `suspend fun`, `Flow`, coroutines, `every { }` / `coEvery { }`. Spock mocks do not understand Kotlin coroutines. |

**Conclusion:** Spock is an excellent framework — but **for Java/Spring projects**. In an Android/Kotlin/Coroutines/Compose project it is technologically incompatible and would introduce unnecessary complexity.

---

### ❌ Option C: Kotest — REJECTED (for now)

Kotest is a Kotlin-native BDD framework — the natural answer to "I want BDD, but in Kotlin":

```kotlin
class ConnectDeviceInputPortTest : BehaviorSpec({
    given("a ConnectDeviceInputPort with mocked port") {
        val port = mockk<HeartRateMonitorPort>()
        val inputPort = ConnectDeviceInputPort(port)

        `when`("connect is called") {
            inputPort.connect("ABC123")

            then("it delegates to the port") {
                verify { port.connect("ABC123") }
            }
        }
    }
})
```

**Reasons for rejection:**
- Requires a separate runner (Kotest runner) — conflicts with `AndroidJUnitRunner` for instrumented tests.
- Smaller knowledge base than JUnit 4 in the Android context.
- Additional configuration complexity for a small project.
- **Can be reconsidered in the future** if the project grows and BDD-style becomes a priority.

## 3. Decision

**Option A: JUnit 4 + MockK + Turbine + coroutines-test.**

### Dependencies to add in `app/build.gradle.kts`

```kotlin
// Test dependencies
testImplementation("io.mockk:mockk:1.13.13")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
testImplementation("app.cash.turbine:turbine:1.2.0")
```

### Test structure

```text
app/src/
├── test/kotlin/                                   # Unit tests (JVM) — ./gradlew test
│   └── com/example/androidapp/
│       ├── application/port/input/
│       │   ├── ConnectDeviceInputPortTest.kt      # MockK + JUnit 4
│       │   └── GetHeartRateStreamInputPortTest.kt # MockK + Turbine + coroutines-test
│       └── framework/adapter/input/ui/
│           └── HrViewModelTest.kt                 # MockK + Turbine + coroutines-test
│
└── androidTest/kotlin/                            # Instrumented tests — ./gradlew connectedAndroidTest
    └── com/example/androidapp/
        └── framework/adapter/input/ui/
            └── HrScreenTest.kt                    # ComposeTestRule + FakePort
```

### Example test (pattern for the project)

```kotlin
class ConnectDeviceInputPortTest {

    private val port = mockk<HeartRateMonitorPort>(relaxed = true)
    private val inputPort = ConnectDeviceInputPort(port)

    @Test
    fun `connect delegates to HeartRateMonitorPort`() {
        inputPort.connect("ABC123")

        verify(exactly = 1) { port.connect("ABC123") }
    }

    @Test
    fun `disconnect delegates to HeartRateMonitorPort`() {
        inputPort.disconnect("ABC123")

        verify(exactly = 1) { port.disconnect("ABC123") }
    }
}
```

## 4. Consequences

### Positive
- Zero new tooling — extends the existing stack (JUnit 4 already in the project).
- Single language (Kotlin) for both production code and tests.
- Full coroutines/Flow support via `coroutines-test` + Turbine.
- Easy mocking of hexagonal ports thanks to MockK.

### Negative
- No BDD-style syntax (but tests are sufficiently readable with backtick names in Kotlin).
- JUnit 4 lacks the extension model of JUnit 5 (but Android officially supports JUnit 4).

### Risks
- None significant. This is the de facto standard in Android/Kotlin.

---

## Related Documents

- `ADR-001-Polar-H10-Integration.md` — decision on Android + Polar BLE SDK
- `android-polar-hr-mvp-plan.md`, section 4 — detailed test plan (what to test, file locations)
- `android-implementation-plan.md` — testing environment table
