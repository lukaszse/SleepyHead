package com.example.androidapp.framework.infra.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidapp.domain.model.HrvSnapshot
import com.example.androidapp.framework.adapter.input.HrViewModel

/**
 * Page 1 of the HorizontalPager — live HRV (RMSSD) chart with session statistics.
 *
 * Displays a Canvas-based line chart of per-minute RMSSD snapshots,
 * a dashed session-average line, and current/average value cards.
 *
 * @param viewModel [HrViewModel] providing HRV state.
 */
@Composable
fun LiveHrvPage(viewModel: HrViewModel) {

    val currentRmssd by viewModel.currentRmssd.collectAsState()
    val sessionAverage by viewModel.sessionAverage.collectAsState()
    val snapshots by viewModel.sessionSnapshots.collectAsState()
    val sessionActive by viewModel.sessionActive.collectAsState()

    val lineColor = MaterialTheme.colorScheme.primary
    val avgColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "HRV (RMSSD)", style = MaterialTheme.typography.headlineMedium)

        if (sessionActive && snapshots.isNotEmpty()) {
            val durationMin = sessionDurationMinutes(snapshots)
            Text(
                text = "Session: ${formatDuration(durationMin)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (snapshots.size >= 2) {
            RmssdChart(
                snapshots = snapshots,
                averageRmssd = sessionAverage,
                lineColor = lineColor,
                avgColor = avgColor,
                gridColor = gridColor,
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
        } else {
            WarmingUpPlaceholder(sessionActive)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ValueCard("Now", currentRmssd?.let { "%.1f ms".format(it) } ?: "—", lineColor)
            ValueCard("Session avg", sessionAverage?.let { "%.1f ms".format(it) } ?: "—", avgColor)
        }
    }
}

@Composable
private fun WarmingUpPlaceholder(sessionActive: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (sessionActive) {
            Text(
                text = "Warming up…",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "First RMSSD value in ~5 min.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "No HRV data",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Canvas-based line chart for RMSSD snapshots with grid and average line.
 */
@Composable
private fun RmssdChart(
    snapshots: List<HrvSnapshot>,
    averageRmssd: Double?,
    lineColor: Color,
    avgColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier
) {
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
            drawDashedLine(avgColor, Offset(padLeft, yOf(avg)), Offset(size.width, yOf(avg)))
        }

        // RMSSD line + dots
        if (values.size >= 2) {
            val path = Path().apply {
                moveTo(xOf(0), yOf(values[0]))
                for (i in 1 until values.size) lineTo(xOf(i), yOf(values[i]))
            }
            drawPath(path, lineColor, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            values.indices.forEach { i ->
                drawCircle(lineColor, radius = 4f, center = Offset(xOf(i), yOf(values[i])))
            }
        }
    }
}

private fun DrawScope.drawDashedLine(color: Color, start: Offset, end: Offset) {
    var x = start.x
    while (x < end.x) {
        val segEnd = (x + 10f).coerceAtMost(end.x)
        drawLine(color, Offset(x, start.y), Offset(segEnd, start.y), strokeWidth = 2f)
        x += 18f
    }
}

@Composable
private fun ValueCard(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 22.sp,
            color = color,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

private fun sessionDurationMinutes(snapshots: List<HrvSnapshot>): Long =
    if (snapshots.size < 2) 0L
    else (snapshots.last().timestamp - snapshots.first().timestamp) / 60_000L

private fun formatDuration(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

