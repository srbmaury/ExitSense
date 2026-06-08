package com.exitsense.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exitsense.app.presentation.components.ExitSenseTopBar
import com.exitsense.app.presentation.components.LoadingScreen
import com.exitsense.app.presentation.theme.ExitSenseTheme
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { ExitSenseTopBar(title = "Settings", onNavigateBack = onNavigateBack) }
    ) { padding ->

        if (state.isLoading) { LoadingScreen(); return@Scaffold }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item { SectionHeader("Wi-Fi Detection") }
            item {
                WifiSsidSetting(
                    currentSsid = state.preferences.homeWifiSsid,
                    onSsidChanged = viewModel::updateHomeWifi
                )
            }

            item { Spacer(Modifier.height(8.dp)); SectionHeader("Floor Detection") }
            item {
                FloorSetting(
                    currentFloor = state.preferences.homeFloor,
                    onFloorChanged = viewModel::updateHomeFloor
                )
            }

            item { Spacer(Modifier.height(8.dp)); SectionHeader("Notifications") }
            item {
                ToggleSetting(
                    label = "Enable Notifications",
                    description = "Show exit reminders when leaving home",
                    checked = state.preferences.notificationsEnabled,
                    onCheckedChange = viewModel::updateNotificationsEnabled
                )
            }
            item {
                SliderSetting(
                    label = "Snooze Duration",
                    description = "How long to wait before re-showing the reminder after you tap 'Snooze' on the notification",
                    value = state.preferences.reminderSnoozeMinutes.toFloat(),
                    valueRange = 1f..15f,
                    steps = 13,
                    valueLabel = "${state.preferences.reminderSnoozeMinutes} min",
                    onValueChange = { viewModel.updateSnoozeMinutes(it.roundToInt()) }
                )
            }

            item { Spacer(Modifier.height(8.dp)); SectionHeader("Detection Sensitivity") }
            item {
                SliderSetting(
                    label = "Confidence Threshold",
                    description = "Higher = fewer false alarms but may miss some exits",
                    value = state.preferences.exitConfidenceThreshold,
                    valueRange = 40f..100f,
                    steps = 11,
                    valueLabel = "${state.preferences.exitConfidenceThreshold.roundToInt()}%",
                    onValueChange = viewModel::updateConfidenceThreshold
                )
            }

            item { Spacer(Modifier.height(8.dp)); SectionHeader("About") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Smart Exit Reminder", style = MaterialTheme.typography.titleSmall)
                        Text("Version 1.0.0", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Works without GPS · All data stored locally",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
    )
}

@Composable
private fun WifiSsidSetting(currentSsid: String, onSsidChanged: (String) -> Unit) {
    var draft by remember(currentSsid) { mutableStateOf(currentSsid) }
    val focusManager = LocalFocusManager.current

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Wifi, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text("Home Wi-Fi Network", style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Network name (SSID)") },
                placeholder = { Text("e.g. MyHomeWifi") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    onSsidChanged(draft)
                    focusManager.clearFocus()
                }),
                trailingIcon = {
                    if (draft != currentSsid) {
                        IconButton(onClick = { onSsidChanged(draft); focusManager.clearFocus() }) {
                            Icon(Icons.Default.Check, "Save")
                        }
                    }
                }
            )
            Text(
                "Used to detect when you leave home. No location permission required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloorSetting(currentFloor: Int, onFloorChanged: (Int) -> Unit) {
    var draft by remember(currentFloor) { mutableStateOf(if (currentFloor > 4) currentFloor.toString() else "") }
    val focusManager = LocalFocusManager.current
    val quickFloors = listOf(0 to "G", 1 to "1", 2 to "2", 3 to "3", 4 to "4")

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Apartment, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text("Home Floor", style = MaterialTheme.typography.bodyLarge)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickFloors.forEach { (floor, label) ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = floor == currentFloor && draft.isEmpty(),
                        onClick = { draft = ""; onFloorChanged(floor) },
                        label = {
                            Text(
                                label,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { value ->
                    if (value.isEmpty() || value.all { it.isDigit() }) {
                        draft = value
                        value.toIntOrNull()?.let { onFloorChanged(it) }
                    }
                },
                label = { Text("Or enter any floor") },
                placeholder = { Text("5, 10, 15…") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (draft.isEmpty()) onFloorChanged(currentFloor)
                    focusManager.clearFocus()
                }),
                supportingText = {
                    val effective = draft.toIntOrNull() ?: currentFloor
                    Text(
                        if (effective == 0) "Ground floor — barometer not applicable"
                        else "Floor $effective — barometer descent detection enabled"
                    )
                }
            )
        }
    }
}

@Composable
private fun ToggleSetting(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    description: String? = null,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    onValueChange: (Float) -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(valueLabel, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(name = "Wi-Fi SSID setting", showBackground = true, widthDp = 360)
@Composable
private fun PreviewWifiSsidSetting() {
    ExitSenseTheme { WifiSsidSetting(currentSsid = "HomeNetwork_5G", onSsidChanged = {}) }
}

@Preview(name = "Floor setting — floor 2", showBackground = true, widthDp = 360)
@Composable
private fun PreviewFloorSetting() {
    ExitSenseTheme { FloorSetting(currentFloor = 2, onFloorChanged = {}) }
}

@Preview(name = "Toggle setting — on", showBackground = true, widthDp = 360)
@Composable
private fun PreviewToggleSetting() {
    ExitSenseTheme {
        ToggleSetting(label = "Enable Notifications",
            description = "Show exit reminders when leaving home", checked = true, onCheckedChange = {})
    }
}

@Preview(name = "Slider setting — confidence 70", showBackground = true, widthDp = 360)
@Composable
private fun PreviewSliderSetting() {
    ExitSenseTheme {
        SliderSetting(label = "Confidence Threshold",
            description = "Higher = fewer false alarms but may miss some exits",
            value = 70f, valueRange = 40f..100f, steps = 11, valueLabel = "70%", onValueChange = {})
    }
}

@Preview(name = "Settings screen — light", showBackground = true, device = Devices.PIXEL_5)
@Composable
private fun PreviewSettingsScreen() {
    ExitSenseTheme {
        Scaffold(topBar = { ExitSenseTopBar(title = "Settings", onNavigateBack = {}) }) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item { SectionHeader("Wi-Fi Detection") }
                item { WifiSsidSetting(currentSsid = "HomeNetwork_5G", onSsidChanged = {}) }
                item { Spacer(Modifier.height(8.dp)); SectionHeader("Floor Detection") }
                item { FloorSetting(currentFloor = 2, onFloorChanged = {}) }
                item { Spacer(Modifier.height(8.dp)); SectionHeader("Notifications") }
                item { ToggleSetting(label = "Enable Notifications",
                    description = "Show exit reminders when leaving home", checked = true, onCheckedChange = {}) }
                item { SliderSetting(label = "Snooze Duration", value = 5f,
                    valueRange = 1f..15f, steps = 13, valueLabel = "5 min", onValueChange = {}) }
                item { Spacer(Modifier.height(8.dp)); SectionHeader("Detection Sensitivity") }
                item { SliderSetting(label = "Confidence Threshold",
                    description = "Higher = fewer false alarms but may miss some exits",
                    value = 70f, valueRange = 40f..100f, steps = 11, valueLabel = "70%", onValueChange = {}) }
            }
        }
    }
}
