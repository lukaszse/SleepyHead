package com.example.androidapp.application.usecase

import app.cash.turbine.test
import com.example.androidapp.application.port.output.HeartRateMonitorPort
import com.example.androidapp.domain.model.HrData
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetHeartRateStreamServiceTest {

    private val port = mockk<HeartRateMonitorPort>(relaxed = true)
    private val service = GetHeartRateStreamService(port)

    @Test
    fun `invoke returns flow from HeartRateMonitorPort`() {
        runTest {
            val expected = HrData(bpm = 72, rrIntervals = listOf(833))
            every { port.getHeartRateStream("ABC123") } returns flowOf(expected)

            service("ABC123").test {
                assertEquals(expected, awaitItem())
                awaitComplete()
            }
        }
    }

    @Test
    fun `invoke emits multiple HrData items in order`() {
        runTest {
            val first = HrData(bpm = 65, rrIntervals = listOf(923))
            val second = HrData(bpm = 70, rrIntervals = listOf(857))
            val third = HrData(bpm = 80, rrIntervals = listOf(750, 745))
            every { port.getHeartRateStream("ABC123") } returns flowOf(first, second, third)

            service("ABC123").test {
                assertEquals(first, awaitItem())
                assertEquals(second, awaitItem())
                assertEquals(third, awaitItem())
                awaitComplete()
            }
        }
    }

    @Test
    fun `invoke propagates error from port`() {
        runTest {
            every { port.getHeartRateStream("ABC123") } returns flow<HrData> {
                throw RuntimeException("BLE connection lost")
            }

            service("ABC123").test {
                assertEquals("BLE connection lost", awaitError().message)
            }
        }
    }

    @Test
    fun `invoke passes exact deviceId to port`() {
        runTest {
            val deviceId = "C0680226"
            every { port.getHeartRateStream(deviceId) } returns flowOf(
                HrData(bpm = 60, rrIntervals = listOf(1000))
            )

            service(deviceId).test {
                awaitItem()
                awaitComplete()
            }

            io.mockk.verify(exactly = 1) { port.getHeartRateStream(deviceId) }
        }
    }
}
