package com.example.volta.ui.schedules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.model.Schedule
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaRed
import com.example.volta.ui.components.InteractiveToggle
import com.example.volta.ui.components.TagBadge
import com.example.volta.viewmodel.VoltaViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(
    viewModel: VoltaViewModel,
    modifier: Modifier = Modifier
) {
    val schedules by viewModel.schedules.collectAsState()
    val strips by viewModel.strips.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Banner Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Schedules",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("add_schedule_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Schedule")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Automate per-outlet or master switching on chosen days. Commands apply automatically even if strips temporarily go offline.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Active Schedules
        if (schedules.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "No Schedules",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Active Schedules",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Click 'New Schedule' to set automated timers or sunrise/sunset rules.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(schedules, key = { it.id }) { item ->
                val strip = strips.find { it.mac == item.stripMac }
                val targetName = if (item.outlet == 0) "All Outlets" else "Outlet ${item.outlet}"
                val daysNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = item.label.ifBlank { "${strip?.displayName ?: item.stripMac} • $targetName" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${if (item.actionOn) "TURN ON" else "TURN OFF"} at ${item.time} (${item.timezone})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (item.actionOn) VoltaGreen else VoltaRed
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = item.enabled,
                                    onCheckedChange = { viewModel.toggleSchedule(item.id, it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VoltaGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(onClick = { viewModel.deleteSchedule(item.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Schedule",
                                        tint = VoltaRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Days Pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            daysNames.forEachIndexed { idx, name ->
                                val isSelected = item.days.contains(idx)
                                Surface(
                                    color = if (isSelected) VoltaBlue.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp),
                                    border = if (isSelected) CardDefaults.outlinedCardBorder() else null
                                ) {
                                    Text(
                                        text = name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) VoltaBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Schedule Dialog
    if (showAddDialog) {
        var selectedStripMac by remember { mutableStateOf(strips.firstOrNull()?.mac ?: "") }
        var outletIndex by remember { mutableStateOf(0) } // 0 = all
        var actionOn by remember { mutableStateOf(true) }
        var timeStr by remember { mutableStateOf("23:00") }
        var labelStr by remember { mutableStateOf("") }
        var selectedDays by remember { mutableStateOf(listOf(0, 1, 2, 3, 4, 5, 6)) }
        var timezone by remember { mutableStateOf("Africa/Cairo") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Create Outlet Schedule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Strip select
                    if (strips.isNotEmpty()) {
                        Text("Power Strip", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            strips.forEach { s ->
                                FilterChip(
                                    selected = selectedStripMac == s.mac,
                                    onClick = { selectedStripMac = s.mac },
                                    label = { Text(s.displayName, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    // Outlet select
                    Text("Target Outlet", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = outletIndex == 0,
                            onClick = { outletIndex = 0 },
                            label = { Text("All 4", fontSize = 11.sp) }
                        )
                        (1..4).forEach { n ->
                            FilterChip(
                                selected = outletIndex == n,
                                onClick = { outletIndex = n },
                                label = { Text("Outlet $n", fontSize = 11.sp) }
                            )
                        }
                    }

                    // Action
                    Text("Action", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = actionOn,
                            onClick = { actionOn = true },
                            label = { Text("Turn ON") }
                        )
                        FilterChip(
                            selected = !actionOn,
                            onClick = { actionOn = false },
                            label = { Text("Turn OFF") }
                        )
                    }

                    OutlinedTextField(
                        value = timeStr,
                        onValueChange = { timeStr = it },
                        label = { Text("Time (24h e.g. 23:30)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = labelStr,
                        onValueChange = { labelStr = it },
                        label = { Text("Label (optional, e.g. Night Off)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Days selector
                    Text("Active Days", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    val daysLabels = listOf("M", "T", "W", "T", "F", "S", "S")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        daysLabels.forEachIndexed { index, letter ->
                            val on = selectedDays.contains(index)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        color = if (on) VoltaBlue else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        selectedDays = if (on) selectedDays - index else selectedDays + index
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = letter,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newSched = Schedule(
                            id = UUID.randomUUID().toString(),
                            stripMac = selectedStripMac,
                            outlet = outletIndex,
                            actionOn = actionOn,
                            time = timeStr,
                            days = selectedDays.ifEmpty { listOf(0, 1, 2, 3, 4, 5, 6) },
                            timezone = timezone,
                            label = labelStr,
                            enabled = true
                        )
                        viewModel.addSchedule(newSched)
                        showAddDialog = false
                    }
                ) {
                    Text("Add Schedule")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
