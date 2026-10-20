package com.example.volta.ui.schedules

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaCyan
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaRed
import com.example.volta.ui.components.HomeSwitcherBar
import com.example.volta.ui.components.ManageHomesDialog
import com.example.volta.ui.components.SignedOutPrompt
import com.example.volta.viewmodel.VoltaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(
    viewModel: VoltaViewModel,
    modifier: Modifier = Modifier
) {
    val schedules by viewModel.schedules.collectAsState()
    val timers by viewModel.timers.collectAsState()
    val strips by viewModel.strips.collectAsState()
    val homes by viewModel.homes.collectAsState()
    val selectedHomeId by viewModel.selectedHomeId.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val authRequired by viewModel.authRequired.collectAsState()

    val isSignedOut = authRequired || settings.serverToken.isBlank()
    if (isSignedOut) {
        SignedOutPrompt(
            viewModel = viewModel,
            title = "Sign In to View Schedules & Timers",
            subtitle = "You are currently signed out. Sign in with Google or enter your user token to view and automate your outlet schedules and timers.",
            modifier = modifier
        )
        return
    }

    var activeSubTab by remember { mutableIntStateOf(0) } // 0 = Daily Schedules, 1 = Outlet Timers
    var showAddScheduleSheet by remember { mutableStateOf(false) }
    var showAddTimerSheet by remember { mutableStateOf(false) }
    var showManageHomes by remember { mutableStateOf(false) }

    val homeFilteredStrips = remember(strips, selectedHomeId) {
        if (selectedHomeId == null) strips
        else strips.filter { it.homeId == selectedHomeId }
    }
    val homeMacs = remember(homeFilteredStrips) {
        homeFilteredStrips.map { it.mac.replace(":", "").uppercase() }.toSet()
    }

    val homeFilteredSchedules = remember(schedules, homeMacs, selectedHomeId) {
        if (selectedHomeId == null) schedules
        else schedules.filter { it.stripMac.replace(":", "").uppercase() in homeMacs }
    }

    val homeFilteredTimers = remember(timers, homeMacs, selectedHomeId) {
        if (selectedHomeId == null) timers
        else timers.filter { it.stripMac.replace(":", "").uppercase() in homeMacs }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Home Switcher Bar (Syncs with Control and Analytics tabs)
        item {
            HomeSwitcherBar(
                homes = homes,
                selectedHomeId = selectedHomeId,
                allStrips = strips,
                onSelectHome = { viewModel.setSelectedHomeId(it) },
                onOpenManageHomes = { showManageHomes = true }
            )
        }

        // Top SubTab Switcher (Schedules vs Timers)
        item {
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
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { activeSubTab = 0 }
                        .testTag("subtab_schedules"),
                    color = if (activeSubTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                    shadowElevation = if (activeSubTab == 0) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (activeSubTab == 0) VoltaBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Daily Schedules (${homeFilteredSchedules.size})",
                            fontSize = 13.sp,
                            fontWeight = if (activeSubTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (activeSubTab == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { activeSubTab = 1 }
                        .testTag("subtab_timers"),
                    color = if (activeSubTab == 1) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                    shadowElevation = if (activeSubTab == 1) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (activeSubTab == 1) VoltaBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Outlet Timers",
                            fontSize = 13.sp,
                            fontWeight = if (activeSubTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (activeSubTab == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val activeTimersCount = homeFilteredTimers.count { it.isRunning && !it.isPaused }
                        if (activeTimersCount > 0) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Surface(
                                color = VoltaGreen,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "$activeTimersCount",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        } else if (homeFilteredTimers.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "(${homeFilteredTimers.size})",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (activeSubTab == 0) {
            // ==================== DAILY SCHEDULES HERO ====================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131C31)),
                    border = BorderStroke(1.dp, Color(0x3338BDF8))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                                )
                            )
                            .padding(16.dp)
                    ) {
                        // Title + Count + Add Button Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color(0x2638BDF8), CircleShape)
                                        .border(1.5.dp, VoltaCyan.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = VoltaCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "DAILY SCHEDULES",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${homeFilteredSchedules.size} Active Rules",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Button(
                                onClick = { showAddScheduleSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = VoltaBlue),
                                shape = RoundedCornerShape(99.dp),
                                modifier = Modifier.testTag("add_schedule_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Rule", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0x1FFFFFFF), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Clock time & NOAA solar automation",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(99.dp),
                                color = Color(0x1F22C55E),
                                border = BorderStroke(1.dp, Color(0x4D22C55E))
                            ) {
                                Text(
                                    text = "⚡ Daemon Sync",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VoltaGreen,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            if (homeFilteredSchedules.isEmpty()) {
                item {
                    val currentHomeName = homes.find { it.id == selectedHomeId }?.name
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
                                text = if (selectedHomeId != null && homeFilteredStrips.isEmpty()) "No Strips in '$currentHomeName'"
                                       else if (selectedHomeId != null) "No Schedules in '$currentHomeName'"
                                       else "No Active Schedules",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (selectedHomeId != null && homeFilteredStrips.isEmpty()) "Assign a strip to this home in Control, or switch to All Homes to manage automation."
                                       else "Click 'New Schedule' to set automated rules.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(homeFilteredSchedules, key = { it.id }) { item ->
                    val strip = homeFilteredStrips.find { it.mac == item.stripMac }
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
        } else {
            // ==================== OUTLET TIMERS HERO ====================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131C31)),
                    border = BorderStroke(1.dp, Color(0x3338BDF8))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                                )
                            )
                            .padding(16.dp)
                    ) {
                        // Title + Count + Add Button Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color(0x2638BDF8), CircleShape)
                                        .border(1.5.dp, VoltaCyan.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = VoltaCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "OUTLET TIMERS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (homeFilteredTimers.isEmpty()) "0 Configured" else "${homeFilteredTimers.size} Configured (${homeFilteredTimers.count { it.isRunning && !it.isPaused }} Active)",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Button(
                                onClick = { showAddTimerSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = VoltaBlue),
                                shape = RoundedCornerShape(99.dp),
                                modifier = Modifier.testTag("add_timer_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Timer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0x1FFFFFFF), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Autonomous countdown auto-off & cyclic loops",
                                fontSize = 11.5.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(99.dp),
                                color = Color(0x1F22C55E),
                                border = BorderStroke(1.dp, Color(0x4D22C55E))
                            ) {
                                Text(
                                    text = "⏱️ Server Accurate",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VoltaGreen,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            if (homeFilteredTimers.isEmpty()) {
                item {
                    val currentHomeName = homes.find { it.id == selectedHomeId }?.name
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
                                imageVector = Icons.Default.HourglassEmpty,
                                contentDescription = "No Timers",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (selectedHomeId != null && homeFilteredStrips.isEmpty()) "No Strips in '$currentHomeName'"
                                       else if (selectedHomeId != null) "No Timers in '$currentHomeName'"
                                       else "No Active Timers",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (selectedHomeId != null && homeFilteredStrips.isEmpty()) "Assign a strip to this home in Control, or switch to All Homes to manage timers."
                                       else "Tap 'New Timer' to set a countdown auto-off or intermittent pulse cycle.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(homeFilteredTimers, key = { it.id }) { timer ->
                    val strip = homeFilteredStrips.find { it.mac == timer.stripMac }
                    TimerItemCard(
                        timer = timer,
                        strip = strip,
                        onTogglePause = { viewModel.toggleTimerPause(timer.id) },
                        onReset = { viewModel.resetTimer(timer.id) },
                        onDelete = { viewModel.deleteTimer(timer.id) }
                    )
                }
            }
        }
    }

    // Create Schedule Modal Bottom Sheet
    if (showAddScheduleSheet) {
        CreateScheduleSheet(
            strips = homeFilteredStrips,
            onDismiss = { showAddScheduleSheet = false },
            onSave = { newSchedule ->
                viewModel.addSchedule(newSchedule)
            }
        )
    }

    // Create Timer Modal Bottom Sheet
    if (showAddTimerSheet) {
        CreateTimerSheet(
            strips = homeFilteredStrips,
            onDismiss = { showAddTimerSheet = false },
            onSave = { newTimer ->
                viewModel.addTimer(newTimer)
                activeSubTab = 1
                showAddTimerSheet = false
            }
        )
    }

    // Multi-Home Management Dialog
    if (showManageHomes) {
        ManageHomesDialog(
            homes = homes,
            allStrips = strips,
            onCreateHome = { name, icon -> viewModel.createHome(name, icon) },
            onUpdateHome = { id, name, icon -> viewModel.updateHome(id, name, icon) },
            onDeleteHome = { id -> viewModel.deleteHome(id) },
            onDismiss = { showManageHomes = false }
        )
    }
}
