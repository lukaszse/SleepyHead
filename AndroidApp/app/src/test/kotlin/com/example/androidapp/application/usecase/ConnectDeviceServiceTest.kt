package com.example.androidapp.application.usecase

import com.example.androidapp.application.port.output.HeartRateMonitorPort
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class ConnectDeviceServiceTest {

    private val port = mockk<HeartRateMonitorPort>(relaxed = true)
    private val service = ConnectDeviceService(port)

    @Test
    fun `connect delegates to HeartRateMonitorPort`() {
        service.connect("ABC123")

        verify(exactly = 1) { port.connect("ABC123") }
    }

    @Test
    fun `disconnect delegates to HeartRateMonitorPort`() {
        service.disconnect("ABC123")

        verify(exactly = 1) { port.disconnect("ABC123") }
    }

    @Test
    fun `connect passes exact deviceId to port`() {
        val deviceId = "C0680226"

        service.connect(deviceId)

        verify { port.connect(deviceId) }
        verify(exactly = 0) { port.disconnect(any()) }
    }

    @Test
    fun `disconnect passes exact deviceId to port`() {
        val deviceId = "C0680226"

        service.disconnect(deviceId)

        verify { port.disconnect(deviceId) }
        verify(exactly = 0) { port.connect(any()) }
    }
}

