package com.exitsense.app.presentation.home

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exitsense.app.domain.model.ExitEvent
import com.exitsense.app.domain.model.ExitSignalType
import com.exitsense.app.domain.model.MotionType
import com.exitsense.app.domain.model.ReminderItem
import com.exitsense.app.domain.model.ReminderProfile
import com.exitsense.app.sensors.PressureData
import com.exitsense.app.presentation.components.*
import com.exitsense.app.presentation.theme.ConfidenceHigh
import com.exitsense.app.presentation.theme.ExitSenseTheme
import com.exitsense.app.rules.ExitDetectionResult
import com.exitsense.app.rules.ExitSignal
import com.exitsense.app.rules.matchesHomeWifiSsid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigateToProfiles: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToIntegrations: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            ExitSenseTopBar(
                title = "Smart Exit Reminder",
                actions = {
                    IconButton(onClick = onNavigateToIntegrations) {
                        Icon(Icons.Default.Extension, "Integrations")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::runManualDetection) {
                Icon(Icons.Default.Refresh, "Check Now")
            }
        }
    ) { padding ->

        if (state.isLoading) {
            LoadingScreen()
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Monitoring toggle card
            item {
                MonitoringCard(
                    isMonitoring = state.isMonitoring,
                    onStart = viewModel::startMonitoringService,
                    onStop = viewModel::stopMonitoringService
                )
            }

            // Live sensor status
            item {
                SensorStatusCard(
                    motion = state.currentMotion,
                    wifiConnected = state.wifiConnected,
                    wifiSsid = state.wifiSsid,
                    wifiNetworkId = state.wifiNetworkId,
                    homeWifiSsid = state.homeWifiSsid,
                    homeNetworkIds = state.homeNetworkIds,
                    pressureData = state.pressureData,
                    onCalibrateBaseline = viewModel::calibratePressureBaseline
                )
            }

            // Detection result card — only meaningful when at least one profile exists
            if (state.activeProfiles.isNotEmpty()) {
                state.detectionResult?.let { result ->
                    item {
                        DetectionResultCard(
                            result = result,
                            threshold = state.confidenceThreshold,
                            lastCheckedAt = state.detectionResultTime
                        )
                    }
                }
            }

            // Active profiles summary
            if (state.activeProfiles.isNotEmpty()) {
                item {
                    Text(
                        "Active Profiles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(state.activeProfiles) { profile ->
                    ProfileSummaryCard(profile.name, profile.items.size, profile.startTimeFormatted, profile.endTimeFormatted)
                }
            } else {
                item {
                    EmptyStateCard(
                        message = "No active profiles. Tap the profiles button to create one.",
                        icon = { Icon(Icons.Default.PersonAdd, null, Modifier.size(40.dp)) }
                    )
                }
            }

            // Recent history
            if (state.recentExitEvents.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent Events", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onNavigateToHistory) { Text("See all") }
                    }
                }
                items(state.recentExitEvents) { event ->
                    RecentEventRow(event)
                }
            }

            // Bottom action buttons
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToProfiles,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.List, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Profiles")
                    }
                    OutlinedButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.History, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("History")
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitoringCard(
    isMonitoring: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isMonitoring)
                ConfidenceHigh.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(isMonitoring, size = 12.dp)
                Column {
                    Text(
                        if (isMonitoring) "Monitoring Active" else "Monitoring Paused",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (isMonitoring) "Watching for exit signals" else "Tap to start",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = isMonitoring,
                onCheckedChange = { if (it) onStart() else onStop() }
            )
        }
    }
}

@Composable
private fun SensorStatusCard(
    motion: MotionType,
    wifiConnected: Boolean,
    wifiSsid: String?,
    wifiNetworkId: Int = -1,
    homeWifiSsid: String = "",
    homeNetworkIds: Set<Int> = emptySet(),
    pressureData: PressureData = PressureData(),
    onCalibrateBaseline: () -> Unit = {}
) {
    val ssidMatch = matchesHomeWifiSsid(homeWifiSsid, wifiSsid)
    val networkIdMatch = wifiNetworkId != -1 && homeNetworkIds.isNotEmpty() && wifiNetworkId in homeNetworkIds
    val onHome = wifiConnected && (ssidMatch || networkIdMatch)
    val wifiLabel = when {
        !wifiConnected -> "No Wi-Fi"
        onHome && wifiSsid != null -> "Home: $wifiSsid"
        onHome -> "Home Wi-Fi"           // matched by network ID; SSID not readable
        wifiSsid == null -> "Wi-Fi (name hidden)"
        else -> wifiSsid
    }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Live Sensors", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SuggestionChip(
                    onClick = {},
                    label = { Text("Motion: ${motion.name.lowercase().replaceFirstChar { it.uppercase() }}") },
                    icon = { Icon(Icons.Default.DirectionsWalk, null, Modifier.size(16.dp)) }
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text(wifiLabel) },
                    icon = { Icon(
                        if (wifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                        null, Modifier.size(16.dp)
                    )}
                )
            }
            if (wifiConnected && wifiSsid == null && !onHome) {
                Text(
                    "Wi-Fi name hidden — grant Wi-Fi Name Access permission in Settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (pressureData.isAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    if (pressureData.isDescending) "Descending ↓" else "Stable",
                                    color = if (pressureData.isDescending) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            icon = { Icon(Icons.Default.Air, null, Modifier.size(16.dp)) }
                        )
                        pressureData.currentPressure?.let { hPa ->
                            Text(
                                "%.1f hPa".format(hPa),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = onCalibrateBaseline) {
                        Text("Set as normal", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetectionResultCard(
    result: com.exitsense.app.rules.ExitDetectionResult,
    threshold: Float,
    lastCheckedAt: Long? = null
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (result.isExitDetected)
                ConfidenceHigh.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (result.isExitDetected) "Exit Detected!" else "No Exit Detected",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (result.isExitDetected) ConfidenceHigh else MaterialTheme.colorScheme.onSurface
                )
                if (lastCheckedAt != null) {
                    Text(
                        "Updated ${timeFmt.format(Date(lastCheckedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ConfidenceBar(confidence = result.confidenceScore, threshold = threshold)
            if (result.signals.isNotEmpty()) {
                Text("Active Signals", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    maxItemsInEachRow = 2
                ) {
                    result.signals.forEach { signal ->
                        SignalChip(
                            label = signal.type.displayName,
                            score = signal.score
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSummaryCard(name: String, itemCount: Int, startTime: String, endTime: String) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.WorkOutline, null, Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text("$itemCount items · $startTime – $endTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            StatusDot(true)
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

private val previewDetectionResult = ExitDetectionResult(
    confidenceScore = 90f,
    signals = listOf(
        ExitSignal(ExitSignalType.WIFI_DISCONNECTED, 50f, "Left home Wi-Fi"),
        ExitSignal(ExitSignalType.MOTION_WALKING, 20f, "Walking detected"),
        ExitSignal(ExitSignalType.SCREEN_UNLOCKED, 10f, "Screen recently unlocked"),
        ExitSignal(ExitSignalType.TIME_WINDOW_MATCH, 10f, "Schedule matched"),
    ),
    isExitDetected = true
)

private val previewProfiles = listOf(
    ReminderProfile(
        id = 1, name = "Office",
        items = listOf(
            ReminderItem(id = 1, profileId = 1, name = "Laptop"),
            ReminderItem(id = 2, profileId = 1, name = "Badge"),
            ReminderItem(id = 3, profileId = 1, name = "Keys"),
        )
    ),
    ReminderProfile(id = 2, name = "Gym", startTimeHour = 6, startTimeMinute = 0,
        items = listOf(ReminderItem(id = 4, profileId = 2, name = "Water bottle")))
)

@Preview(name = "Monitoring — active", showBackground = true, widthDp = 360)
@Composable
private fun PreviewMonitoringCardActive() {
    ExitSenseTheme { MonitoringCard(isMonitoring = true, onStart = {}, onStop = {}) }
}

@Preview(name = "Monitoring — paused", showBackground = true, widthDp = 360)
@Composable
private fun PreviewMonitoringCardPaused() {
    ExitSenseTheme { MonitoringCard(isMonitoring = false, onStart = {}, onStop = {}) }
}

@Preview(name = "Sensor status — walking, no wifi", showBackground = true, widthDp = 360)
@Composable
private fun PreviewSensorStatusCard() {
    ExitSenseTheme {
        SensorStatusCard(motion = MotionType.WALKING, wifiConnected = false, wifiSsid = null)
    }
}

@Preview(name = "Sensor status — home wifi", showBackground = true, widthDp = 360)
@Composable
private fun PreviewSensorStatusCardHome() {
    ExitSenseTheme {
        SensorStatusCard(motion = MotionType.STILL, wifiConnected = true, wifiSsid = "HomeNetwork_5G")
    }
}

@Preview(name = "Detection result — exit detected", showBackground = true, widthDp = 360)
@Composable
private fun PreviewDetectionResultCardHigh() {
    ExitSenseTheme { DetectionResultCard(result = previewDetectionResult, threshold = 70f) }
}

@Preview(name = "Detection result — below threshold", showBackground = true, widthDp = 360)
@Composable
private fun PreviewDetectionResultCardLow() {
    val lowResult = ExitDetectionResult(
        confidenceScore = 30f,
        signals = listOf(ExitSignal(ExitSignalType.MOTION_WALKING, 20f), ExitSignal(ExitSignalType.SCREEN_UNLOCKED, 10f)),
        isExitDetected = false
    )
    ExitSenseTheme { DetectionResultCard(result = lowResult, threshold = 70f) }
}

@Preview(name = "Profile summary card", showBackground = true, widthDp = 360)
@Composable
private fun PreviewProfileSummaryCard() {
    ExitSenseTheme { ProfileSummaryCard(name = "Office", itemCount = 5, startTime = "08:00", endTime = "10:00") }
}

@Preview(name = "Home screen — light", showBackground = true, device = Devices.PIXEL_5)
@Composable
private fun PreviewHomeScreenLight() {
    ExitSenseTheme {
        Scaffold(
            topBar = {
                ExitSenseTopBar(title = "Smart Exit Reminder",
                    actions = { IconButton(onClick = {}) { Icon(Icons.Default.Settings, "Settings") } })
            },
            floatingActionButton = {
                FloatingActionButton(onClick = {}) { Icon(Icons.Default.Refresh, "Check Now") }
            }
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { MonitoringCard(isMonitoring = true, onStart = {}, onStop = {}) }
                item { SensorStatusCard(motion = MotionType.WALKING, wifiConnected = false, wifiSsid = null) }
                item { DetectionResultCard(result = previewDetectionResult, threshold = 70f) }
                item { Text("Active Profiles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                items(previewProfiles) { p -> ProfileSummaryCard(p.name, p.items.size, p.startTimeFormatted, p.endTimeFormatted) }
            }
        }
    }
}

@Preview(name = "Home screen — dark", showBackground = true, device = Devices.PIXEL_5)
@Composable
private fun PreviewHomeScreenDark() {
    ExitSenseTheme(darkTheme = true) {
        Scaffold(
            topBar = {
                ExitSenseTopBar(title = "Smart Exit Reminder",
                    actions = { IconButton(onClick = {}) { Icon(Icons.Default.Settings, "Settings") } })
            },
            floatingActionButton = {
                FloatingActionButton(onClick = {}) { Icon(Icons.Default.Refresh, "Check Now") }
            }
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { MonitoringCard(isMonitoring = false, onStart = {}, onStop = {}) }
                item { SensorStatusCard(motion = MotionType.STILL, wifiConnected = true, wifiSsid = "HomeNetwork") }
            }
        }
    }
}

@Composable
private fun RecentEventRow(event: ExitEvent) {
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ExitToApp, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(
                        fmt.format(Date(event.timestamp)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Confidence: ${event.confidenceScore.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (event.userResponded) {
                Icon(Icons.Default.CheckCircle, "Responded", tint = ConfidenceHigh)
            }
        }
    }
}
