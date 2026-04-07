package com.example.androidapp.framework.infra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidapp.framework.adapter.input.HrViewModel

/**
 * Page 0 of the HorizontalPager — live heart-rate data (BPM + RR intervals).
 *
 * Displays the current BPM reading in large text and raw RR intervals
 * from the connected Polar device.
 *
 * @param viewModel [HrViewModel] providing HR state.
 */
@Composable
fun LiveHrPage(viewModel: HrViewModel) {

    val hrData by viewModel.hrData.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Avg RR: ${"%.0f".format(hrData!!.rrIntervals.average())} ms",
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
    }
}

