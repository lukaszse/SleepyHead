package com.example.androidapp.framework.infra.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.androidapp.domain.model.HrvSession
import com.example.androidapp.domain.model.HrvSnapshot
import com.example.androidapp.framework.adapter.input.HrViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen displaying the history of past HRV recording sessions.
 *
 * Loads all sessions on entry and shows them in a list. Tapping a session
 * expands an inline chart with RMSSD snapshots and session statistics.
 *
 * @param viewModel [HrViewModel] providing session history state.
 * @param onBack Callback invoked when the user taps the back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HrvHistoryScreen(
    viewModel: HrViewModel,
    onBack: () -> Unit
) {
    val sessions by viewModel.sessionHistory.collectAsState()
    val isLoading by viewModel.isLoadingHistory.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadSessionHistory() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HRV Session History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Loading sessions…")
                }
            } else if (sessions.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No sessions recorded yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connect to your Polar device and record your first HRV session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        SessionCard(session = session)
                    }
                }
            }
        }
    }
}

/**
 * Expandable card displaying a single HRV session summary.
 * Tapping it toggles the inline RMSSD chart.
 */
@Composable
private fun SessionCard(session: HrvSession) {
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // --- Header row ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateFormat.format(Date(session.startTime)),
                    style = MaterialTheme.typography.titleMedium
                )
                val durationText = sessionDurationText(session)
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- Stats row ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Avg RMSSD: ${session.averageRmssd?.let { "%.1f ms".format(it) } ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${session.snapshots.size} snapshots",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // --- Expanded: chart + detail ---
            if (expanded && session.snapshots.size >= 2) {
                Spacer(modifier = Modifier.height(12.dp))
                SessionChart(
                    snapshots = session.snapshots,
                    averageRmssd = session.averageRmssd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                SessionDetailStats(session)
            } else if (expanded && session.snapshots.size < 2) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Not enough data to display a chart.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Canvas-based line chart for a historical session's RMSSD snapshots.
 */
@Composable
private fun SessionChart(
    snapshots: List<HrvSnapshot>,
    averageRmssd: Double?,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val avgColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        val padLeft = 48f
        val padBottom = 24f
        val chartWidth = size.width - padLeft
        val chartHeight = size.height - padBottom

        val values = snapshots.map { it.rmssdMs }
        val minVal = (values.min() - 5).coerceAtLeast(0.0)
        val maxVal = values.max() + 5
        val range = (maxVal - minVal).coerceAtLeast(1.0)

        fun yOf(v: Double): Float =
            (chartHeight - ((v - minVal) / range * chartHeight)).toFloat()

        fun xOf(index: Int): Float =
            padLeft + index.toFloat() / (values.size - 1).coerceAtLeast(1) * chartWidth

        // Grid lines
        for (i in 0..3) {
            val y = yOf(minVal + range * i / 3)
            drawLine(gridColor, Offset(padLeft, y), Offset(size.width, y), strokeWidth = 1f)
        }

        // Session average (dashed)
        averageRmssd?.let { avg ->
            var x = padLeft
            while (x < size.width) {
                val segEnd = (x + 10f).coerceAtMost(size.width)
                drawLine(avgColor, Offset(x, yOf(avg)), Offset(segEnd, yOf(avg)), strokeWidth = 2f)
                x += 18f
            }
        }

        // RMSSD line + dots
        if (values.size >= 2) {
            val path = Path().apply {
                moveTo(xOf(0), yOf(values[0]))
                for (i in 1 until values.size) lineTo(xOf(i), yOf(values[i]))
            }
            drawPath(
                path, lineColor,
                style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            values.indices.forEach { i ->
                drawCircle(lineColor, radius = 4f, center = Offset(xOf(i), yOf(values[i])))
            }
        }
    }
}

/**
 * Detailed statistics shown when a session card is expanded.
 */
@Composable
private fun SessionDetailStats(session: HrvSession) {
    val snapshots = session.snapshots
    if (snapshots.isEmpty()) return

    val minRmssd = snapshots.minOf { it.rmssdMs }
    val maxRmssd = snapshots.maxOf { it.rmssdMs }
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(label = "Min", value = "%.1f ms".format(minRmssd))
            StatItem(label = "Max", value = "%.1f ms".format(maxRmssd))
            StatItem(label = "Avg", value = "%.1f ms".format(session.averageRmssd ?: 0.0))
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Start: ${dateFormat.format(Date(session.startTime))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            session.endTime?.let {
                Text(
                    text = "End: ${dateFormat.format(Date(it))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun sessionDurationText(session: HrvSession): String {
    val endMs = session.endTime ?: return "Active"
    val durationMin = (endMs - session.startTime) / 60_000L
    val h = durationMin / 60
    val m = durationMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

