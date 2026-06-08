package com.exitsense.app.presentation.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onSetupComplete()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            // Progress indicator
            Spacer(Modifier.height(16.dp))
            SetupProgressBar(currentStep = state.currentStep)
            Spacer(Modifier.height(24.dp))

            Box(modifier = Modifier.weight(1f)) {
                when (state.currentStep) {
                    SetupStep.WIFI -> WifiSetupStep(
                        ssid = state.homeWifiSsid,
                        detectedSsid = state.detectedSsid,
                        onSsidChanged = viewModel::onWifiSsidChanged,
                        onUseDetected = viewModel::useDetectedSsid
                    )
                    SetupStep.FLOOR -> FloorSetupStep(
                        selectedFloor = state.homeFloor,
                        onFloorChanged = viewModel::onFloorChanged
                    )
                    SetupStep.PROFILES -> ProfilesSetupStep(
                        onCreateOfficeProfile = viewModel::createDefaultOfficeProfile
                    )
                    SetupStep.PERMISSIONS -> PermissionsSetupStep()
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
                if (state.currentStep != SetupStep.WIFI) {
                    OutlinedButton(
                        onClick = viewModel::previousStep,
                        modifier = Modifier.weight(1f)
                    ) { Text("Back") }
                }

                Button(
                    onClick = {
                        if (state.currentStep == SetupStep.PERMISSIONS) viewModel.finishSetup()
                        else viewModel.nextStep()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (state.currentStep == SetupStep.PERMISSIONS) "Get Started" else "Next")
                }
            }
        }
    }
}

@Composable
private fun SetupProgressBar(currentStep: SetupStep) {
    val steps = SetupStep.entries
    val currentIndex = steps.indexOf(currentStep)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        steps.forEachIndexed { index, _ ->
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

@Composable
private fun WifiSetupStep(
    ssid: String,
    detectedSsid: String?,
    onSsidChanged: (String) -> Unit,
    onUseDetected: () -> Unit
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StepHeader(
            icon = Icons.Default.Wifi,
            step = "Step 1 of 4",
            title = "Set Your Home Wi-Fi",
            subtitle = "The app uses Wi-Fi disconnection as the primary exit signal — no GPS needed."
        )

        if (detectedSsid != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = ConfidenceHigh.copy(alpha = 0.1f)
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Wifi, null, tint = ConfidenceHigh)
                        Text("Detected: $detectedSsid", style = MaterialTheme.typography.bodyLarge)
                    }
                    Button(
                        onClick = onUseDetected,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Use This Network") }
                }
            }
        }

        OutlinedTextField(
            value = ssid,
            onValueChange = onSsidChanged,
            label = { Text("Home Wi-Fi Name (SSID)") },
            placeholder = { Text("e.g. MyHomeNetwork") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text(
            "You can change this later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloorSetupStep(selectedFloor: Int, onFloorChanged: (Int) -> Unit) {
    var draft by remember(selectedFloor) { mutableStateOf(if (selectedFloor > 4) selectedFloor.toString() else "") }
    val focusManager = LocalFocusManager.current
    val quickFloors = listOf(0 to "G", 1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th")

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StepHeader(
            icon = Icons.Default.Apartment,
            step = "Step 2 of 4",
            title = "Home Floor",
            subtitle = "If your device has a barometer, the app can detect descent toward the exit."
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
private fun ProfilesSetupStep(onCreateOfficeProfile: () -> Unit) {
    var created by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StepHeader(
            icon = Icons.Default.List,
            step = "Step 3 of 4",
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
private fun PermissionsSetupStep() {
    val context = LocalContext.current
    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    var activityGranted by remember { mutableStateOf(false) }
    var notifyGranted by remember { mutableStateOf(false) }
    var batteryGranted by remember {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName))
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StepHeader(
            icon = Icons.Default.Security,
            step = "Step 4 of 4",
            title = "Grant Permissions",
            subtitle = "These permissions let the app detect motion and send notifications. Location is NOT required."
        )

        PermissionCard(
            icon = Icons.Default.DirectionsWalk,
            title = "Activity Recognition",
            description = "Detects walking, running, driving (no location data).",
            isGranted = activityGranted,
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    activityLauncher.launch(
                        arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)
                    )
                    activityGranted = true
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
                    activityLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    notifyGranted = true
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
                batteryGranted = true
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

@Preview(name = "Setup — step 1 Wi-Fi", showBackground = true, device = Devices.PIXEL_5)
@Composable
private fun PreviewSetupWifiStep() {
    ExitSenseTheme {
        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(16.dp))
                SetupProgressBar(currentStep = SetupStep.WIFI)
                Spacer(Modifier.height(24.dp))
                Box(Modifier.weight(1f)) {
                    WifiSetupStep(ssid = "", detectedSsid = "HomeNetwork_5G", onSsidChanged = {}, onUseDetected = {})
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Button(onClick = {}, Modifier.fillMaxWidth()) { Text("Next") }
                }
            }
        }
    }
}

@Preview(name = "Setup — step 2 Floor", showBackground = true, device = Devices.PIXEL_5)
@Composable
private fun PreviewSetupFloorStep() {
    ExitSenseTheme {
        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(16.dp))
                SetupProgressBar(currentStep = SetupStep.FLOOR)
                Spacer(Modifier.height(24.dp))
                Box(Modifier.weight(1f)) {
                    FloorSetupStep(selectedFloor = 3, onFloorChanged = {})
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {}, Modifier.weight(1f)) { Text("Back") }
                    Button(onClick = {}, Modifier.weight(1f)) { Text("Next") }
                }
            }
        }
    }
}

@Preview(name = "Setup — step 3 Profiles", showBackground = true, device = Devices.PIXEL_5)
@Composable
private fun PreviewSetupProfilesStep() {
    ExitSenseTheme {
        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(16.dp))
                SetupProgressBar(currentStep = SetupStep.PROFILES)
                Spacer(Modifier.height(24.dp))
                Box(Modifier.weight(1f)) {
                    ProfilesSetupStep(onCreateOfficeProfile = {})
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {}, Modifier.weight(1f)) { Text("Back") }
                    Button(onClick = {}, Modifier.weight(1f)) { Text("Next") }
                }
            }
        }
    }
}

@Preview(name = "Setup — step 4 Permissions", showBackground = true, device = Devices.PIXEL_5)
@Composable
private fun PreviewSetupPermissionsStep() {
    ExitSenseTheme {
        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(16.dp))
                SetupProgressBar(currentStep = SetupStep.PERMISSIONS)
                Spacer(Modifier.height(24.dp))
                Box(Modifier.weight(1f)) {
                    PermissionsSetupStep()
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {}, Modifier.weight(1f)) { Text("Back") }
                    Button(onClick = {}, Modifier.weight(1f)) { Text("Get Started") }
                }
            }
        }
    }
}
