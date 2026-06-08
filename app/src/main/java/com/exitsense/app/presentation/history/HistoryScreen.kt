package com.exitsense.app.presentation.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exitsense.app.domain.model.ExitEvent
import com.exitsense.app.domain.model.ExitSignalType
import com.exitsense.app.presentation.components.EmptyStateCard
import com.exitsense.app.presentation.components.ExitSenseTopBar
import com.exitsense.app.presentation.components.LoadingScreen
import com.exitsense.app.presentation.theme.ConfidenceHigh
import com.exitsense.app.presentation.theme.ConfidenceLow
import com.exitsense.app.presentation.theme.ConfidenceMedium
import com.exitsense.app.presentation.theme.ExitSenseTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { ExitSenseTopBar(title = "Exit History", onNavigateBack = onNavigateBack) }
    ) { padding ->

        if (state.isLoading) { LoadingScreen(); return@Scaffold }

        if (state.events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    message = "No exit events recorded yet.\nThe app will log events when it detects you leaving home.",
                    icon = { Icon(Icons.Default.History, null, Modifier.size(48.dp)) }
                )
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.events, key = { it.id }) { event ->
                ExitEventCard(event)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExitEventCard(event: ExitEvent) {
    val fmt = remember { SimpleDateFormat("EEE, MMM d  HH:mm", Locale.getDefault()) }
    val confidenceColor = when {
        event.confidenceScore >= 70f -> ConfidenceHigh
        event.confidenceScore >= 40f -> ConfidenceMedium
        else -> ConfidenceLow
    }

    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    fmt.format(Date(event.timestamp)),
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "${event.confidenceScore.toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = confidenceColor
                    )
                    if (event.userResponded) {
                        Icon(Icons.Default.CheckCircle, "Responded",
                            Modifier.size(16.dp), tint = ConfidenceHigh)
                    }
                }
            }

            // Signal chips
            if (event.triggeredSignals.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    event.triggeredSignals.forEach { signal ->
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    signalShortName(signal),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

private val previewEvents = listOf(
    ExitEvent(id = 1, timestamp = System.currentTimeMillis() - 3_600_000,
        confidenceScore = 90f, triggeredSignals = listOf(ExitSignalType.WIFI_DISCONNECTED, ExitSignalType.MOTION_WALKING, ExitSignalType.SCREEN_UNLOCKED),
        userResponded = true),
    ExitEvent(id = 2, timestamp = System.currentTimeMillis() - 86_400_000,
        confidenceScore = 72f, triggeredSignals = listOf(ExitSignalType.WIFI_DISCONNECTED, ExitSignalType.TIME_WINDOW_MATCH),
        userResponded = false),
    ExitEvent(id = 3, timestamp = System.currentTimeMillis() - 172_800_000,
        confidenceScore = 45f, triggeredSignals = listOf(ExitSignalType.MOTION_WALKING),
        userResponded = false),
)

@Preview(name = "Exit event — high confidence", showBackground = true, widthDp = 360)
@Composable
private fun PreviewExitEventCardHigh() {
    ExitSenseTheme { ExitEventCard(previewEvents[0]) }
}

@Preview(name = "Exit event — medium confidence", showBackground = true, widthDp = 360)
@Composable
private fun PreviewExitEventCardMedium() {
    ExitSenseTheme { ExitEventCard(previewEvents[1]) }
}

@Preview(name = "History — with events", showBackground = true, device = Devices.PIXEL_5)
@Composable
private fun PreviewHistoryList() {
    ExitSenseTheme {
        Scaffold(topBar = { ExitSenseTopBar(title = "Exit History", onNavigateBack = {}) }) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(previewEvents, key = { it.id }) { ExitEventCard(it) }
            }
        }
    }
}

@Preview(name = "History — empty", showBackground = true, device = Devices.PIXEL_5)
@Composable
private fun PreviewHistoryEmpty() {
    ExitSenseTheme {
        Scaffold(topBar = { ExitSenseTopBar(title = "Exit History", onNavigateBack = {}) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyStateCard(message = "No exit events recorded yet.\nThe app will log events when it detects you leaving home.",
                    icon = { Icon(Icons.Default.History, null, Modifier.size(48.dp)) })
            }
        }
    }
}

private fun signalShortName(signal: ExitSignalType) = when (signal) {
    ExitSignalType.WIFI_DISCONNECTED -> "Wi-Fi off"
    ExitSignalType.WIFI_CONNECTED_HOME -> "At home"
    ExitSignalType.MOTION_WALKING -> "Walking"
    ExitSignalType.MOTION_RUNNING -> "Running"
    ExitSignalType.MOTION_DRIVING -> "Driving"
    ExitSignalType.SCREEN_UNLOCKED -> "Unlocked"
    ExitSignalType.TIME_WINDOW_MATCH -> "Schedule"
    ExitSignalType.BAROMETER_DESCENT -> "Descent"
    ExitSignalType.STEP_COUNT -> "Steps"
    ExitSignalType.CHARGER_UNPLUGGED -> "Unplugged"
    ExitSignalType.AMBIENT_LIGHT -> "Outdoor"
}
