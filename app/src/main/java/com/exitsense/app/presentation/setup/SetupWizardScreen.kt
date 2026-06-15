package com.exitsense.app.presentation.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exitsense.app.presentation.theme.ConfidenceHigh
import com.exitsense.app.presentation.theme.ExitSenseTheme

@Composable
fun SetupWizardScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupWizardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importBackup(it) } }

    // Keys on both isComplete and importMessage so that if the user clicks "Get Started"
    // while the import snackbar is still showing, navigation fires once the snackbar clears.
    LaunchedEffect(state.isComplete, state.importMessage) {
        if (state.isComplete && state.importMessage == null) onSetupComplete()
    }

    state.importMessage?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearImportMessage()
        }
    }

    val visibleSteps = if (state.hasBarometer) SetupStep.entries
                       else SetupStep.entries.filter { it != SetupStep.FLOOR }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            // Progress indicator
            Spacer(Modifier.height(16.dp))
            SetupProgressBar(currentStep = state.currentStep, visibleSteps = visibleSteps)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) {
                    Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import Backup")
                }
            }
            Spacer(Modifier.height(24.dp))

            val totalSteps = visibleSteps.size
            val stepNum = visibleSteps.indexOf(state.currentStep) + 1

            Box(modifier = Modifier.weight(1f)) {
                when (state.currentStep) {
                    SetupStep.WIFI -> WifiSetupStep(
                        ssid = state.homeWifiSsid,
                        detectedSsid = state.detectedSsid,
                        availableNetworks = state.availableNetworks,
                        stepLabel = "Step $stepNum of $totalSteps",
                        onSsidChanged = viewModel::onWifiSsidChanged,
                        onUseDetected = viewModel::useDetectedSsid,
                        onScanClicked = viewModel::triggerScan
                    )
                    SetupStep.FLOOR -> FloorSetupStep(
                        selectedFloor = state.homeFloor,
                        stepLabel = "Step $stepNum of $totalSteps",
                        onFloorChanged = viewModel::onFloorChanged
                    )
                    SetupStep.PROFILES -> ProfilesSetupStep(
                        stepLabel = "Step $stepNum of $totalSteps",
                        onCreateOfficeProfile = viewModel::createDefaultOfficeProfile
                    )
                    SetupStep.PERMISSIONS -> PermissionsSetupStep(stepLabel = "Step $stepNum of $totalSteps")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.currentStep != visibleSteps.first()) {
                    OutlinedButton(
                        onClick = viewModel::previousStep,
                        modifier = Modifier.weight(1f)
                    ) { Text("Back") }
                }

                Button(
                    onClick = {
                        if (state.currentStep == SetupStep.WIFI) viewModel.finishSetup()
                        else viewModel.nextStep()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (state.currentStep == SetupStep.WIFI) "Get Started" else "Next")
                }
            }
        }
    }
}

@Composable
private fun SetupProgressBar(currentStep: SetupStep, visibleSteps: List<SetupStep>) {
    val currentIndex = visibleSteps.indexOf(currentStep)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        visibleSteps.forEachIndexed { index, _ ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
            ) {
                LinearProgressIndicator(
                    progress = { if (index <= currentIndex) 1f else 0f },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WifiSetupStep(
    ssid: String,
    detectedSsid: String?,
    availableNetworks: List<String>,
    stepLabel: String,
    onSsidChanged: (String) -> Unit,
    onUseDetected: () -> Unit,
    onScanClicked: () -> Unit
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StepHeader(
            icon = Icons.Default.Wifi,
            step = stepLabel,
            title = "Set Your Home Wi-Fi",
            subtitle = "Optional — skip for now and set it later in Settings. Wi-Fi disconnection is the primary exit signal; the app can also match by network ID without a name."
        )

        // ── Network picker ──────────────────────────────────────────────────
        Card {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (availableNetworks.isEmpty()) "Visible Networks" else "Visible Networks (${availableNetworks.size})",
                        style = MaterialTheme.typography.labelLarge
                    )
                    IconButton(onClick = onScanClicked) {
                        Icon(Icons.Default.Refresh, "Scan again", Modifier.size(20.dp))
                    }
                }

                if (availableNetworks.isEmpty()) {
                    Text(
                        "No networks found. Tap ↻ to scan, or type your Wi-Fi name below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        availableNetworks.forEach { network ->
                            val isSelected = ssid.split(",").map { it.trim() }.contains(network)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    // Toggle: add or remove this network from the comma list
                                    val current = ssid.split(",")
                                        .map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                                    if (isSelected) current.remove(network) else current.add(network)
                                    onSsidChanged(current.joinToString(", "))
                                },
                                label = { Text(network, style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = if (network == detectedSsid) {
                                    { Icon(Icons.Default.Wifi, null, Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }
                }

                // Highlight currently connected network if it didn't appear in scan results
                if (detectedSsid != null && !availableNetworks.contains(detectedSsid)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Wifi, null, tint = ConfidenceHigh, modifier = Modifier.size(16.dp))
                        Text("Connected: $detectedSsid", style = MaterialTheme.typography.bodySmall, color = ConfidenceHigh)
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = onUseDetected,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) { Text("Use") }
                    }
                }
            }
        }

        // ── Custom / multi-band text entry ──────────────────────────────────
        OutlinedTextField(
            value = ssid,
            onValueChange = onSsidChanged,
            label = { Text("Wi-Fi Name (SSID)") },
            placeholder = { Text("e.g. HomeWifi or HomeWifi, HomeWifi_5G") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = {
                Text("Optional. Tap chips above or type here. Separate multiple bands with commas.")
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloorSetupStep(selectedFloor: Int, stepLabel: String, onFloorChanged: (Int) -> Unit) {
    var draft by remember(selectedFloor) { mutableStateOf(if (selectedFloor > 4) selectedFloor.toString() else "") }
    val focusManager = LocalFocusManager.current
    val quickFloors = listOf(0 to "G", 1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th")

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StepHeader(
            icon = Icons.Default.Apartment,
            step = stepLabel,
            title = "Home Floor",
            subtitle = "Your device has a barometer — the app can detect floor descent toward the exit."
        )
        Text("Which floor do you live on?", style = MaterialTheme.typography.bodyLarge)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickFloors.forEach { (floor, label) ->
                FilterChip(
                    modifier = Modifier.weight(1f),
                    selected = floor == selectedFloor && draft.isEmpty(),
                    onClick = { draft = ""; onFloorChanged(floor) },
                    label = {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
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
            label = { Text("Higher floor? Enter number") },
            placeholder = { Text("5, 10, 20…") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (draft.isEmpty()) onFloorChanged(selectedFloor)
                focusManager.clearFocus()
            })
        )
        val effective = draft.toIntOrNull() ?: selectedFloor
        Text(
            if (effective == 0) "Ground floor — barometer detection not applicable."
            else "Floor $effective selected. Barometer descent detection enabled.",
            style = MaterialTheme.typography.bodySmall,
            color = if (effective == 0) MaterialTheme.colorScheme.onSurfaceVariant else ConfidenceHigh
        )
    }
}

@Composable
private fun ProfilesSetupStep(stepLabel: String, onCreateOfficeProfile: () -> Unit) {
    var created by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StepHeader(
            icon = Icons.Default.List,
            step = stepLabel,
            title = "Create Your First Profile",
            subtitle = "Profiles define what items to check and when. You can add more later."
        )
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Office Profile (Suggested)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Monday–Friday, 8:00–10:00\nItems: Office ID, Laptop, Wallet, Keys, Charger",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { onCreateOfficeProfile(); created = true },
                    enabled = !created,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (created) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Created!")
                    } else {
                        Text("Create Office Profile")
                    }
                }
            }
        }
        Text(
            "You can create custom profiles from the Profiles screen after setup.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionsSetupStep(stepLabel: String) {
    val context = LocalContext.current

    var activityGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var notifyGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var wifiNameGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var batteryGranted by remember {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName))
    }

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> activityGranted = results[Manifest.permission.ACTIVITY_RECOGNITION] == true }

    val notifyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> notifyGranted = results[Manifest.permission.POST_NOTIFICATIONS] == true }

    val wifiNameLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        wifiNameGranted =
            results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        batteryGranted = pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StepHeader(
            icon = Icons.Default.Security,
            step = stepLabel,
            title = "Grant Permissions",
            subtitle = "These permissions enable motion detection, notifications, and Wi-Fi name matching."
        )

        PermissionCard(
            icon = Icons.Default.DirectionsWalk,
            title = "Activity Recognition",
            description = "Detects walking, running, driving (no location data).",
            isGranted = activityGranted,
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    activityLauncher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
                } else {
                    activityGranted = true
                }
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                icon = Icons.Default.Notifications,
                title = "Post Notifications",
                description = "Required to show exit reminders on Android 13+.",
                isGranted = notifyGranted,
                onRequest = {
                    notifyLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PermissionCard(
                icon = Icons.Default.Wifi,
                title = "Wi-Fi Name Access",
                description = "Allows reading your Wi-Fi name to detect when you leave home. Android requires location permission for this — your location is never stored or shared.",
                isGranted = wifiNameGranted,
                onRequest = {
                    wifiNameLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )
        }

        PermissionCard(
            icon = Icons.Default.BatterySaver,
            title = "Battery Optimization",
            description = "Keeps background detection running reliably. Without this the system may pause the app.",
            isGranted = batteryGranted,
            onRequest = {
                batteryLauncher.launch(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        )
    }
}

@Composable
private fun PermissionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted)
                ConfidenceHigh.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface
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
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon, null,
                    tint = if (isGranted) ConfidenceHigh else MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isGranted) {
                Icon(Icons.Default.CheckCircle, "Granted", tint = ConfidenceHigh)
            } else {
                TextButton(onClick = onRequest) { Text("Allow") }
            }
        }
    }
}

@Composable
private fun StepHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    step: String,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(step, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(name = "Setup — step 4 Wi-Fi", showBackground = true, device = Devices.PIXEL_5)
@Composable
private fun PreviewSetupWifiStep() {
    ExitSenseTheme {
        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(16.dp))
                SetupProgressBar(currentStep = SetupStep.WIFI, visibleSteps = SetupStep.entries)
                Spacer(Modifier.height(24.dp))
                Box(Modifier.weight(1f)) {
                    WifiSetupStep(ssid = "", detectedSsid = "HomeNetwork_5G", availableNetworks = listOf("HomeNetwork_5G", "HomeNetwork_2G", "Neighbor"), stepLabel = "Step 4 of 4", onSsidChanged = {}, onUseDetected = {}, onScanClicked = {})
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {}, Modifier.weight(1f)) { Text("Back") }
                    Button(onClick = {}, Modifier.weight(1f)) { Text("Get Started") }
                }
            }
        }
    }
}

@Preview(name = "Setup — step 1 Floor", showBackground = true, device = Devices.PIXEL_5)
@Composable
private fun PreviewSetupFloorStep() {
    ExitSenseTheme {
        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(16.dp))
                SetupProgressBar(currentStep = SetupStep.FLOOR, visibleSteps = SetupStep.entries)
                Spacer(Modifier.height(24.dp))
                Box(Modifier.weight(1f)) {
                    FloorSetupStep(selectedFloor = 3, stepLabel = "Step 1 of 4", onFloorChanged = {})
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {}, Modifier.weight(1f)) { Text("Back") }
                    Button(onClick = {}, Modifier.weight(1f)) { Text("Next") }
                }
            }
        }
    }
}

@Preview(name = "Setup — step 2 Profiles", showBackground = true, device = Devices.PIXEL_5)
@Composable
private fun PreviewSetupProfilesStep() {
    ExitSenseTheme {
        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(16.dp))
                SetupProgressBar(currentStep = SetupStep.PROFILES, visibleSteps = SetupStep.entries)
                Spacer(Modifier.height(24.dp))
                Box(Modifier.weight(1f)) {
                    ProfilesSetupStep(stepLabel = "Step 2 of 4", onCreateOfficeProfile = {})
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {}, Modifier.weight(1f)) { Text("Back") }
                    Button(onClick = {}, Modifier.weight(1f)) { Text("Next") }
                }
            }
        }
    }
}

@Preview(name = "Setup — step 3 Permissions", showBackground = true, device = Devices.PIXEL_5)
@Composable
private fun PreviewSetupPermissionsStep() {
    ExitSenseTheme {
        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(16.dp))
                SetupProgressBar(currentStep = SetupStep.PERMISSIONS, visibleSteps = SetupStep.entries)
                Spacer(Modifier.height(24.dp))
                Box(Modifier.weight(1f)) {
                    PermissionsSetupStep(stepLabel = "Step 3 of 4")
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {}, Modifier.weight(1f)) { Text("Back") }
                    Button(onClick = {}, Modifier.weight(1f)) { Text("Next") }
                }
            }
        }
    }
}
