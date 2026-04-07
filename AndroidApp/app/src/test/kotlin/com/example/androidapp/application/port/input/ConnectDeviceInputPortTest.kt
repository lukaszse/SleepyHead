package com.example.androidapp.application.port.input

import com.example.androidapp.application.port.output.HeartRateMonitorPort
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ConnectDeviceInputPortTest {

    private val port = mockk<HeartRateMonitorPort>(relaxed = true)
    private val inputPort = ConnectDeviceInputPort(port)

    @Test
    fun `connect delegates to HeartRateMonitorPort`() {
        runTest {
            inputPort.connect("ABC123")

            coVerify(exactly = 1) { port.connect("ABC123") }
        }
    }

    @Test
    fun `disconnect delegates to HeartRateMonitorPort`() {
        inputPort.disconnect("ABC123")

        verify(exactly = 1) { port.disconnect("ABC123") }
    }

    @Test
    fun `connect passes exact deviceId to port`() {
        runTest {
            val deviceId = "C0680226"

            inputPort.connect(deviceId)

            coVerify { port.connect(deviceId) }
            verify(exactly = 0) { port.disconnect(any()) }
        }
    }

    @Test
    fun `disconnect passes exact deviceId to port`() {
        val deviceId = "C0680226"

        inputPort.disconnect(deviceId)

        verify { port.disconnect(deviceId) }
        coVerify(exactly = 0) { port.connect(any()) }
    }
}

