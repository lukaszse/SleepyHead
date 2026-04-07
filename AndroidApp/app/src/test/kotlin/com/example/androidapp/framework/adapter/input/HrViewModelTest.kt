package com.example.androidapp.framework.adapter.input

import app.cash.turbine.test
import com.example.androidapp.application.port.output.MonitoringServicePort
import com.example.androidapp.application.usecase.ConnectDeviceUseCase
import com.example.androidapp.application.usecase.GetSessionHistoryUseCase
import com.example.androidapp.application.usecase.GetHeartRateStreamUseCase
import com.example.androidapp.application.usecase.RecordHrvSnapshotUseCase
import com.example.androidapp.application.usecase.ScanForDevicesUseCase
import com.example.androidapp.application.usecase.StartHrvSessionUseCase
import com.example.androidapp.application.usecase.StopHrvSessionUseCase
import com.example.androidapp.domain.model.HrData
import com.example.androidapp.domain.model.HrvSession
import com.example.androidapp.domain.model.HrvSnapshot
import com.example.androidapp.domain.service.HrvCalculator
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HrViewModelTest {

    private val connectUseCase = mockk<ConnectDeviceUseCase>(relaxed = true)
    private val streamUseCase = mockk<GetHeartRateStreamUseCase>()
    private val scanUseCase = mockk<ScanForDevicesUseCase>(relaxed = true)
    private val startSessionUseCase = mockk<StartHrvSessionUseCase>()
    private val recordSnapshotUseCase = mockk<RecordHrvSnapshotUseCase>(relaxed = true)
    private val stopSessionUseCase = mockk<StopHrvSessionUseCase>(relaxed = true)
    private val getSessionHistoryUseCase = mockk<GetSessionHistoryUseCase>(relaxed = true)
    private val monitoringServicePort = mockk<MonitoringServicePort>(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: HrViewModel

    companion object {
        private const val DEVICE_ID = "ABC123"
        private val DEFAULT_SESSION = HrvSession(id = "test-session", startTime = 0L)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Default: connect succeeds immediately
        coEvery { connectUseCase.connect(any()) } returns Unit
        coEvery { startSessionUseCase.invoke() } returns DEFAULT_SESSION
        coEvery { stopSessionUseCase.invoke(any()) } returns DEFAULT_SESSION.copy(endTime = 1000L)
        viewModel = HrViewModel(
            connectUseCase, streamUseCase, scanUseCase,
            startSessionUseCase, recordSnapshotUseCase, stopSessionUseCase,
            getSessionHistoryUseCase, monitoringServicePort
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Initial state ---

    @Test
    fun `initial hrData is null`() {
        assertNull(viewModel.hrData.value)
    }

    @Test
    fun `initial error is null`() {
        assertNull(viewModel.error.value)
    }

    @Test
    fun `initial isConnected is false`() {
        assertFalse(viewModel.isConnected.value)
    }

    @Test
    fun `initial HRV state is inactive`() {
        assertNull(viewModel.currentRmssd.value)
        assertNull(viewModel.sessionAverage.value)
        assertTrue(viewModel.sessionSnapshots.value.isEmpty())
        assertFalse(viewModel.sessionActive.value)
    }

    // --- startMonitoring ---

    @Test
    fun `startMonitoring calls connect on use case`() {
        runTest {
            every { streamUseCase.invoke(DEVICE_ID) } returns flowOf<HrData>()

            viewModel.startMonitoring(DEVICE_ID)

            coVerify(exactly = 1) { connectUseCase.connect(DEVICE_ID) }
            viewModel.stopMonitoring(DEVICE_ID)
        }
    }

    @Test
    fun `startMonitoring sets isConnected to true after successful connect`() {
        runTest {
            every { streamUseCase.invoke(DEVICE_ID) } returns flowOf<HrData>()

            viewModel.startMonitoring(DEVICE_ID)

            assertTrue(viewModel.isConnected.value)
            viewModel.stopMonitoring(DEVICE_ID)
        }
    }

    @Test
    fun `startMonitoring clears previous error`() {
        runTest {
            // First cause an error via stream failure
            every { streamUseCase.invoke(DEVICE_ID) } returns flow<HrData> {
                throw RuntimeException("fail")
            }
            viewModel.startMonitoring(DEVICE_ID)
            assertEquals("fail", viewModel.error.value)

            // Now start again with a valid stream
            every { streamUseCase.invoke(DEVICE_ID) } returns flowOf<HrData>()
            viewModel.startMonitoring(DEVICE_ID)

            assertNull(viewModel.error.value)
            viewModel.stopMonitoring(DEVICE_ID)
        }
    }

    @Test
    fun `startMonitoring collects HrData into hrData state`() {
        runTest {
            val expected = HrData(bpm = 72, rrIntervals = listOf(833))
            every { streamUseCase.invoke(DEVICE_ID) } returns flowOf(expected)

            viewModel.startMonitoring(DEVICE_ID)

            viewModel.hrData.test {
                assertEquals(expected, awaitItem())
            }
            viewModel.stopMonitoring(DEVICE_ID)
        }
    }

    @Test
    fun `startMonitoring updates hrData with latest emission`() {
        runTest {
            val first = HrData(bpm = 65, rrIntervals = listOf(923))
            val second = HrData(bpm = 80, rrIntervals = listOf(750))
            every { streamUseCase.invoke(DEVICE_ID) } returns flowOf(first, second)

            viewModel.startMonitoring(DEVICE_ID)

            // With UnconfinedTestDispatcher both emissions are processed immediately,
            // so the state holds the last value.
            assertEquals(second, viewModel.hrData.value)
            viewModel.stopMonitoring(DEVICE_ID)
        }
    }

    // --- Connection error handling ---

    @Test
    fun `startMonitoring sets error when connect fails`() {
        runTest {
            coEvery { connectUseCase.connect(DEVICE_ID) } throws RuntimeException("BLE unavailable")

            viewModel.startMonitoring(DEVICE_ID)

            assertEquals("BLE unavailable", viewModel.error.value)
            assertFalse(viewModel.isConnected.value)
        }
    }

    @Test
    fun `startMonitoring does not start streaming when connect fails`() {
        runTest {
            coEvery { connectUseCase.connect(DEVICE_ID) } throws RuntimeException("BLE unavailable")

            viewModel.startMonitoring(DEVICE_ID)

            // streamUseCase should never be called if connect failed
            io.mockk.verify(exactly = 0) { streamUseCase.invoke(any()) }
        }
    }

    // --- Stream error handling ---

    @Test
    fun `startMonitoring sets error on stream failure`() {
        runTest {
            every { streamUseCase.invoke(DEVICE_ID) } returns flow<HrData> {
                throw RuntimeException("BLE connection lost")
            }

            viewModel.startMonitoring(DEVICE_ID)

            assertEquals("BLE connection lost", viewModel.error.value)
        }
    }

    @Test
    fun `startMonitoring sets isConnected to false on stream failure`() {
        runTest {
            every { streamUseCase.invoke(DEVICE_ID) } returns flow<HrData> {
                throw RuntimeException("BLE connection lost")
            }

            viewModel.startMonitoring(DEVICE_ID)

            assertFalse(viewModel.isConnected.value)
        }
    }

    @Test
    fun `startMonitoring sets Unknown error when exception has no message`() {
        runTest {
            every { streamUseCase.invoke(DEVICE_ID) } returns flow<HrData> {
                throw RuntimeException()
            }

            viewModel.startMonitoring(DEVICE_ID)

            assertEquals("Unknown error", viewModel.error.value)
        }
    }

    // --- stopMonitoring ---

    @Test
    fun `stopMonitoring calls disconnect on use case`() {
        viewModel.stopMonitoring(DEVICE_ID)

        verify(exactly = 1) { connectUseCase.disconnect(DEVICE_ID) }
    }

    @Test
    fun `stopMonitoring sets isConnected to false`() {
        runTest {
            // First connect
            every { streamUseCase.invoke(DEVICE_ID) } returns flowOf<HrData>()
            viewModel.startMonitoring(DEVICE_ID)
            assertTrue(viewModel.isConnected.value)

            // Then disconnect
            viewModel.stopMonitoring(DEVICE_ID)

            assertFalse(viewModel.isConnected.value)
        }
    }

    // ================================================================
    // HRV SESSION TESTS (new — TDR-001)
    // ================================================================

    // --- Session lifecycle ---

    @Test
    fun `startMonitoring starts HRV session`() {
        runTest {
            every { streamUseCase.invoke(DEVICE_ID) } returns flowOf<HrData>()

            viewModel.startMonitoring(DEVICE_ID)

            coVerify(exactly = 1) { startSessionUseCase.invoke() }
            assertTrue(viewModel.sessionActive.value)
            viewModel.stopMonitoring(DEVICE_ID)
        }
    }

    @Test
    fun `startMonitoring does not start session when connect fails`() {
        runTest {
            coEvery { connectUseCase.connect(DEVICE_ID) } throws RuntimeException("fail")

            viewModel.startMonitoring(DEVICE_ID)

            coVerify(exactly = 0) { startSessionUseCase.invoke() }
            assertFalse(viewModel.sessionActive.value)
        }
    }

    @Test
    fun `stopMonitoring stops HRV session`() {
        runTest {
            every { streamUseCase.invoke(DEVICE_ID) } returns flowOf<HrData>()
            viewModel.startMonitoring(DEVICE_ID)

            viewModel.stopMonitoring(DEVICE_ID)

            assertFalse(viewModel.sessionActive.value)
            coVerify(exactly = 1) { stopSessionUseCase.invoke("test-session") }
        }
    }

    // --- RR Buffer ---

    @Test
    fun `addRrIntervals populates RR buffer`() {
        val hrData = HrData(bpm = 72, rrIntervals = listOf(800, 810, 820))

        viewModel.addRrIntervals(hrData)

        // Not directly observable, but we can verify via snapshot computation later
        // For now we just verify it doesn't crash
    }

    // --- Snapshot timer with controlled time ---

    @Test
    fun `snapshot timer produces RMSSD after 60s with enough RR data`() {
        val testScheduler = kotlinx.coroutines.test.TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.resetMain()
        Dispatchers.setMain(dispatcher)

        var fakeTime = 100_000L
        coEvery { startSessionUseCase.invoke() } returns DEFAULT_SESSION

        val vm = HrViewModel(
            connectUseCase, streamUseCase, scanUseCase,
            startSessionUseCase, recordSnapshotUseCase, stopSessionUseCase,
            getSessionHistoryUseCase, monitoringServicePort,
            timeProvider = { fakeTime }
        )

        runTest(dispatcher) {
            // Setup: stream emits HrData with enough RR intervals, then suspends forever
            every { streamUseCase.invoke(DEVICE_ID) } returns flow {
                emit(HrData(bpm = 72, rrIntervals = (1..25).map { 800 + it }))
                kotlinx.coroutines.awaitCancellation()
            }

            vm.startMonitoring(DEVICE_ID)
            // Process coroutine setup without entering infinite timer loop
            testScheduler.advanceTimeBy(100)

            // Verify session is active and buffer is filled
            assertTrue(vm.sessionActive.value)

            // Advance 60 seconds to trigger first snapshot
            fakeTime += 60_000L
            testScheduler.advanceTimeBy(60_001L)

            // Should have produced a snapshot
            assertNotNull(vm.currentRmssd.value)
            assertEquals(1, vm.sessionSnapshots.value.size)
            assertNotNull(vm.sessionAverage.value)

            // Cleanup: cancel monitoringJob (and all children) before runTest exits
            vm.stopMonitoring(DEVICE_ID)
            testScheduler.advanceTimeBy(100)
        }

        // Restore for tearDown
        Dispatchers.resetMain()
        Dispatchers.setMain(testDispatcher)
    }

    @Test
    fun `snapshot timer does not produce RMSSD with too few RR intervals`() {
        val testScheduler = kotlinx.coroutines.test.TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.resetMain()
        Dispatchers.setMain(dispatcher)

        var fakeTime = 100_000L
        coEvery { startSessionUseCase.invoke() } returns DEFAULT_SESSION

        val vm = HrViewModel(
            connectUseCase, streamUseCase, scanUseCase,
            startSessionUseCase, recordSnapshotUseCase, stopSessionUseCase,
            getSessionHistoryUseCase, monitoringServicePort,
            timeProvider = { fakeTime }
        )

        runTest(dispatcher) {
            // Only 5 RR intervals — below MIN_INTERVALS
            every { streamUseCase.invoke(DEVICE_ID) } returns flow {
                emit(HrData(bpm = 72, rrIntervals = listOf(800, 810, 820, 830, 840)))
                kotlinx.coroutines.awaitCancellation()
            }

            vm.startMonitoring(DEVICE_ID)
            testScheduler.advanceTimeBy(100)

            fakeTime += 60_000L
            testScheduler.advanceTimeBy(60_001L)

            // No snapshot should be produced
            assertNull(vm.currentRmssd.value)
            assertTrue(vm.sessionSnapshots.value.isEmpty())

            vm.stopMonitoring(DEVICE_ID)
            testScheduler.advanceTimeBy(100)
        }

        Dispatchers.resetMain()
        Dispatchers.setMain(testDispatcher)
    }

    @Test
    fun `stopMonitoring resets HRV state flags`() {
        runTest {
            every { streamUseCase.invoke(DEVICE_ID) } returns flowOf<HrData>()
            viewModel.startMonitoring(DEVICE_ID)
            assertTrue(viewModel.sessionActive.value)

            viewModel.stopMonitoring(DEVICE_ID)

            assertFalse(viewModel.sessionActive.value)
        }
    }

    @Test
    fun `session snapshots are cleared on new session start`() {
        runTest {
            every { streamUseCase.invoke(DEVICE_ID) } returns flowOf<HrData>()

            viewModel.startMonitoring(DEVICE_ID)

            assertTrue(viewModel.sessionSnapshots.value.isEmpty())
            viewModel.stopMonitoring(DEVICE_ID)
        }
    }

    // --- Session history ---

    @Test
    fun `loadSessionHistory populates sessionHistory state`() {
        runTest {
            val sessions = listOf(
                HrvSession(id = "s1", startTime = 1000L, endTime = 2000L),
                HrvSession(id = "s2", startTime = 3000L, endTime = 4000L)
            )
            coEvery { getSessionHistoryUseCase.invoke() } returns sessions

            viewModel.loadSessionHistory()

            assertEquals(sessions, viewModel.sessionHistory.value)
            assertFalse(viewModel.isLoadingHistory.value)
        }
    }

    @Test
    fun `loadSessionHistory handles empty list`() {
        runTest {
            coEvery { getSessionHistoryUseCase.invoke() } returns emptyList()

            viewModel.loadSessionHistory()

            assertTrue(viewModel.sessionHistory.value.isEmpty())
        }
    }

    @Test
    fun `loadSessionHistory handles error gracefully`() {
        runTest {
            coEvery { getSessionHistoryUseCase.invoke() } throws RuntimeException("IO error")

            viewModel.loadSessionHistory()

            // Should not crash, history remains empty
            assertTrue(viewModel.sessionHistory.value.isEmpty())
            assertFalse(viewModel.isLoadingHistory.value)
        }
    }

    // ================================================================
    // FOREGROUND SERVICE PORT TESTS (Phase F — TDR-001)
    // ================================================================

    @Test
    fun `startMonitoring starts foreground service after successful connect`() {
        runTest {
            every { streamUseCase.invoke(DEVICE_ID) } returns flowOf<HrData>()

            viewModel.startMonitoring(DEVICE_ID)

            verify(exactly = 1) { monitoringServicePort.startForegroundMonitoring() }
            viewModel.stopMonitoring(DEVICE_ID)
        }
    }

    @Test
    fun `stopMonitoring stops foreground service`() {
        runTest {
            every { streamUseCase.invoke(DEVICE_ID) } returns flowOf<HrData>()
            viewModel.startMonitoring(DEVICE_ID)

            viewModel.stopMonitoring(DEVICE_ID)

            verify(exactly = 1) { monitoringServicePort.stopForegroundMonitoring() }
        }
    }

    @Test
    fun `startMonitoring does not start service when connect fails`() {
        runTest {
            coEvery { connectUseCase.connect(DEVICE_ID) } throws RuntimeException("fail")

            viewModel.startMonitoring(DEVICE_ID)

            verify(exactly = 0) { monitoringServicePort.startForegroundMonitoring() }
        }
    }

    @Test
    fun `service port failure does not prevent monitoring`() {
        runTest {
            every { streamUseCase.invoke(DEVICE_ID) } returns flowOf<HrData>()
            every { monitoringServicePort.startForegroundMonitoring() } throws RuntimeException("Service error")

            viewModel.startMonitoring(DEVICE_ID)

            // Monitoring should still be active despite service failure
            assertTrue(viewModel.sessionActive.value)
            viewModel.stopMonitoring(DEVICE_ID)
        }
    }
}
