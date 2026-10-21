package com.example.volta.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.model.PowerStrip
import com.example.volta.model.VoltaHome
import com.example.volta.theme.VoltaAmber
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaCyan
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaGreenBright
import com.example.volta.theme.VoltaRed
import com.example.volta.theme.VoltaSlateBorder
import com.example.volta.theme.VoltaSlateCard
import com.example.volta.theme.VoltaSlateSubCard
import java.util.Locale

/**
 * Strip Long-Press Shortcut Drawer / Menu matching Volta Web App spec:
 * Triggered on mobile touch-and-hold (450ms) on the physical strip enclosure or card.
 *
 * Options in Drawer:
 * 1. 🏠 Home & Properties: Reassign strip to another home / house via a quick dropdown, rename strip.
 * 2. ℹ️ Hardware Info & Diagnostics: Live telemetry (IP address, Firmware version, Wi-Fi RSSI in dBm, and Grid Voltage).
 * 3. 🛡️ Safety & Voltage Guard: Direct shortcut to configure Voltage & Temperature Guard thresholds and outlet locks.
 * 4. 🔄 Reboot Physical Strip: Direct shortcut to send up:reboot:0 to restart the Realtek microcontroller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StripShortcutSheet(
    strip: PowerStrip,
    homes: List<VoltaHome>,
    onDismiss: () -> Unit,
    onAssignHome: (String?) -> Unit,
    onRequestRename: () -> Unit,
    onToggleOutletLock: (Int) -> Unit,
    onOpenGuard: () -> Unit,
    onRequestReboot: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var homeDropdownExpanded by remember { mutableStateOf(false) }
    var showRebootConfirm by remember { mutableStateOf(false) }

    val currentHome = remember(homes, strip.homeId) {
        if (strip.homeId.isNullOrBlank()) null else homes.find { it.id == strip.homeId }
    }

    // Reboot Confirmation Dialog
    if (showRebootConfirm) {
        AlertDialog(
            onDismissRequest = { showRebootConfirm = false },
            icon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = VoltaCyan) },
            title = { Text("Reboot Physical Strip?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Reboot '${strip.displayName}'? The microcontroller will restart and reconnect via Wi-Fi in ~5 seconds.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRebootConfirm = false
                        onRequestReboot()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VoltaCyan, contentColor = Color.Black)
                ) {
                    Text("Reboot MCU", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebootConfirm = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = VoltaSlateCard
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0C1322),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 38.dp, height = 4.dp),
                shape = CircleShape,
                color = Color(0xFF334155)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // HEADER: Strip identity & status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF1E293B), CircleShape)
                            .border(1.dp, if (strip.online) VoltaGreenBright else VoltaRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (strip.online) VoltaGreenBright else VoltaRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = strip.displayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = strip.mac,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VoltaCyan
                            )
                            Text(
                                text = "•",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = if (strip.online) "Online (${strip.lastSeenAgo})" else "Offline",
                                fontSize = 11.sp,
                                color = if (strip.online) VoltaGreenBright else VoltaRed
                            )
                        }
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // TAB ROW - Scrollable to prevent awkward text breaking and height enlargement
            SecondaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, VoltaSlateBorder, RoundedCornerShape(10.dp)),
                containerColor = Color(0xFF1E293B),
                contentColor = VoltaCyan,
                edgePadding = 6.dp,
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedTab),
                        color = VoltaCyan
                    )
                },
                divider = { HorizontalDivider() },
                tabs = {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "Properties",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "Diagnostics",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                "Safety & Locks",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = {
                            Text(
                                "Reboot",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    )
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // TAB CONTENTS
            when (selectedTab) {
                // 1. HOME & PROPERTIES
                0 -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VoltaSlateCard,
                        border = BorderStroke(1.dp, VoltaSlateBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Home / Location Assignment",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = VoltaCyan
                            )
                            Text(
                                text = "Assign this strip to a specific Home to scope telemetry, control, and multi-user access.",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Home selector dropdown button
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF1E293B),
                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { homeDropdownExpanded = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = getHomeIconVector(currentHome?.icon ?: "home"),
                                                contentDescription = null,
                                                tint = VoltaCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = currentHome?.name ?: "All Homes (Unassigned)",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = homeDropdownExpanded,
                                    onDismissRequest = { homeDropdownExpanded = false },
                                    modifier = Modifier
                                        .background(Color(0xFF0F172A))
                                        .border(1.dp, VoltaSlateBorder, RoundedCornerShape(8.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.Home, contentDescription = null, tint = VoltaCyan, modifier = Modifier.size(16.dp))
                                                Text("All Homes (Unassigned)", color = Color.White, fontSize = 13.sp)
                                                if (strip.homeId.isNullOrBlank()) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = VoltaCyan, modifier = Modifier.size(15.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            homeDropdownExpanded = false
                                            onAssignHome(null)
                                            Toast.makeText(context, "Unassigned strip home", Toast.LENGTH_SHORT).show()
                                        }
                                    )

                                    homes.forEach { h ->
                                        val isSelected = strip.homeId == h.id
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(getHomeIconVector(h.icon), contentDescription = null, tint = VoltaCyan, modifier = Modifier.size(16.dp))
                                                    Text(h.name, color = Color.White, fontSize = 13.sp)
                                                    if (isSelected) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = VoltaCyan, modifier = Modifier.size(15.dp))
                                                    }
                                                }
                                            },
                                            onClick = {
                                                homeDropdownExpanded = false
                                                onAssignHome(h.id)
                                                Toast.makeText(context, "Assigned to '${h.name}'", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFF1E293B))

                            // Rename Action
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Custom Name / Label",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = strip.displayName,
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onRequestRename()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = VoltaCyan)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Rename", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }

                // 2. HARDWARE INFO & DIAGNOSTICS
                1 -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VoltaSlateCard,
                        border = BorderStroke(1.dp, VoltaSlateBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Hardware Diagnostics & Live Telemetry",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = VoltaCyan
                            )

                            // Diagnostic items
                            DiagnosticRow(
                                label = "IP Address",
                                value = strip.ip,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(strip.ip))
                                    Toast.makeText(context, "IP copied: ${strip.ip}", Toast.LENGTH_SHORT).show()
                                }
                            )

                            DiagnosticRow(
                                label = "MAC Address",
                                value = strip.mac,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(strip.mac))
                                    Toast.makeText(context, "MAC copied: ${strip.mac}", Toast.LENGTH_SHORT).show()
                                }
                            )

                            DiagnosticRow(
                                label = "Model",
                                value = strip.model
                            )

                            DiagnosticRow(
                                label = "Firmware",
                                value = strip.fw
                            )

                            DiagnosticRow(
                                label = "Wi-Fi RSSI",
                                value = "${strip.rssi} dBm (${getSignalLabel(strip.rssi)})",
                                valueColor = getSignalColor(strip.rssi)
                            )

                            DiagnosticRow(
                                label = "Grid Voltage",
                                value = "${String.format(Locale.US, "%.1f", strip.voltageV)} V",
                                valueColor = VoltaCyan
                            )

                            DiagnosticRow(
                                label = "Total Active Load",
                                value = "${String.format(Locale.US, "%.1f", strip.totalPowerW)} W",
                                valueColor = VoltaGreenBright
                            )

                            DiagnosticRow(
                                label = "Last Telemetry",
                                value = strip.lastSeenAgo
                            )
                        }
                    }
                }

                // 3. SAFETY / LOCKS
                2 -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VoltaSlateCard,
                        border = BorderStroke(1.dp, VoltaSlateBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Socket Child & Safety Locks",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = VoltaCyan
                            )
                            Text(
                                text = "Locked sockets cannot be turned off accidentally or by physical pushbuttons.",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Batch Lock & Unlock Controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        strip.outlets.forEach { if (!it.locked) onToggleOutletLock(it.n) }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = VoltaAmber),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Lock All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }

                                OutlinedButton(
                                    onClick = {
                                        strip.outlets.forEach { if (it.locked) onToggleOutletLock(it.n) }
                                    },
                                    border = BorderStroke(1.dp, VoltaSlateBorder),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Unlock All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            // Per-outlet lock toggle rows
                            var localLockedOutlets by remember(strip.mac, strip.outlets) {
                                mutableStateOf(strip.outlets.associate { it.n to it.locked })
                            }
                            LaunchedEffect(strip.outlets) {
                                localLockedOutlets = strip.outlets.associate { it.n to it.locked }
                            }

                            strip.outlets.forEach { outlet ->
                                val isOutletLocked = localLockedOutlets[outlet.n] ?: outlet.locked
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = VoltaSlateSubCard,
                                    border = BorderStroke(1.dp, if (isOutletLocked) VoltaAmber else VoltaSlateBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val nextState = !isOutletLocked
                                                localLockedOutlets = localLockedOutlets + (outlet.n to nextState)
                                                onToggleOutletLock(outlet.n)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isOutletLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                                contentDescription = null,
                                                tint = if (isOutletLocked) VoltaAmber else Color(0xFF64748B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = outlet.name.ifBlank { "Outlet ${outlet.n}" },
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = if (isOutletLocked) "LOCKED • Protection active" else "UNLOCKED • Freely switchable",
                                                    fontSize = 10.sp,
                                                    color = if (isOutletLocked) VoltaAmber else Color(0xFF94A3B8)
                                                )
                                            }
                                        }

                                        Switch(
                                            checked = isOutletLocked,
                                            onCheckedChange = { next ->
                                                localLockedOutlets = localLockedOutlets + (outlet.n to next)
                                                onToggleOutletLock(outlet.n)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = VoltaAmber,
                                                checkedTrackColor = VoltaAmber.copy(alpha = 0.35f)
                                            )
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFF1E293B))

                            // Voltage Guard Shortcut
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Voltage Guard Protection",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (strip.guard.enabled) "ARMED (${strip.guard.minV.toInt()}V - ${strip.guard.maxV.toInt()}V)" else "DISARMED",
                                        fontSize = 11.sp,
                                        color = if (strip.guard.enabled) VoltaCyan else Color(0xFF94A3B8)
                                    )
                                }
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onOpenGuard()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = VoltaBlue),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Configure Guard", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 4. REBOOT
                3 -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VoltaSlateCard,
                        border = BorderStroke(1.dp, VoltaSlateBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = VoltaCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Reboot Physical Strip",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Text(
                                text = "Dispatches the 'up:reboot:0' command to physically restart the Realtek microcontroller. Use this if the strip's Wi-Fi telemetry becomes unresponsive.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = VoltaAmber.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, VoltaAmber.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = VoltaAmber, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Outlets will maintain their physical latch state during MCU reboot.",
                                        fontSize = 11.sp,
                                        color = VoltaAmber
                                    )
                                }
                            }

                            Button(
                                onClick = { showRebootConfirm = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reboot_strip_action_btn")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reboot Strip Now", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    valueColor: Color = Color.White,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF94A3B8)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            if (onCopy != null) {
                IconButton(onClick = onCopy, modifier = Modifier.size(22.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy $label",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

private fun getSignalLabel(rssi: Int): String {
    return when {
        rssi >= -60 -> "Strong"
        rssi >= -75 -> "Good"
        rssi >= -85 -> "Fair"
        else -> "Weak"
    }
}

private fun getSignalColor(rssi: Int): Color {
    return when {
        rssi >= -60 -> VoltaGreenBright
        rssi >= -75 -> VoltaGreen
        rssi >= -85 -> VoltaAmber
        else -> VoltaRed
    }
}
