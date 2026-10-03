package com.example.volta.ui.schedules

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.model.PowerStrip
import com.example.volta.model.Schedule
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaGreenBright
import com.example.volta.theme.VoltaRed
import com.example.volta.theme.VoltaYellow
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateScheduleSheet(
    strips: List<PowerStrip>,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current

    // State management
    var selectedStripMac by remember {
        mutableStateOf(strips.firstOrNull()?.mac ?: "")
    }
    var selectedOutlet by remember { mutableIntStateOf(0) } // 0 = Master All, 1..4 = individual
    var actionOn by remember { mutableStateOf(false) } // Default to turning OFF (saving energy)
    var triggerKind by remember { mutableStateOf("time") } // "time", "rise", "set"

    var selectedHour by remember { mutableIntStateOf(23) }
    var selectedMinute by remember { mutableIntStateOf(0) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    var offsetMinutes by remember { mutableIntStateOf(0) }
    var selectedDays by remember { mutableStateOf(listOf(0, 1, 2, 3, 4, 5, 6)) } // Mon..Sun
    var labelStr by remember { mutableStateOf("") }
    var stripDropdownExpanded by remember { mutableStateOf(false) }

    val selectedStrip = strips.find { it.mac == selectedStripMac } ?: strips.firstOrNull()

    // Formatted time string "HH:MM"
    val formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute)

    // Compute active days description
    val daysSummary = when {
        selectedDays.size == 7 -> "Everyday"
        selectedDays.containsAll(listOf(0, 1, 2, 3, 4)) && selectedDays.size == 5 -> "Weekdays (Mon-Fri)"
        selectedDays.containsAll(listOf(5, 6)) && selectedDays.size == 2 -> "Weekends (Sat-Sun)"
        selectedDays.isEmpty() -> "No days selected"
        else -> {
            val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            selectedDays.sorted().joinToString(", ") { names.getOrElse(it) { "" } }
        }
    }

    // Target name description
    val targetOutletName = if (selectedOutlet == 0) {
        "All Outlets"
    } else {
        selectedStrip?.outlets?.find { it.n == selectedOutlet }?.name ?: "Outlet $selectedOutlet"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(2.dp)
                    )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                VoltaBlue.copy(alpha = 0.12f),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = VoltaBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "New Automation Rule",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Schedule automatic switching",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_schedule_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hero Live Preview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (actionOn) {
                        VoltaGreen.copy(alpha = 0.08f)
                    } else {
                        VoltaRed.copy(alpha = 0.08f)
                    }
                ),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(
                                if (actionOn) VoltaGreen else VoltaRed,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (actionOn) Icons.Default.Power else Icons.Default.PowerOff,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (actionOn) "TURN ON" else "TURN OFF",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (actionOn) VoltaGreen else VoltaRed
                            )
                            Text(
                                text = " • $targetOutletName",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val timeTriggerDescription = when (triggerKind) {
                            "rise" -> if (offsetMinutes == 0) "At Sunrise" else if (offsetMinutes > 0) "$offsetMinutes min after Sunrise" else "${-offsetMinutes} min before Sunrise"
                            "set" -> if (offsetMinutes == 0) "At Sunset" else if (offsetMinutes > 0) "$offsetMinutes min after Sunset" else "${-offsetMinutes} min before Sunset"
                            else -> "At $formattedTime"
                        }

                        Text(
                            text = "$timeTriggerDescription • $daysSummary",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Target: ${selectedStrip?.displayName ?: "Selected Strip"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 1: Target Strip & Outlets
            SectionHeader(
                title = "1. Target Strip & Channel",
                icon = Icons.Default.ElectricBolt
            )

            // Strip Selection Dropdown (if multiple strips exist)
            if (strips.size > 1) {
                ExposedDropdownMenuBox(
                    expanded = stripDropdownExpanded,
                    onExpandedChange = { stripDropdownExpanded = !stripDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedStrip?.displayName ?: "Select Strip",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Power Strip") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stripDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("schedule_strip_selector"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = stripDropdownExpanded,
                        onDismissRequest = { stripDropdownExpanded = false }
                    ) {
                        strips.forEach { s ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(s.displayName, fontWeight = FontWeight.Medium)
                                        Text("MAC: ${s.mac}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    selectedStripMac = s.mac
                                    stripDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Outlet Selector Grid (Master All + 1..4)
            Text(
                text = "Select Target Outlet",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Master Outlet (0)
                OutletOptionPill(
                    label = "All",
                    sublabel = "Master",
                    isSelected = selectedOutlet == 0,
                    onClick = { selectedOutlet = 0 },
                    modifier = Modifier.weight(1.1f),
                    testTag = "schedule_outlet_all"
                )

                // Outlets 1 to 4
                (1..4).forEach { outletNum ->
                    val customName = selectedStrip?.outlets?.find { it.n == outletNum }?.name
                    OutletOptionPill(
                        label = "#$outletNum",
                        sublabel = customName?.take(6) ?: "Out $outletNum",
                        isSelected = selectedOutlet == outletNum,
                        onClick = { selectedOutlet = outletNum },
                        modifier = Modifier.weight(1f),
                        testTag = "schedule_outlet_$outletNum"
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 2: Action (Turn ON vs Turn OFF)
            SectionHeader(
                title = "2. Power Action",
                icon = Icons.Default.PowerSettingsNew
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Turn ON Card
                ActionChoiceCard(
                    title = "Turn ON",
                    subtitle = "Supply power",
                    icon = Icons.Default.Power,
                    isSelected = actionOn,
                    activeColor = VoltaGreen,
                    onClick = { actionOn = true },
                    modifier = Modifier.weight(1f),
                    testTag = "action_turn_on"
                )

                // Turn OFF Card
                ActionChoiceCard(
                    title = "Turn OFF",
                    subtitle = "Cut power (save energy)",
                    icon = Icons.Default.PowerOff,
                    isSelected = !actionOn,
                    activeColor = VoltaRed,
                    onClick = { actionOn = false },
                    modifier = Modifier.weight(1f),
                    testTag = "action_turn_off"
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 3: Trigger & Time
            SectionHeader(
                title = "3. Time & Trigger",
                icon = Icons.Default.AccessTime
            )

            // Trigger Kind Segment (Clock Time vs Astronomical Sunrise / Sunset)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TriggerTabButton(
                    title = "Clock Time",
                    icon = Icons.Default.AccessTime,
                    isSelected = triggerKind == "time",
                    onClick = { triggerKind = "time" },
                    modifier = Modifier.weight(1f)
                )
                TriggerTabButton(
                    title = "Sunrise",
                    icon = Icons.Default.WbSunny,
                    isSelected = triggerKind == "rise",
                    onClick = { triggerKind = "rise" },
                    modifier = Modifier.weight(1f)
                )
                TriggerTabButton(
                    title = "Sunset",
                    icon = Icons.Default.NightsStay,
                    isSelected = triggerKind == "set",
                    onClick = { triggerKind = "set" },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (triggerKind == "time") {
                // Digital Clock Display & Time Picker Launcher
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePickerDialog = true }
                        .testTag("open_time_picker_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Execution Time",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formattedTime,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = VoltaBlue,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "Tap to pick hour and minute",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { showTimePickerDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = VoltaBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Set Time")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Preset Time Chips
                Text(
                    text = "Quick Presets",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        Triple(6, 0, "06:00 (Early)"),
                        Triple(8, 30, "08:30 (Morning)"),
                        Triple(18, 0, "18:00 (Evening)"),
                        Triple(22, 0, "22:00 (Night)"),
                        Triple(23, 0, "23:00 (Bedtime)")
                    )
                    presets.forEach { (h, m, label) ->
                        val isSelected = selectedHour == h && selectedMinute == m
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedHour = h
                                selectedMinute = m
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VoltaBlue.copy(alpha = 0.15f),
                                selectedLabelColor = VoltaBlue
                            )
                        )
                    }
                }
            } else {
                // Sunrise/Sunset Offset Selector
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = if (triggerKind == "rise") "Astronomical Sunrise Trigger" else "Astronomical Sunset Trigger",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Strip switches automatically based on daily solar calculations.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Offset from Sun Event:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                -30 to "-30 min",
                                0 to "Exact",
                                30 to "+30 min"
                            ).forEach { (offset, label) ->
                                FilterChip(
                                    selected = offsetMinutes == offset,
                                    onClick = { offsetMinutes = offset },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 4: Repeat Days
            SectionHeader(
                title = "4. Repeat Schedule",
                icon = Icons.Default.Schedule
            )

            // Quick Day Filter Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedDays.size == 7,
                    onClick = { selectedDays = listOf(0, 1, 2, 3, 4, 5, 6) },
                    label = { Text("Everyday", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedDays == listOf(0, 1, 2, 3, 4),
                    onClick = { selectedDays = listOf(0, 1, 2, 3, 4) },
                    label = { Text("Weekdays", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedDays == listOf(5, 6),
                    onClick = { selectedDays = listOf(5, 6) },
                    label = { Text("Weekends", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 7 Circular Day Chips (with minimum 48dp touch targets)
            val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")
            val fullNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                dayNames.forEachIndexed { index, letter ->
                    val isDaySelected = selectedDays.contains(index)
                    val bgColor by animateColorAsState(
                        if (isDaySelected) VoltaBlue else MaterialTheme.colorScheme.surfaceVariant,
                        label = "dayBg"
                    )
                    val textColor = if (isDaySelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                            .clickable {
                                selectedDays = if (isDaySelected) {
                                    selectedDays - index
                                } else {
                                    (selectedDays + index).sorted()
                                }
                            }
                            .testTag("day_selector_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 5: Rule Label & Presets
            SectionHeader(
                title = "5. Rule Label (Optional)",
                icon = Icons.Default.Add
            )

            OutlinedTextField(
                value = labelStr,
                onValueChange = { labelStr = it },
                label = { Text("Schedule Label") },
                placeholder = { Text("e.g. Night Standby Off, Morning Routine") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("schedule_label_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick label suggestion chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "Bedtime Power Cut",
                    "Morning Routine",
                    "Save Standby Power",
                    "Office Hours"
                ).forEach { suggestion ->
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { labelStr = suggestion },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "+ $suggestion",
                            fontSize = 11.sp,
                            color = VoltaBlue,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Actions (Save & Cancel)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val newSchedule = Schedule(
                            id = UUID.randomUUID().toString(),
                            stripMac = selectedStripMac,
                            outlet = selectedOutlet,
                            actionOn = actionOn,
                            kind = triggerKind,
                            time = formattedTime,
                            offsetMinutes = offsetMinutes,
                            days = selectedDays.ifEmpty { listOf(0, 1, 2, 3, 4, 5, 6) },
                            timezone = "Africa/Cairo",
                            label = labelStr,
                            enabled = true
                        )
                        onSave(newSchedule)
                        val actionDesc = if (actionOn) "TURN ON" else "TURN OFF"
                        Toast.makeText(context, "Schedule saved: $actionDesc $targetOutletName", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1.6f)
                        .height(52.dp)
                        .testTag("save_schedule_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (actionOn) VoltaGreen else VoltaBlue
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save Schedule",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Material 3 Time Picker Dialog
    if (showTimePickerDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        selectedHour = timePickerState.hour
                        selectedMinute = timePickerState.minute
                        showTimePickerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VoltaBlue)
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("Cancel")
                }
            },
            title = {
                Text("Select Schedule Time", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = VoltaBlue,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun OutletOptionPill(
    label: String,
    sublabel: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    val grayBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val glowColor = VoltaBlue.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .then(
                if (isSelected) {
                    Modifier
                        .border(2.5.dp, glowColor, RoundedCornerShape(12.dp))
                        .padding(2.dp)
                } else {
                    Modifier.padding(2.dp)
                }
            )
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) VoltaBlue else Color.Transparent)
            .border(
                1.dp,
                if (isSelected) VoltaBlue else grayBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sublabel,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = if (isSelected) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActionChoiceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    val borderColor = if (isSelected) activeColor else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (isSelected) activeColor.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(listOf(borderColor, borderColor))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isSelected) activeColor else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TriggerTabButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shape = RoundedCornerShape(9.dp),
        shadowElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) VoltaBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
