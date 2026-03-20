package com.example.androidapp.framework.adapter.input.ui

import app.cash.turbine.test
import com.example.androidapp.application.port.input.ConnectDeviceUseCase
import com.example.androidapp.application.port.input.GetHeartRateStreamUseCase
import com.example.androidapp.application.port.input.ScanForDevicesUseCase
import com.example.androidapp.domain.model.FoundDevice
import com.example.androidapp.domain.model.HrData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HrViewModelTest {

    private val connectUseCase = mockk<ConnectDeviceUseCase>(relaxed = true)
    private val streamUseCase = mockk<GetHeartRateStreamUseCase>()
    private val scanUseCase = mockk<ScanForDevicesUseCase>(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: HrViewModel

    companion object {
        private const val DEVICE_ID = "ABC123"
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Default: connect succeeds immediately
        coEvery { connectUseCase.connect(any()) } returns Unit
        viewModel = HrViewModel(connectUseCase, streamUseCase, scanUseCase)
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

    // --- startMonitoring ---

    @Test
    fun `startMonitoring calls connect on use case`() {
        runTest {
            every { streamUseCase.invoke(DEVICE_ID) } returns flowOf<HrData>()

            viewModel.startMonitoring(DEVICE_ID)

            coVerify(exactly = 1) { connectUseCase.connect(DEVICE_ID) }
        }
    }

    @Test
    fun `startMonitoring sets isConnected to true after successful connect`() {
        runTest {
            every { streamUseCase.invoke(DEVICE_ID) } returns flowOf<HrData>()

            viewModel.startMonitoring(DEVICE_ID)

            assertTrue(viewModel.isConnected.value)
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
}
