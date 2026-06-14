package com.exitsense.app.presentation.integrations

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exitsense.app.presentation.components.ExitSenseTopBar
import com.exitsense.app.presentation.theme.ExitSenseTheme

@Composable
fun IntegrationsScreen(
    onNavigateBack: () -> Unit,
    viewModel: IntegrationsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCalendarPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted =
            results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) viewModel.setWeatherEnabled(true)
        else viewModel.setWeatherEnabled(false)
    }
    val calendarPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCalendarPermission = granted
        if (granted) {
            viewModel.setCalendarEnabled(true)
            viewModel.refreshCalendarEvents()
        } else {
            viewModel.setCalendarEnabled(false)
        }
    }

    Scaffold(
        topBar = { ExitSenseTopBar(title = "Integrations", onNavigateBack = onNavigateBack) }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Connect services to enrich exit reminders with context.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            item {
                IntegrationCard(
                    icon = Icons.Default.WbSunny,
                    title = "Weather Alerts",
                    description = "Adds an umbrella reminder when rain is forecast for the next 2 hours. Uses Open-Meteo — free, no account required.",
                    enabled = state.weatherEnabled,
                    hasPermission = hasLocationPermission,
                    permissionLabel = "Location access",
                    permissionRationale = "Needed to fetch local weather forecast.",
                    onToggle = { on ->
                        if (on && !hasLocationPermission) {
                            locationPermLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        } else {
                            viewModel.setWeatherEnabled(on)
                        }
                    },
                    onGrantPermission = {
                        locationPermLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                )
            }

            item {
                IntegrationCard(
                    icon = Icons.Default.CalendarMonth,
                    title = "Calendar Events",
                    description = "Shows your upcoming events in exit reminders so you know what to prepare for. Reads locally synced calendars — no sign-in needed.",
                    enabled = state.calendarEnabled,
                    hasPermission = hasCalendarPermission,
                    permissionLabel = "Calendar access",
                    permissionRationale = "Needed to read upcoming events from your device calendar.",
                    calendarEvents = state.calendarEvents,
                    onToggle = { on ->
                        if (on && !hasCalendarPermission) {
                            calendarPermLauncher.launch(Manifest.permission.READ_CALENDAR)
                        } else {
                            viewModel.setCalendarEnabled(on)
                        }
                    },
                    onGrantPermission = {
                        calendarPermLauncher.launch(Manifest.permission.READ_CALENDAR)
                    }
                )
            }
        }
    }
}

@Composable
private fun IntegrationCard(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    hasPermission: Boolean,
    permissionLabel: String,
    permissionRationale: String,
    calendarEvents: List<String> = emptyList(),
    onToggle: (Boolean) -> Unit,
    onGrantPermission: () -> Unit
) {
    var eventsExpanded by remember { mutableStateOf(true) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Title row with toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(title, style = MaterialTheme.typography.titleSmall)
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (enabled) {
                HorizontalDivider()

                // Permission status row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (hasPermission) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                permissionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                "$permissionLabel required",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (!hasPermission) {
                        TextButton(
                            onClick = onGrantPermission,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Grant", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (!hasPermission) {
                    Text(
                        permissionRationale,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Collapsible upcoming events section
                if (hasPermission && calendarEvents.isNotEmpty()) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { eventsExpanded = !eventsExpanded }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Upcoming events (${calendarEvents.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = if (eventsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (eventsExpanded) "Collapse" else "Expand",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AnimatedVisibility(
                        visible = eventsExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            calendarEvents.forEach { event ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        event,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                } else if (hasPermission && calendarEvents.isEmpty()) {
                    Text(
                        "No events in the next 3 hours",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PreviewWeatherCardEnabled() {
    ExitSenseTheme {
        Column(Modifier.padding(16.dp)) {
            IntegrationCard(
                icon = Icons.Default.WbSunny,
                title = "Weather Alerts",
                description = "Adds an umbrella reminder when rain is forecast for the next 2 hours.",
                enabled = true,
                hasPermission = true,
                permissionLabel = "Location access",
                permissionRationale = "Needed to fetch local weather forecast.",
                onToggle = {},
                onGrantPermission = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PreviewCalendarCardWithEvents() {
    ExitSenseTheme {
        Column(Modifier.padding(16.dp)) {
            IntegrationCard(
                icon = Icons.Default.CalendarMonth,
                title = "Calendar Events",
                description = "Shows your upcoming events in exit reminders.",
                enabled = true,
                hasPermission = true,
                permissionLabel = "Calendar access",
                permissionRationale = "Needed to read upcoming events from your device calendar.",
                calendarEvents = listOf("Team standup (10:00)", "Dentist (11:30)", "Lunch with Alex (13:00)"),
                onToggle = {},
                onGrantPermission = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PreviewCalendarCardNeedsPermission() {
    ExitSenseTheme {
        Column(Modifier.padding(16.dp)) {
            IntegrationCard(
                icon = Icons.Default.CalendarMonth,
                title = "Calendar Events",
                description = "Shows your upcoming events in exit reminders.",
                enabled = true,
                hasPermission = false,
                permissionLabel = "Calendar access",
                permissionRationale = "Needed to read upcoming events from your device calendar.",
                onToggle = {},
                onGrantPermission = {}
            )
        }
    }
}
