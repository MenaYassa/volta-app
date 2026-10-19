package com.example.volta.ui.schedules

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.model.OutletTimer
import com.example.volta.model.PowerStrip
import com.example.volta.model.TimerMode
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaRed
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateTimerSheet(
    strips: List<PowerStrip>,
    initialStripMac: String = strips.firstOrNull()?.mac ?: "",
    initialOutlet: Int = 0,
    onDismiss: () -> Unit,
    onSave: (OutletTimer) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    var isSubmitting by remember { mutableStateOf(false) }

    var selectedStripMac by remember {
        mutableStateOf(initialStripMac.ifBlank { strips.firstOrNull()?.mac ?: "" })
    }
    var selectedOutlet by remember { mutableIntStateOf(initialOutlet) } // 0 = Master, 1..4 = individual
    var timerMode by remember { mutableStateOf(TimerMode.COUNTDOWN) }

    // Countdown settings: Target action (true = Turn ON, false = Turn OFF). Default is OFF!
    var targetActionOn by remember { mutableStateOf(false) }

    // Durations with unit selector: value + unit in seconds, minutes, hours, days
    var countdownValueStr by remember { mutableStateOf("15") }
    var countdownUnit by remember { mutableStateOf("minutes") } // "seconds", "minutes", "hours", "days"

    // Cyclic ON duration
    var cyclicOnValueStr by remember { mutableStateOf("15") }
    var cyclicOnUnit by remember { mutableStateOf("minutes") }

    // Cyclic OFF duration
    var cyclicOffValueStr by remember { mutableStateOf("45") }
    var cyclicOffUnit by remember { mutableStateOf("minutes") }

    // Repeat Behavior: "once" (default), "times", "forever"
    var repeatOption by remember { mutableStateOf("once") } // "once", "times", "forever"
    var customRepeatCount by remember { mutableIntStateOf(5) }

    var labelStr by remember { mutableStateOf("") }
    var stripDropdownExpanded by remember { mutableStateOf(false) }

    val selectedStrip = strips.find { it.mac == selectedStripMac } ?: strips.firstOrNull()

    val targetOutletName = if (selectedOutlet == 0) {
        "All Outlets"
    } else {
        selectedStrip?.outlets?.find { it.n == selectedOutlet }?.name ?: "Outlet $selectedOutlet"
    }

    // Convert value + unit to total seconds
    fun unitToSeconds(valStr: String, unit: String): Long {
        val num = valStr.toLongOrNull()?.coerceAtLeast(1L) ?: 1L
        return when (unit) {
            "seconds" -> num
            "minutes" -> num * 60L
            "hours" -> num * 3600L
            "days" -> num * 86400L
            else -> num * 60L
        }
    }

    val totalCountdownSeconds = unitToSeconds(countdownValueStr, countdownUnit).coerceAtLeast(1L)
    val totalCyclicOnSeconds = unitToSeconds(cyclicOnValueStr, cyclicOnUnit).coerceAtLeast(1L)
    val totalCyclicOffSeconds = unitToSeconds(cyclicOffValueStr, cyclicOffUnit).coerceAtLeast(1L)

    val effectiveRepeatCount = when (repeatOption) {
        "once" -> 1
        "forever" -> -1
        else -> customRepeatCount.coerceAtLeast(2)
    }

    fun formatDurationPlain(seconds: Long): String {
        return when {
            seconds < 60 -> "$seconds sec"
            seconds < 3600 -> "${seconds / 60} min"
            seconds < 86400 -> {
                val h = seconds / 3600
                val m = (seconds % 3600) / 60
                if (m > 0) "$h hr $m min" else "$h hr"
            }
            else -> {
                val d = seconds / 86400
                val h = (seconds % 86400) / 3600
                if (h > 0) "$d day $h hr" else "$d day"
            }
        }
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
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = VoltaBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "New Outlet Timer",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Server-backed timers (runs when app is closed)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_timer_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live Preview Hero Card in plain English
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        timerMode == TimerMode.COUNTDOWN && targetActionOn -> VoltaGreen.copy(alpha = 0.08f)
                        timerMode == TimerMode.COUNTDOWN && !targetActionOn -> VoltaRed.copy(alpha = 0.08f)
                        else -> VoltaBlue.copy(alpha = 0.08f)
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
                                when {
                                    timerMode == TimerMode.COUNTDOWN && targetActionOn -> VoltaGreen
                                    timerMode == TimerMode.COUNTDOWN && !targetActionOn -> VoltaRed
                                    else -> VoltaBlue
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (timerMode) {
                                TimerMode.COUNTDOWN -> if (targetActionOn) Icons.Default.Power else Icons.Default.PowerOff
                                TimerMode.CYCLIC -> Icons.Default.Loop
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (timerMode) {
                                TimerMode.COUNTDOWN -> if (targetActionOn) "AUTO TURN ON" else "AUTO TURN OFF"
                                TimerMode.CYCLIC -> "CYCLIC ON/OFF LOOP"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                timerMode == TimerMode.COUNTDOWN && targetActionOn -> VoltaGreen
                                timerMode == TimerMode.COUNTDOWN && !targetActionOn -> VoltaRed
                                else -> VoltaBlue
                            }
                        )

                        Text(
                            text = when (timerMode) {
                                TimerMode.COUNTDOWN -> {
                                    val act = if (targetActionOn) "Turn ON" else "Turn OFF"
                                    "$act $targetOutletName in ${formatDurationPlain(totalCountdownSeconds)}"
                                }
                                TimerMode.CYCLIC -> {
                                    "Stay ON for ${formatDurationPlain(totalCyclicOnSeconds)}, OFF for ${formatDurationPlain(totalCyclicOffSeconds)}"
                                }
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val repeatText = when (repeatOption) {
                            "once" -> "Runs once"
                            "forever" -> "Repeats forever ♾️"
                            else -> "Repeats for $effectiveRepeatCount cycles"
                        }

                        Text(
                            text = "Target: ${selectedStrip?.displayName ?: "Selected Strip"} • $repeatText",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 1: Target Strip & Outlet Pills
            SectionHeader(
                title = "1. Target Strip & Outlet",
                icon = Icons.Default.ElectricBolt
            )

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
                            .testTag("timer_strip_selector"),
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

            // Outlet Selector Grid (Master All + 1..4) with Solid Blue Fill + White Text + Glow Ring
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TimerOutletPill(
                    label = "All",
                    sublabel = "Master",
                    isSelected = selectedOutlet == 0,
                    onClick = { selectedOutlet = 0 },
                    modifier = Modifier.weight(1.1f),
                    testTag = "timer_outlet_all"
                )

                (1..4).forEach { outletNum ->
                    val customName = selectedStrip?.outlets?.find { it.n == outletNum }?.name
                    TimerOutletPill(
                        label = "#$outletNum",
                        sublabel = customName?.take(6) ?: "Out $outletNum",
                        isSelected = selectedOutlet == outletNum,
                        onClick = { selectedOutlet = outletNum },
                        modifier = Modifier.weight(1f),
                        testTag = "timer_outlet_$outletNum"
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 2: Timer Mode & Action
            SectionHeader(
                title = "2. Timer Mode & Action",
                icon = Icons.Default.HourglassBottom
            )

            // Mode Toggle: Countdown vs Cyclic
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
                TimerModeTab(
                    title = "Countdown",
                    subtitle = "Single switch",
                    icon = Icons.Default.HourglassBottom,
                    isSelected = timerMode == TimerMode.COUNTDOWN,
                    onClick = { timerMode = TimerMode.COUNTDOWN },
                    modifier = Modifier.weight(1f)
                )
                TimerModeTab(
                    title = "Cyclic Loop",
                    subtitle = "Repeat ON ↔ OFF",
                    icon = Icons.Default.Loop,
                    isSelected = timerMode == TimerMode.CYCLIC,
                    onClick = { timerMode = TimerMode.CYCLIC },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // If Countdown: Choose action (Turn OFF vs Turn ON)
            if (timerMode == TimerMode.COUNTDOWN) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TimerActionCard(
                        title = "Turn OFF",
                        subtitle = "Cut power (Default)",
                        icon = Icons.Default.PowerOff,
                        isSelected = !targetActionOn,
                        activeColor = VoltaRed,
                        onClick = { targetActionOn = false },
                        modifier = Modifier.weight(1f),
                        testTag = "timer_action_turn_off"
                    )

                    TimerActionCard(
                        title = "Turn ON",
                        subtitle = "Supply power",
                        icon = Icons.Default.Power,
                        isSelected = targetActionOn,
                        activeColor = VoltaGreen,
                        onClick = { targetActionOn = true },
                        modifier = Modifier.weight(1f),
                        testTag = "timer_action_turn_on"
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 3: Duration with Unit Selector
            SectionHeader(
                title = "3. Duration & Presets",
                icon = Icons.Default.Timer
            )

            if (timerMode == TimerMode.COUNTDOWN) {
                DurationWithUnitSelector(
                    title = "Countdown Duration",
                    valueStr = countdownValueStr,
                    onValueChange = { countdownValueStr = it },
                    unit = countdownUnit,
                    onUnitChange = { countdownUnit = it },
                    accentColor = if (targetActionOn) VoltaGreen else VoltaRed
                )
            } else {
                DurationWithUnitSelector(
                    title = "Stay ON Duration",
                    valueStr = cyclicOnValueStr,
                    onValueChange = { cyclicOnValueStr = it },
                    unit = cyclicOnUnit,
                    onUnitChange = { cyclicOnUnit = it },
                    accentColor = VoltaGreen
                )
                Spacer(modifier = Modifier.height(10.dp))
                DurationWithUnitSelector(
                    title = "Stay OFF Duration",
                    valueStr = cyclicOffValueStr,
                    onValueChange = { cyclicOffValueStr = it },
                    unit = cyclicOffUnit,
                    onUnitChange = { cyclicOffUnit = it },
                    accentColor = VoltaRed
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 4: Repeat Behavior (Once default, N times, Forever)
            SectionHeader(
                title = "4. Repeat Cycle",
                icon = Icons.Default.Repeat
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimerRepeatCard(
                    title = "Once",
                    subtitle = "Default",
                    isSelected = repeatOption == "once",
                    onClick = { repeatOption = "once" },
                    modifier = Modifier.weight(1f),
                    testTag = "repeat_once"
                )
                TimerRepeatCard(
                    title = "N Times",
                    subtitle = if (repeatOption == "times") "$customRepeatCount cycles" else "Specific count",
                    isSelected = repeatOption == "times",
                    onClick = { repeatOption = "times" },
                    modifier = Modifier.weight(1f),
                    testTag = "repeat_times"
                )
                TimerRepeatCard(
                    title = "Forever",
                    subtitle = "♾️ Continuous",
                    isSelected = repeatOption == "forever",
                    onClick = { repeatOption = "forever" },
                    modifier = Modifier.weight(1f),
                    testTag = "repeat_forever"
                )
            }

            // Stepper when "N Times" is chosen
            if (repeatOption == "times") {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Repeat Cycles:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { customRepeatCount = (customRepeatCount - 1).coerceAtLeast(2) },
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = "$customRepeatCount",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = VoltaBlue,
                            modifier = Modifier.width(36.dp),
                            textAlign = TextAlign.Center
                        )
                        IconButton(
                            onClick = { customRepeatCount = (customRepeatCount + 1).coerceAtMost(99) },
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 5: Timer Label & Web Suggestions
            SectionHeader(
                title = "5. Label (Optional)",
                icon = Icons.Default.Add
            )

            OutlinedTextField(
                value = labelStr,
                onValueChange = { labelStr = it },
                label = { Text("Timer Label") },
                placeholder = { Text("e.g. Charger Shutoff, Pump Loop") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("timer_label_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Web UI Parity Suggestions: + Charger Shutoff, + Pump Loop, + Fan Pulse
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "Charger Shutoff",
                    "Pump Loop",
                    "Fan Pulse",
                    "Power Saver"
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
                        if (isSubmitting) return@Button
                        isSubmitting = true

                        val onSec = if (timerMode == TimerMode.COUNTDOWN) totalCountdownSeconds else totalCyclicOnSeconds
                        val offSec = if (timerMode == TimerMode.COUNTDOWN) totalCountdownSeconds else totalCyclicOffSeconds
                        val duration = if (timerMode == TimerMode.COUNTDOWN) totalCountdownSeconds else totalCyclicOnSeconds
                        val now = System.currentTimeMillis()
                        val newTimer = OutletTimer(
                            id = UUID.randomUUID().toString(),
                            stripMac = selectedStripMac,
                            outlet = selectedOutlet,
                            label = labelStr.trim(),
                            mode = timerMode,
                            targetActionOn = targetActionOn,
                            durationSeconds = duration,
                            onDurationSeconds = onSec,
                            offDurationSeconds = offSec,
                            startPhaseOn = targetActionOn,
                            repeatCount = effectiveRepeatCount,
                            currentCycle = 0,
                            isRunning = true,
                            isPaused = false,
                            currentPhaseOn = if (timerMode == TimerMode.CYCLIC) true else targetActionOn,
                            remainingSeconds = duration,
                            totalSecondsInPhase = duration,
                            startedAt = now,
                            lastTickAt = now,
                            phase = if (timerMode == TimerMode.COUNTDOWN) (if (targetActionOn) "on" else "off") else "on",
                            phaseStarted = now
                        )
                        onSave(newTimer)

                        val durationStr = formatDurationPlain(duration)
                        val toastMsg = if (timerMode == TimerMode.COUNTDOWN) {
                            val actionStr = if (targetActionOn) "Turn ON" else "Turn OFF"
                            "⏱️ Timer set: $actionStr $targetOutletName in $durationStr"
                        } else {
                            "⏱️ Cyclic timer started for $targetOutletName"
                        }
                        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(52.dp)
                        .testTag("save_timer_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            timerMode == TimerMode.COUNTDOWN && targetActionOn -> VoltaGreen
                            timerMode == TimerMode.COUNTDOWN && !targetActionOn -> VoltaRed
                            else -> VoltaBlue
                        }
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Start Timer",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DurationWithUnitSelector(
    title: String,
    valueStr: String,
    onValueChange: (String) -> Unit,
    unit: String,
    onUnitChange: (String) -> Unit,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Value Text Input + Unit Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = valueStr,
                    onValueChange = { if (it.all { ch -> ch.isDigit() } && it.length <= 5) onValueChange(it) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(90.dp),
                    shape = RoundedCornerShape(10.dp)
                )

                // Unit Chips: seconds, minutes, hours, days
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("seconds", "minutes", "hours", "days").forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { onUnitChange(u) },
                            label = { Text(u, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.18f),
                                selectedLabelColor = accentColor
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Presets: 30s, 15m, 1h, 2h, 8h, 1d
            Text(
                text = "Quick Presets:",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Pair("30", "seconds"),
                    Pair("15", "minutes"),
                    Pair("1", "hours"),
                    Pair("2", "hours"),
                    Pair("8", "hours"),
                    Pair("1", "days")
                ).forEach { (v, u) ->
                    val label = when (u) {
                        "seconds" -> "${v}s"
                        "minutes" -> "${v}m"
                        "hours" -> "${v}h"
                        "days" -> "${v}d"
                        else -> v
                    }
                    val isSelected = valueStr == v && unit == u
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onValueChange(v)
                            onUnitChange(u)
                        },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.18f),
                            selectedLabelColor = accentColor
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerOutletPill(
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
private fun TimerModeTab(
    title: String,
    subtitle: String,
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) VoltaBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TimerActionCard(
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
private fun TimerRepeatCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    val borderColor = if (isSelected) VoltaBlue else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (isSelected) VoltaBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(listOf(borderColor, borderColor))
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) VoltaBlue else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = if (isSelected) VoltaBlue else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
