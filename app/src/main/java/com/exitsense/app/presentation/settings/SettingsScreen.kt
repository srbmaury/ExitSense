package com.exitsense.app.presentation.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exitsense.app.presentation.components.ExitSenseTopBar
import com.exitsense.app.presentation.components.LoadingScreen
import com.exitsense.app.presentation.theme.ExitSenseTheme
import com.exitsense.app.rules.matchesHomeWifiSsid
import com.exitsense.app.rules.parseHomeWifiSsids
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var permissionRefreshTrigger by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshWifiSsid()
                permissionRefreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // File picker launchers for backup
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportProfiles(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importProfiles(it) } }

    state.exportMessage?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearExportMessage()
        }
    }
    state.importMessage?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearImportMessage()
        }
    }

    Scaffold(
        topBar = { ExitSenseTopBar(title = "Settings", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
            // ── Wi-Fi Detection ──────────────────────────────────────────────
            item { SectionHeader("Wi-Fi Detection") }
            item { WifiNamePermissionCard(permissionRefreshTrigger = permissionRefreshTrigger) }
            item {
                WifiSsidSetting(
                    currentSsid = state.preferences.homeWifiSsid,
                    currentWifiSsid = state.currentWifiSsid,
                    currentNetworkId = state.currentNetworkId,
                    isWifiConnected = state.isWifiConnected,
                    savedNetworkIds = state.preferences.homeNetworkIds,
                    onSsidChanged = viewModel::updateHomeWifi,
                    onAddNetworkId = viewModel::addHomeNetworkId,
                    onRemoveNetworkId = viewModel::removeHomeNetworkId
                )
            }
            item {
                ResetHomeNetworkCard(onReset = viewModel::clearHomeNetworkData)
            }

            // ── Floor Detection ──────────────────────────────────────────────
            if (state.hasBarometer) {
                item { Spacer(Modifier.height(8.dp)); SectionHeader("Floor Detection") }
                item {
                    FloorSetting(
                        currentFloor = state.preferences.homeFloor,
                        onFloorChanged = viewModel::updateHomeFloor
                    )
                }
            }

            // ── Notifications ─────────────────────────────────────────────────
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
                    description = "How long to wait before re-showing the reminder",
                    value = state.preferences.reminderSnoozeMinutes.toFloat(),
                    valueRange = 1f..15f,
                    steps = 13,
                    valueLabel = "${state.preferences.reminderSnoozeMinutes} min",
                    onValueChange = { viewModel.updateSnoozeMinutes(it.roundToInt()) }
                )
            }
            item {
                QuietHoursSetting(
                    enabled = state.preferences.quietHoursEnabled,
                    startMinute = state.preferences.quietHoursStartMinute,
                    endMinute = state.preferences.quietHoursEndMinute,
                    onEnabledChanged = viewModel::updateQuietHoursEnabled,
                    onStartChanged = viewModel::updateQuietHoursStart,
                    onEndChanged = viewModel::updateQuietHoursEnd
                )
            }

            // ── Detection Sensitivity ─────────────────────────────────────────
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

            // ── Data / Backup ─────────────────────────────────────────────────
            item { Spacer(Modifier.height(8.dp)); SectionHeader("Data") }
            item {
                BackupSetting(
                    exportMessage = state.exportMessage,
                    importMessage = state.importMessage,
                    onExport = {
                        val ts = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            .format(java.util.Date())
                        exportLauncher.launch("exitsense_profiles_$ts.json")
                    },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) }
                )
            }

            // ── About ─────────────────────────────────────────────────────────
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WifiSsidSetting(
    currentSsid: String,
    currentWifiSsid: String?,
    currentNetworkId: Int = -1,
    isWifiConnected: Boolean,
    savedNetworkIds: Set<Int> = emptySet(),
    onSsidChanged: (String) -> Unit,
    onAddNetworkId: (Int) -> Unit = {},
    onRemoveNetworkId: (Int) -> Unit = {}
) {
    var draft by remember(currentSsid) { mutableStateOf(currentSsid) }
    val focusManager = LocalFocusManager.current
    val matches = matchesHomeWifiSsid(currentSsid, currentWifiSsid)

    fun withSavedSsid(ssid: String): String {
        val saved = parseHomeWifiSsids(currentSsid)
        if (saved.any { it.equals(ssid, ignoreCase = true) }) return currentSsid
        return (saved + ssid.trim()).joinToString(", ")
    }

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

            when {
                currentWifiSsid != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Now connected: $currentWifiSsid",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (matches) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!matches) {
                            TextButton(onClick = {
                                val updated = withSavedSsid(currentWifiSsid)
                                draft = updated
                                onSsidChanged(updated)
                                if (currentNetworkId != -1) onAddNetworkId(currentNetworkId)
                            }) { Text("Use This") }
                        } else {
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                isWifiConnected && currentNetworkId != -1 -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Connected · network detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onAddNetworkId(currentNetworkId) }) {
                            Text("Use This")
                        }
                    }
                }
                isWifiConnected -> {
                    Text("Connected · detecting network…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    Text("Not connected to Wi-Fi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Saved home network name (SSID)") },
                placeholder = { Text("e.g. MyHomeWifi") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    onSsidChanged(draft); focusManager.clearFocus()
                }),
                trailingIcon = {
                    if (draft != currentSsid) {
                        IconButton(onClick = { onSsidChanged(draft); focusManager.clearFocus() }) {
                            Icon(Icons.Default.Check, "Save")
                        }
                    }
                }
            )

            // Saved network ID chips
            if (savedNetworkIds.isNotEmpty()) {
                Text("Trusted networks (no location permission needed):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    savedNetworkIds.sorted().forEachIndexed { i, id ->
                        val isCurrentlyConnected = id == currentNetworkId
                        InputChip(
                            selected = isCurrentlyConnected,
                            onClick = {},
                            label = { Text("Network ${i + 1}${if (isCurrentlyConnected) " (now)" else ""}") },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onRemoveNetworkId(id) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(Icons.Default.Close, "Remove", Modifier.size(14.dp))
                                }
                            }
                        )
                    }
                }
            }

            Text(
                "Connect to your home Wi-Fi and tap 'Use This' to save it. Multiple networks are supported for mesh routers.",
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
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Apartment, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text("Home Floor", style = MaterialTheme.typography.bodyLarge)
            }
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quickFloors.forEach { (floor, label) ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = floor == currentFloor && draft.isEmpty(),
                        onClick = { draft = ""; onFloorChanged(floor) },
                        label = { Text(label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
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
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (draft.isEmpty()) onFloorChanged(currentFloor); focusManager.clearFocus()
                }),
                supportingText = {
                    val effective = draft.toIntOrNull() ?: currentFloor
                    Text(if (effective == 0) "Ground floor — barometer not applicable"
                         else "Floor $effective — barometer descent detection enabled")
                }
            )
        }
    }
}

@Composable
private fun QuietHoursSetting(
    enabled: Boolean,
    startMinute: Int,
    endMinute: Int,
    onEnabledChanged: (Boolean) -> Unit,
    onStartChanged: (Int) -> Unit,
    onEndChanged: (Int) -> Unit
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    if (showStartPicker) {
        QuietTimePickerDialog(
            initialHour = startMinute / 60,
            initialMinute = startMinute % 60,
            onConfirm = { h, m -> onStartChanged(h * 60 + m); showStartPicker = false },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        QuietTimePickerDialog(
            initialHour = endMinute / 60,
            initialMinute = endMinute % 60,
            onConfirm = { h, m -> onEndChanged(h * 60 + m); showEndPicker = false },
            onDismiss = { showEndPicker = false }
        )
    }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bedtime, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Quiet Hours", style = MaterialTheme.typography.bodyLarge)
                        Text("No notifications during this window",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChanged)
            }
            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showStartPicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.NightsStay, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("From %02d:%02d".format(startMinute / 60, startMinute % 60))
                    }
                    OutlinedButton(
                        onClick = { showEndPicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.WbSunny, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Until %02d:%02d".format(endMinute / 60, endMinute % 60))
                    }
                }
                val overnight = startMinute > endMinute
                Text(
                    if (overnight)
                        "Quiet from %02d:%02d to %02d:%02d (overnight)".format(
                            startMinute / 60, startMinute % 60, endMinute / 60, endMinute % 60)
                    else
                        "Quiet from %02d:%02d to %02d:%02d".format(
                            startMinute / 60, startMinute % 60, endMinute / 60, endMinute % 60),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuietTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Time") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BackupSetting(
    exportMessage: String?,
    importMessage: String?,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CloudDownload, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text("Full Backup", style = MaterialTheme.typography.bodyLarge)
            }
            Text("Saves everything — Wi-Fi settings, confidence threshold, quiet hours, snooze, all profiles and their learned item priorities — as a single JSON file.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Upload, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export")
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import")
                }
            }
            exportMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("Export failed")) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary)
            }
            importMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("Import failed")) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun WifiNamePermissionCard(permissionRefreshTrigger: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

    val context = LocalContext.current
    var granted by remember(permissionRefreshTrigger) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                  results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    if (!granted) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.WifiOff, null, tint = MaterialTheme.colorScheme.error)
                Column(Modifier.weight(1f)) {
                    Text(
                        "Wi-Fi Name Access not granted",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "The app can't read your Wi-Fi name to detect home. Tap Allow to fix.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
                TextButton(
                    onClick = {
                        launcher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    }
                ) { Text("Allow") }
            }
        }
    }
}

@Composable
private fun ResetHomeNetworkCard(onReset: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Reset Home Network?") },
            text = {
                Text("This clears your saved Wi-Fi name and all saved network IDs. The app won't recognise your home network until you re-configure it.")
            },
            confirmButton = {
                TextButton(
                    onClick = { onReset(); showDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Reset Home Network", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "Clear saved Wi-Fi name and all network IDs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = { showDialog = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Reset") }
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
            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(valueLabel, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(value = value, onValueChange = onValueChange, valueRange = valueRange,
                steps = steps, modifier = Modifier.fillMaxWidth())
        }
    }
}
