package com.exitsense.app.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.exitsense.app.presentation.theme.ConfidenceHigh
import com.exitsense.app.presentation.theme.ConfidenceLow
import com.exitsense.app.presentation.theme.ConfidenceMedium
import com.exitsense.app.presentation.theme.ExitSenseTheme

@Composable
fun ExitSenseTopBar(
    title: String,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
fun ConfidenceBar(
    confidence: Float,
    threshold: Float,
    modifier: Modifier = Modifier
) {
    val normalised = (confidence / 100f).coerceIn(0f, 1f)
    val color = when {
        confidence >= threshold -> ConfidenceHigh
        confidence >= threshold * 0.6f -> ConfidenceMedium
        else -> ConfidenceLow
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Confidence: ${confidence.toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
            Text(
                text = "Threshold: ${threshold.toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { normalised },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
fun SignalChip(
    label: String,
    score: Float,
    modifier: Modifier = Modifier
) {
    val chipColor = if (score > 0) ConfidenceHigh else ConfidenceLow
    SuggestionChip(
        onClick = {},
        label = {
            Text(
                text = "$label (${if (score > 0) "+" else ""}${score.toInt()})",
                style = MaterialTheme.typography.labelSmall
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = chipColor.copy(alpha = 0.15f),
            labelColor = chipColor
        ),
        modifier = modifier
    )
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun EmptyStateCard(
    message: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit = {}
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatusDot(
    isActive: Boolean,
    size: Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isActive) ConfidenceHigh else MaterialTheme.colorScheme.outline)
    )
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(name = "Top bar — with back", showBackground = true, widthDp = 360)
@Composable
private fun PreviewTopBarWithBack() {
    ExitSenseTheme { ExitSenseTopBar(title = "Exit History", onNavigateBack = {}) }
}

@Preview(name = "Top bar — home", showBackground = true, widthDp = 360)
@Composable
private fun PreviewTopBarHome() {
    ExitSenseTheme {
        ExitSenseTopBar(title = "Smart Exit Reminder",
            actions = { IconButton(onClick = {}) { Icon(Icons.Default.ArrowBack, null) } })
    }
}

@Preview(name = "Confidence bar — high (90%)", showBackground = true, widthDp = 360)
@Composable
private fun PreviewConfidenceBarHigh() {
    ExitSenseTheme { Box(Modifier.padding(16.dp)) { ConfidenceBar(confidence = 90f, threshold = 70f) } }
}

@Preview(name = "Confidence bar — medium (55%)", showBackground = true, widthDp = 360)
@Composable
private fun PreviewConfidenceBarMedium() {
    ExitSenseTheme { Box(Modifier.padding(16.dp)) { ConfidenceBar(confidence = 55f, threshold = 70f) } }
}

@Preview(name = "Confidence bar — low (20%)", showBackground = true, widthDp = 360)
@Composable
private fun PreviewConfidenceBarLow() {
    ExitSenseTheme { Box(Modifier.padding(16.dp)) { ConfidenceBar(confidence = 20f, threshold = 70f) } }
}

@Preview(name = "Signal chip — positive", showBackground = true)
@Composable
private fun PreviewSignalChipPositive() {
    ExitSenseTheme { Box(Modifier.padding(8.dp)) { SignalChip(label = "Wi-Fi off", score = 50f) } }
}

@Preview(name = "Signal chip — negative", showBackground = true)
@Composable
private fun PreviewSignalChipNegative() {
    ExitSenseTheme { Box(Modifier.padding(8.dp)) { SignalChip(label = "At home", score = -40f) } }
}

@Preview(name = "Empty state card", showBackground = true, widthDp = 360)
@Composable
private fun PreviewEmptyStateCard() {
    ExitSenseTheme {
        EmptyStateCard(message = "No exit events recorded yet.",
            icon = { Icon(Icons.Default.History, null, Modifier.size(48.dp)) })
    }
}

@Preview(name = "Status dots", showBackground = true)
@Composable
private fun PreviewStatusDots() {
    ExitSenseTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusDot(isActive = true, size = 12.dp)
            StatusDot(isActive = false, size = 12.dp)
        }
    }
}
