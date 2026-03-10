package com.example.androidapp.framework.adapter.input.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Main Compose screen that displays heart-rate data and allows the user
 * to connect / disconnect from a Polar device.
 *
 * @param viewModel [HrViewModel] instance providing the UI state.
 * @param deviceId Polar device identifier used for connection (e.g. "A1B2C3D4").
 */
@Composable
fun HrScreen(viewModel: HrViewModel, deviceId: String) {

    val hrData by viewModel.hrData.collectAsState()
    val error by viewModel.error.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()

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

        Button(onClick = {
            if (isConnected) {
                viewModel.stopMonitoring(deviceId)
            } else {
                viewModel.startMonitoring(deviceId)
            }
        }) {
            Text(text = if (isConnected) "Disconnect" else "Connect")
        }
    }
}

