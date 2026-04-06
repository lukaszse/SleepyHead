package com.example.androidapp.framework.infra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.androidapp.framework.adapter.input.HrViewModel

/**
 * Main Compose screen that shows either a device scan list or the heart-rate monitor,
 * depending on connection state.
 *
 * When connected, a [HorizontalPager] lets the user swipe between:
 * - **Page 0** — [LiveHrPage] (BPM + RR intervals)
 * - **Page 1** — [LiveHrvPage] (RMSSD chart + session statistics)
 *
 * @param viewModel [HrViewModel] instance providing the UI state.
 * @param onNavigateToHistory Callback invoked when the user taps the history icon.
 */
@Composable
fun HrScreen(
    viewModel: HrViewModel,
    onNavigateToHistory: () -> Unit = {}
) {

    val isConnected by viewModel.isConnected.collectAsState()
    var selectedDeviceId by remember { mutableStateOf<String?>(null) }

    if (isConnected && selectedDeviceId != null) {
        MonitorScreen(
            viewModel = viewModel,
            deviceId = selectedDeviceId!!,
            onNavigateToHistory = onNavigateToHistory
        )
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
 * Connected-device screen with a [HorizontalPager]:
 * - Page 0 → [LiveHrPage] (BPM + RR)
 * - Page 1 → [LiveHrvPage] (RMSSD chart)
 *
 * A page indicator and a Disconnect button are shown below the pager.
 */
@Composable
private fun MonitorScreen(
    viewModel: HrViewModel,
    deviceId: String,
    onNavigateToHistory: () -> Unit = {}
) {

    val pagerState = rememberPagerState(pageCount = { 2 })

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Pager content (takes remaining space) ---
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> LiveHrPage(viewModel = viewModel)
                1 -> LiveHrvPage(viewModel = viewModel)
            }
        }

        // --- Page indicator ---
        Row(
            modifier = Modifier.padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pagerState.pageCount) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
                if (index < pagerState.pageCount - 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }

        // --- Device info + History + Disconnect ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Device: $deviceId",
                style = MaterialTheme.typography.bodySmall
            )
            IconButton(onClick = onNavigateToHistory) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = "Session history"
                )
            }
        }

        Button(
            onClick = { viewModel.stopMonitoring(deviceId) },
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Text(text = "Disconnect")
        }
    }
}
