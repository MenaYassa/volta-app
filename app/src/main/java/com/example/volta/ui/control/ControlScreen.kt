package com.example.volta.ui.control

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import com.example.volta.model.OutletTimer
import com.example.volta.ui.schedules.CreateTimerSheet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.model.Outlet
import com.example.volta.model.PowerStrip
import com.example.volta.theme.VoltaAmber
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaGreenBright
import com.example.volta.theme.VoltaNavy
import com.example.volta.theme.VoltaRed
import com.example.volta.theme.VoltaYellow
import com.example.volta.ui.VoltaTab
import com.example.volta.ui.components.InteractiveToggle
import com.example.volta.ui.components.RenameDialog
import com.example.volta.ui.components.StatusDot
import com.example.volta.ui.components.TagBadge
import com.example.volta.viewmodel.VoltaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(
    viewModel: VoltaViewModel,
    modifier: Modifier = Modifier
) {
    val strips by viewModel.strips.collectAsState()
    val timers by viewModel.timers.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val authRequired by viewModel.authRequired.collectAsState()

    var selectedFilterMac by remember { mutableStateOf<String?>(null) }
    var renameDialogTarget by remember { mutableStateOf<Pair<String, Outlet>?>(null) }
    var renameStripTarget by remember { mutableStateOf<PowerStrip?>(null) }
    var timerSheetTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }

    val filteredStrips = if (selectedFilterMac == null) strips else strips.filter { it.mac == selectedFilterMac }

    val totalLoadW = strips.sumOf { it.totalPowerW }
    val totalOutlets = strips.sumOf { it.outlets.size }
    val activeOutletsCount = strips.sumOf { it.outlets.count { o -> o.on } }
    val avgVoltage = if (strips.isNotEmpty()) strips.map { it.voltageV }.average() else 220.0

    // Outlet Rename Dialog
    renameDialogTarget?.let { (mac, outlet) ->
        RenameDialog(
            title = "Rename Outlet ${outlet.n}",
            initialValue = outlet.name,
            label = "Custom Name (e.g. Workstation, Coffee Maker)",
            onConfirm = { newName ->
                viewModel.renameOutlet(mac, outlet.n, newName)
                renameDialogTarget = null
            },
            onDismiss = { renameDialogTarget = null }
        )
    }

    // Strip Rename Dialog
    renameStripTarget?.let { strip ->
        RenameDialog(
            title = "Rename Power Strip",
            initialValue = strip.displayName,
            label = "Strip Location / Name",
            onConfirm = { newName ->
                viewModel.renameStrip(strip.mac, newName)
                renameStripTarget = null
            },
            onDismiss = { renameStripTarget = null }
        )
    }

    // Quick Timer Sheet Modal
    timerSheetTarget?.let { (mac, outletN) ->
        CreateTimerSheet(
            strips = strips,
            initialStripMac = mac,
            initialOutlet = outletN,
            onDismiss = { timerSheetTarget = null },
            onSave = { newTimer ->
                viewModel.addTimer(newTimer)
                timerSheetTarget = null
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Energy Overview Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "REAL-TIME ACTIVE LOAD",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = String.format("%.1f", totalLoadW),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (totalLoadW > 500) VoltaYellow else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Watts",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }

                        // Status pill badges
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TagBadge(
                                text = "${String.format("%.0f", avgVoltage)} V Normal",
                                color = VoltaGreen,
                                bgColor = VoltaGreen.copy(alpha = 0.12f)
                            )
                            TagBadge(
                                text = "$activeOutletsCount/$totalOutlets Outlets Active",
                                color = if (activeOutletsCount > 0) VoltaBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                bgColor = if (activeOutletsCount > 0) VoltaBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Global Master Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                strips.forEach { s -> viewModel.toggleMaster(s.mac, true) }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("global_turn_all_on_button")
                        ) {
                            Icon(Icons.Default.Power, contentDescription = "All On", modifier = Modifier.size(15.dp), tint = VoltaGreen)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("All ON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                strips.forEach { s -> viewModel.toggleMaster(s.mac, false) }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("global_turn_all_off_button")
                        ) {
                            Icon(Icons.Default.PowerOff, contentDescription = "All Off", modifier = Modifier.size(15.dp), tint = VoltaRed)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("All OFF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = { viewModel.refresh() },
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                                .testTag("refresh_strips_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Horizontal Device Filter Chips (if multiple strips)
        if (strips.size > 1) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedFilterMac == null,
                            onClick = { selectedFilterMac = null },
                            label = { Text("All Devices (${strips.size})", fontSize = 12.sp) }
                        )
                    }
                    items(strips) { s ->
                        FilterChip(
                            selected = selectedFilterMac == s.mac,
                            onClick = { selectedFilterMac = s.mac },
                            leadingIcon = {
                                StatusDot(isOnline = s.online, modifier = Modifier.size(6.dp))
                            },
                            label = {
                                Text("${s.displayName} (${String.format("%.0f", s.totalPowerW)}W)", fontSize = 12.sp)
                            }
                        )
                    }
                }
            }
        }

        // Strips List
        if (filteredStrips.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
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
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "No strips",
                            tint = VoltaBlue,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (strips.isEmpty()) "No Power Strips Paired" else "No strips match filter",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (strips.isEmpty()) "To pair a strip, switch to the Setup tab and use the SoftAP handshake." else "Select 'All Devices' to view all strips.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredStrips) { strip ->
                EnhancedPowerStripCard(
                    strip = strip,
                    timers = timers,
                    onToggleOutlet = { outletN, st -> viewModel.toggleOutlet(strip.mac, outletN, st) },
                    onToggleMaster = { st -> viewModel.toggleMaster(strip.mac, st) },
                    onToggleLock = { outletN -> viewModel.toggleOutletLock(strip.mac, outletN) },
                    onRequestRenameOutlet = { outlet -> renameDialogTarget = Pair(strip.mac, outlet) },
                    onRequestRenameStrip = { renameStripTarget = strip },
                    onRequestTimer = { outletN -> timerSheetTarget = Pair(strip.mac, outletN) }
                )
            }
        }
    }
}

@Composable
fun EnhancedPowerStripCard(
    strip: PowerStrip,
    timers: List<OutletTimer> = emptyList(),
    onToggleOutlet: (Int, Boolean) -> Unit,
    onToggleMaster: (Boolean) -> Unit,
    onToggleLock: (Int) -> Unit,
    onRequestRenameOutlet: (Outlet) -> Unit,
    onRequestRenameStrip: () -> Unit,
    onRequestTimer: (Int) -> Unit = {}
) {
    var stripMenuExpanded by remember { mutableStateOf(false) }
    val masterTimer = timers.find { it.stripMac == strip.mac && it.outlet == 0 && it.isRunning }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Strip Identity + Master Toggle + Context Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    StatusDot(isOnline = strip.online)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = strip.displayName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "MAC: ${strip.mac} • ${strip.ip.ifBlank { "DHCP" }}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Quick Master Switch with Label
                    InteractiveToggle(
                        checked = strip.isAnyOn,
                        onCheckedChange = { onToggleMaster(it) },
                        modifier = Modifier.testTag("master_switch_${strip.mac}")
                    )

                    // Strip Context Dropdown Menu
                    Box {
                        IconButton(
                            onClick = { stripMenuExpanded = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Strip Menu",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = stripMenuExpanded,
                            onDismissRequest = { stripMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Set Master Timer / Cycle") },
                                leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    stripMenuExpanded = false
                                    onRequestTimer(0)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename Strip") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    stripMenuExpanded = false
                                    onRequestRenameStrip()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (strip.isAnyOn) "Turn All Off" else "Turn All On") },
                                leadingIcon = { Icon(Icons.Default.Power, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    stripMenuExpanded = false
                                    onToggleMaster(!strip.isAnyOn)
                                }
                            )
                        }
                    }
                }
            }

            // Strip Metrics Pill Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TagBadge(
                    text = if (strip.online) "ONLINE" else "OFFLINE",
                    color = if (strip.online) VoltaGreen else VoltaRed,
                    bgColor = (if (strip.online) VoltaGreen else VoltaRed).copy(alpha = 0.12f)
                )
                TagBadge(text = "${strip.voltageV} V")
                TagBadge(
                    text = "${String.format("%.1f", strip.totalPowerW)} W",
                    color = if (strip.totalPowerW > 100) VoltaYellow else MaterialTheme.colorScheme.onSurfaceVariant
                )
                TagBadge(text = "${String.format("%.2f", strip.totalEnergyKwh)} kWh")
                if (masterTimer != null) {
                    TagBadge(
                        text = "⏱️ ${masterTimer.formattedRemaining}",
                        color = VoltaBlue,
                        bgColor = VoltaBlue.copy(alpha = 0.15f)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Outlets List with Individual Dropdowns
            strip.outlets.forEachIndexed { idx, outlet ->
                val outletTimer = timers.find { it.stripMac == strip.mac && it.outlet == outlet.n && it.isRunning }
                EnhancedOutletRow(
                    outlet = outlet,
                    activeTimer = outletTimer,
                    onToggle = { onToggleOutlet(outlet.n, it) },
                    onToggleLock = { onToggleLock(outlet.n) },
                    onRename = { onRequestRenameOutlet(outlet) },
                    onRequestTimer = { onRequestTimer(outlet.n) }
                )
                if (idx < strip.outlets.size - 1) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EnhancedOutletRow(
    outlet: Outlet,
    activeTimer: OutletTimer? = null,
    onToggle: (Boolean) -> Unit,
    onToggleLock: () -> Unit,
    onRename: () -> Unit,
    onRequestTimer: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Outlet Number & Status Indicator
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = if (outlet.on) VoltaGreen.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = outlet.n.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (outlet.on) VoltaGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = outlet.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (outlet.locked) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = VoltaRed,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (activeTimer != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = VoltaBlue.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.clickable(onClick = onRequestTimer)
                        ) {
                            Text(
                                text = "⏱ ${activeTimer.formattedRemaining}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = VoltaBlue,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (outlet.on) "${String.format("%.1f", outlet.powerW)} W" else "0.0 W (Off)",
                        fontSize = 11.sp,
                        fontWeight = if (outlet.on && outlet.powerW > 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (outlet.on && outlet.powerW > 0) VoltaAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${String.format("%.2f", outlet.energyKwh)} kWh",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${outlet.tempC}°C",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Right: Toggle + More Actions Dropdown Menu
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Tactile Animated Switch
            InteractiveToggle(
                checked = outlet.on,
                onCheckedChange = { if (!outlet.locked) onToggle(it) },
                enabled = !outlet.locked,
                modifier = Modifier.testTag("switch_outlet_${outlet.n}")
            )

            // Context Dropdown Menu
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Outlet Actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Set Timer / Cycle") },
                        leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuExpanded = false
                            onRequestTimer()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename Outlet") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (outlet.locked) "Unlock Outlet" else "Lock Outlet (Prevent Toggle)") },
                        leadingIcon = {
                            Icon(
                                if (outlet.locked) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (outlet.locked) VoltaGreen else VoltaRed,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleLock()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (outlet.on) "Turn OFF" else "Turn ON") },
                        leadingIcon = {
                            Icon(
                                if (outlet.on) Icons.Default.PowerOff else Icons.Default.Power,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        enabled = !outlet.locked,
                        onClick = {
                            menuExpanded = false
                            onToggle(!outlet.on)
                        }
                    )
                }
            }
        }
    }
}
