package com.exitsense.app.presentation.profiles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exitsense.app.domain.model.ScheduleType
import com.exitsense.app.presentation.components.ExitSenseTopBar
import com.exitsense.app.presentation.components.LoadingScreen
import com.exitsense.app.presentation.theme.ExitSenseTheme

@Composable
fun AddEditProfileScreen(
    profileId: Long?,
    onNavigateBack: () -> Unit,
    viewModel: AddEditProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(profileId) {
        if (profileId != null) viewModel.loadProfile(profileId)
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onNavigateBack()
    }

    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            ExitSenseTopBar(
                title = if (state.isEditMode) "Edit Profile" else "New Profile",
                onNavigateBack = onNavigateBack,
                actions = {
                    TextButton(
                        onClick = viewModel::saveProfile,
                        enabled = !state.isSaving
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            )
        }
    ) { padding ->

        if (state.isLoading) { LoadingScreen(); return@Scaffold }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Profile name
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChanged,
                    label = { Text("Profile Name") },
                    placeholder = { Text("e.g. Office, Gym, Travel") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = state.error?.contains("name") == true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
            }

            // Schedule type selector
            item {
                ScheduleSelector(
                    selected = state.scheduleType,
                    onSelect = viewModel::onScheduleTypeChanged
                )
            }

            // Day picker (for CUSTOM only)
            if (state.scheduleType == ScheduleType.CUSTOM) {
                item {
                    DayPicker(
                        selectedDays = state.activeDays,
                        onDaysChanged = viewModel::onActiveDaysChanged
                    )
                }
            }

            // Time window
            item {
                TimeWindowRow(
                    startHour = state.startTimeHour,
                    startMinute = state.startTimeMinute,
                    endHour = state.endTimeHour,
                    endMinute = state.endTimeMinute,
                    onStartChanged = viewModel::onStartTimeChanged,
                    onEndChanged = viewModel::onEndTimeChanged
                )
            }

            // Items header
            item {
                Text("Checklist Items", style = MaterialTheme.typography.titleMedium)
            }

            // Item input
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.newItemName,
                        onValueChange = viewModel::onNewItemNameChanged,
                        label = { Text("Item name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            viewModel.addItem()
                            focusManager.clearFocus()
                        })
                    )
                    FilledTonalIconButton(
                        onClick = viewModel::addItem,
                        enabled = state.newItemName.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, "Add item")
                    }
                }
            }

            // Items list
            itemsIndexed(state.items, key = { index, _ -> index }) { index, item ->
                ItemRow(
                    name = item.name,
                    onRemove = { viewModel.removeItem(index) }
                )
            }

            // Error message
            state.error?.let { error ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                            Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleSelector(selected: ScheduleType, onSelect: (ScheduleType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Schedule", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 3
        ) {
            ScheduleType.entries.forEach { type ->
                FilterChip(
                    selected = type == selected,
                    onClick = { onSelect(type) },
                    label = {
                        Text(
                            when (type) {
                                ScheduleType.WEEKDAYS -> "Weekdays"
                                ScheduleType.WEEKENDS -> "Weekends"
                                ScheduleType.ALL_DAYS -> "Every day"
                                ScheduleType.CUSTOM -> "Custom"
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DayPicker(selectedDays: Set<Int>, onDaysChanged: (Set<Int>) -> Unit) {
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Active Days", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            dayLabels.forEachIndexed { index, label ->
                val day = index + 1
                FilterChip(
                    selected = day in selectedDays,
                    onClick = {
                        val newDays = if (day in selectedDays)
                            selectedDays - day else selectedDays + day
                        onDaysChanged(newDays)
                    },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
private fun TimeWindowRow(
    startHour: Int, startMinute: Int,
    endHour: Int, endMinute: Int,
    onStartChanged: (Int, Int) -> Unit,
    onEndChanged: (Int, Int) -> Unit
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    if (showStartPicker) {
        TimePickerDialog(
            initialHour = startHour,
            initialMinute = startMinute,
            onConfirm = { h, m -> onStartChanged(h, m); showStartPicker = false },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initialHour = endHour,
            initialMinute = endMinute,
            onConfirm = { h, m -> onEndChanged(h, m); showEndPicker = false },
            onDismiss = { showEndPicker = false }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Active Time Window", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { showStartPicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.AccessTime, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("From %02d:%02d".format(startHour, startMinute))
            }
            OutlinedButton(
                onClick = { showEndPicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.AccessTime, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Until %02d:%02d".format(endHour, endMinute))
            }
        }
    }
}

@Composable
private fun TimePickerDialog(
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
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ItemRow(name: String, onRemove: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircleOutline, null,
                    Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(name, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, "Remove", Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(name = "Checklist item row", showBackground = true, widthDp = 360)
@Composable
private fun PreviewItemRow() {
    ExitSenseTheme { ItemRow(name = "Laptop charger", onRemove = {}) }
}

@Preview(name = "Schedule selector — weekdays", showBackground = true, widthDp = 360)
@Composable
private fun PreviewScheduleSelector() {
    ExitSenseTheme { ScheduleSelector(selected = ScheduleType.WEEKDAYS, onSelect = {}) }
}

@Preview(name = "Day picker — Mon–Fri selected", showBackground = true, widthDp = 360)
@Composable
private fun PreviewDayPicker() {
    ExitSenseTheme { DayPicker(selectedDays = setOf(1, 2, 3, 4, 5), onDaysChanged = {}) }
}

@Preview(name = "Add profile screen", showBackground = true, widthDp = 360)
@Composable
private fun PreviewAddEditProfileScreen() {
    ExitSenseTheme {
        Scaffold(
            topBar = { ExitSenseTopBar(title = "New Profile", onNavigateBack = {},
                actions = { TextButton(onClick = {}) { Text("Save", color = MaterialTheme.colorScheme.onPrimary) } }) }
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { OutlinedTextField(value = "Office", onValueChange = {}, label = { Text("Profile Name") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { ScheduleSelector(selected = ScheduleType.WEEKDAYS, onSelect = {}) }
                item { Text("Checklist Items", style = MaterialTheme.typography.titleMedium) }
                item { ItemRow(name = "Laptop", onRemove = {}) }
                item { ItemRow(name = "Badge", onRemove = {}) }
                item { ItemRow(name = "Keys", onRemove = {}) }
            }
        }
    }
}
