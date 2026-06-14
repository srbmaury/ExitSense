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

        // Group events by calendar day for sticky headers
        val grouped = remember(state.events) {
            state.events.groupBy { event ->
                val cal = Calendar.getInstance().apply { timeInMillis = event.timestamp }
                // Key: year * 1000 + dayOfYear — unique per calendar day
                cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
            }.entries.toList()
        }

        val today = remember {
            Calendar.getInstance().let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
        }
        val yesterday = remember {
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                .let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
        }
        val headerFmt = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            grouped.forEach { (dayKey, events) ->
                // Sticky date header
                stickyHeader(key = "header_$dayKey") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Text(
                            text = when (dayKey) {
                                today -> "Today"
                                yesterday -> "Yesterday"
                                else -> headerFmt.format(Date(events.first().timestamp))
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                items(events, key = { it.id }) { event ->
                    ExitEventCard(
                        event = event,
                        profileName = event.profileId?.let { state.profileNames[it] },
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExitEventCard(
    event: ExitEvent,
    profileName: String? = null,
    modifier: Modifier = Modifier
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val confidenceColor = when {
        event.confidenceScore >= 70f -> ConfidenceHigh
        event.confidenceScore >= 40f -> ConfidenceMedium
        else -> ConfidenceLow
    }

    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        timeFmt.format(Date(event.timestamp)),
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (profileName != null) {
                        Text(
                            profileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                        Icon(Icons.Default.CheckCircle, "Confirmed",
                            Modifier.size(16.dp), tint = ConfidenceHigh)
                    }
                }
            }

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
                                Text(signalShortName(signal), style = MaterialTheme.typography.labelSmall)
                            }
                        )
                    }
                }
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

// ── Previews ─────────────────────────────────────────────────────────────────

private val previewEvents = listOf(
    ExitEvent(id = 1, timestamp = System.currentTimeMillis() - 3_600_000,
        confidenceScore = 90f,
        triggeredSignals = listOf(ExitSignalType.WIFI_DISCONNECTED, ExitSignalType.MOTION_WALKING),
        profileId = 1L, userResponded = true),
    ExitEvent(id = 2, timestamp = System.currentTimeMillis() - 86_400_000,
        confidenceScore = 72f,
        triggeredSignals = listOf(ExitSignalType.WIFI_DISCONNECTED, ExitSignalType.TIME_WINDOW_MATCH),
        profileId = 2L, userResponded = false),
    ExitEvent(id = 3, timestamp = System.currentTimeMillis() - 172_800_000,
        confidenceScore = 45f,
        triggeredSignals = listOf(ExitSignalType.MOTION_WALKING),
        profileId = 1L, userResponded = false),
)

@Preview(name = "Exit event card — high confidence", showBackground = true, widthDp = 360)
@Composable
private fun PreviewExitEventCardHigh() {
    ExitSenseTheme { ExitEventCard(previewEvents[0], profileName = "Office") }
}

@Preview(name = "Exit event card — medium confidence", showBackground = true, widthDp = 360)
@Composable
private fun PreviewExitEventCardMedium() {
    ExitSenseTheme { ExitEventCard(previewEvents[1], profileName = "Gym") }
}
