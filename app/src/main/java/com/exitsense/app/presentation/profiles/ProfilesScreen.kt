package com.exitsense.app.presentation.profiles

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
import com.exitsense.app.domain.model.ReminderItem
import com.exitsense.app.domain.model.ReminderProfile
import com.exitsense.app.domain.model.ScheduleType
import com.exitsense.app.presentation.components.EmptyStateCard
import com.exitsense.app.presentation.components.ExitSenseTopBar
import com.exitsense.app.presentation.components.LoadingScreen
import com.exitsense.app.presentation.components.StatusDot
import com.exitsense.app.presentation.theme.ExitSenseTheme

@Composable
fun ProfilesScreen(
    onNavigateBack: () -> Unit,
    onAddProfile: () -> Unit,
    onEditProfile: (Long) -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete Profile") },
            text = { Text("Delete '${state.showDeleteConfirm?.name}'? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            ExitSenseTopBar(title = "Reminder Profiles", onNavigateBack = onNavigateBack)
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProfile) {
                Icon(Icons.Default.Add, "Add Profile")
            }
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingScreen()
            return@Scaffold
        }

        if (state.profiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    message = "No profiles yet.\nTap + to create your first reminder profile.",
                    icon = { Icon(Icons.Default.PersonAdd, null, Modifier.size(48.dp)) }
                )
            }
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
            items(state.profiles, key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    onToggle = { viewModel.toggleProfile(profile.id, it) },
                    onEdit = { onEditProfile(profile.id) },
                    onDelete = { viewModel.requestDelete(profile) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(
    profile: ReminderProfile,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(elevation = CardDefaults.cardElevation(2.dp)) {
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusDot(profile.isActive)
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Edit", Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.DeleteOutline, "Delete",
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(
                        checked = profile.isActive,
                        onCheckedChange = onToggle,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            // Schedule badges — two chips that wrap on narrow screens
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                maxItemsInEachRow = 1
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(scheduleLabel(profile.scheduleType), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(14.dp)) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("${profile.startTimeFormatted}–${profile.endTimeFormatted}", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Default.AccessTime, null, Modifier.size(14.dp)) }
                )
            }

            // Item list preview
            if (profile.items.isNotEmpty()) {
                Text(
                    profile.items.take(3).joinToString(" · ") { it.name } +
                            if (profile.items.size > 3) " +${profile.items.size - 3} more" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

private val previewOfficeProfile = ReminderProfile(
    id = 1, name = "Office", isActive = true, scheduleType = ScheduleType.WEEKDAYS,
    startTimeHour = 8, startTimeMinute = 0, endTimeHour = 10, endTimeMinute = 0,
    items = listOf(
        ReminderItem(id = 1, profileId = 1, name = "Laptop"),
        ReminderItem(id = 2, profileId = 1, name = "Badge"),
        ReminderItem(id = 3, profileId = 1, name = "Charger"),
        ReminderItem(id = 4, profileId = 1, name = "Keys"),
        ReminderItem(id = 5, profileId = 1, name = "Wallet"),
    )
)

private val previewGymProfile = ReminderProfile(
    id = 2, name = "Gym", isActive = false, scheduleType = ScheduleType.WEEKENDS,
    startTimeHour = 6, startTimeMinute = 30, endTimeHour = 9, endTimeMinute = 0,
    items = listOf(
        ReminderItem(id = 6, profileId = 2, name = "Gym bag"),
        ReminderItem(id = 7, profileId = 2, name = "Water bottle"),
    )
)

@Preview(name = "Profile card — active", showBackground = true, widthDp = 360)
@Composable
private fun PreviewProfileCardActive() {
    ExitSenseTheme { ProfileCard(profile = previewOfficeProfile, onToggle = {}, onEdit = {}, onDelete = {}) }
}

@Preview(name = "Profile card — inactive", showBackground = true, widthDp = 360)
@Composable
private fun PreviewProfileCardInactive() {
    ExitSenseTheme { ProfileCard(profile = previewGymProfile, onToggle = {}, onEdit = {}, onDelete = {}) }
}

@Preview(name = "Profiles screen — list", showBackground = true, device = Devices.PIXEL_5)
@Composable
private fun PreviewProfilesList() {
    ExitSenseTheme {
        Scaffold(
            topBar = { ExitSenseTopBar(title = "Reminder Profiles", onNavigateBack = {}) },
            floatingActionButton = { FloatingActionButton(onClick = {}) { Icon(Icons.Default.Add, "Add") } }
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(listOf(previewOfficeProfile, previewGymProfile), key = { it.id }) { profile ->
                    ProfileCard(profile = profile, onToggle = {}, onEdit = {}, onDelete = {})
                }
            }
        }
    }
}

@Preview(name = "Profiles screen — empty", showBackground = true, device = Devices.PIXEL_5)
@Composable
private fun PreviewProfilesEmpty() {
    ExitSenseTheme {
        Scaffold(
            topBar = { ExitSenseTopBar(title = "Reminder Profiles", onNavigateBack = {}) },
            floatingActionButton = { FloatingActionButton(onClick = {}) { Icon(Icons.Default.Add, "Add") } }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyStateCard(message = "No profiles yet.\nTap + to create your first reminder profile.",
                    icon = { Icon(Icons.Default.PersonAdd, null, Modifier.size(48.dp)) })
            }
        }
    }
}

private fun scheduleLabel(type: ScheduleType) = when (type) {
    ScheduleType.WEEKDAYS -> "Mon–Fri"
    ScheduleType.WEEKENDS -> "Sat–Sun"
    ScheduleType.ALL_DAYS -> "Every day"
    ScheduleType.CUSTOM -> "Custom"
}
