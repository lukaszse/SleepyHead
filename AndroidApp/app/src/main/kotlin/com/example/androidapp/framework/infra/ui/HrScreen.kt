package com.example.androidapp.framework.infra.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidapp.framework.adapter.input.HrViewModel

/**
 * Main Compose screen that shows either a device scan list or the heart-rate monitor,
 * depending on connection state.
 *
 * In Davi Vieira's hexagonal architecture this is **framework infrastructure** —
 * a UI component that consumes state from the driving adapter ([HrViewModel])
 * but does not call use cases directly.
 *
 * @param viewModel [HrViewModel] instance providing the UI state.
 */
@Composable
fun HrScreen(viewModel: HrViewModel) {

    val isConnected by viewModel.isConnected.collectAsState()
    var selectedDeviceId by remember { mutableStateOf<String?>(null) }

    if (isConnected && selectedDeviceId != null) {
        MonitorScreen(viewModel = viewModel, deviceId = selectedDeviceId!!)
    } else {
        ScanScreen(
            viewModel = viewModel,
            onDeviceSelected = { deviceId ->
                selectedDeviceId = deviceId
                viewModel.startMonitoring(deviceId)
            }
        )
    }
}

/**
 * Screen that scans for nearby Polar devices and lets the user pick one.
 */
@Composable
private fun ScanScreen(
    viewModel: HrViewModel,
    onDeviceSelected: (String) -> Unit
) {
    val foundDevices by viewModel.foundDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Find your Polar device",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            if (isScanning) viewModel.stopScan() else viewModel.startScan()
        }) {
            Text(text = if (isScanning) "Stop Scan" else "Start Scan")
        }

        if (isScanning) {
            Spacer(modifier = Modifier.height(8.dp))
            CircularProgressIndicator()
        }

        error?.let { errorMessage ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (foundDevices.isEmpty() && !isScanning) {
            Text(
                text = "No devices found. Tap 'Start Scan' and make sure your Polar strap is on.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(foundDevices) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onDeviceSelected(device.deviceId) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = device.name ?: "Unknown Device",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = device.deviceId,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = "Connect",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

/**
 * Screen that displays live heart-rate data from a connected device.
 */
@Composable
private fun MonitorScreen(viewModel: HrViewModel, deviceId: String) {

    val hrData by viewModel.hrData.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = hrData?.bpm?.toString() ?: "--",
            fontSize = 96.sp,
            style = MaterialTheme.typography.displayLarge
        )

        Text(
            text = "BPM",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (hrData != null) {
            Text(
                text = "RR: ${hrData!!.rrIntervals.joinToString(", ")} ms",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        error?.let { errorMessage ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Device: $deviceId",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { viewModel.stopMonitoring(deviceId) }) {
            Text(text = "Disconnect")
        }
    }
}

