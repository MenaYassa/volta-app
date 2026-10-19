package com.example.volta.ui.strips

import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.model.Outlet
import com.example.volta.model.PowerStrip
import com.example.volta.theme.VoltaAmber
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaCyan
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaGreenBright
import com.example.volta.theme.VoltaGreenText
import com.example.volta.theme.VoltaOrange
import com.example.volta.theme.VoltaRed
import com.example.volta.theme.VoltaSlateBorder
import com.example.volta.theme.VoltaSlateCard
import com.example.volta.theme.VoltaSlateSubCard
import com.example.volta.ui.components.GuardConfigSheet
import com.example.volta.ui.components.HomeSwitcherBar
import com.example.volta.ui.components.InteractiveToggle
import com.example.volta.ui.components.ManageHomesDialog
import com.example.volta.ui.components.MiniMetricTile
import com.example.volta.ui.components.PhysicalStripEnclosure
import com.example.volta.ui.components.PillBadge
import com.example.volta.ui.components.RenameDialog
import com.example.volta.ui.components.SafetyGuardAlertBanner
import com.example.volta.ui.components.SafetyGuardStatusBadge
import com.example.volta.ui.components.SignedOutPrompt
import com.example.volta.ui.components.StatBox
import com.example.volta.ui.components.StatusDot
import com.example.volta.ui.components.StripShortcutSheet
import com.example.volta.ui.components.TagBadge
import com.example.volta.ui.schedules.CreateTimerSheet
import com.example.volta.viewmodel.VoltaViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StripsScreen(
    viewModel: VoltaViewModel,
    modifier: Modifier = Modifier
) {
    val strips by viewModel.strips.collectAsState()
    val whoAmI by viewModel.whoAmI.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val authRequired by viewModel.authRequired.collectAsState()
    val homes by viewModel.homes.collectAsState()
    val selectedHomeId by viewModel.selectedHomeId.collectAsState()

    val isSignedOut = authRequired || settings.serverToken.isBlank()
    if (isSignedOut) {
        SignedOutPrompt(
            viewModel = viewModel,
            title = "Sign In to View Strips",
            subtitle = "You are currently signed out. Sign in with Google or enter your user token to view, configure, and claim your power strips.",
            modifier = modifier
        )
        return
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var showLimitReachedDialog by remember { mutableStateOf(false) }
    var renameStripTarget by remember { mutableStateOf<PowerStrip?>(null) }
    var renameOutletTarget by remember { mutableStateOf<Triple<String, Int, String>?>(null) }
    var deleteConfirmTarget by remember { mutableStateOf<PowerStrip?>(null) }
    var rebootConfirmTarget by remember { mutableStateOf<PowerStrip?>(null) }
    var guardSheetStrip by remember { mutableStateOf<PowerStrip?>(null) }
    var timerSheetTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var shortcutSheetStrip by remember { mutableStateOf<PowerStrip?>(null) }
    var showManageHomes by remember { mutableStateOf(false) }

    val homeFilteredStrips = remember(strips, selectedHomeId) {
        if (selectedHomeId == null) strips
        else strips.filter { it.homeId == selectedHomeId }
    }

    val maxStrips = whoAmI?.maxStrips ?: 10
    val usedStrips = strips.size
    val isLimitReached = !settings.isStandalone && whoAmI != null && usedStrips >= maxStrips

    // Hardware Safety Guard Configuration Sheet
    guardSheetStrip?.let { strip ->
        GuardConfigSheet(
            strip = strip,
            onDismiss = { guardSheetStrip = null },
            onSave = { enabled, minV, maxV, safeDelayMin, tempEnabled, maxTempC ->
                viewModel.updateVoltageGuard(strip.mac, enabled, minV, maxV, safeDelayMin, tempEnabled, maxTempC)
            },
            onReset = {
                viewModel.resetVoltageGuard(strip.mac)
                guardSheetStrip = null
            }
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

    // Plan Limit Reached Dialog
    if (showLimitReachedDialog) {
        AlertDialog(
            onDismissRequest = { showLimitReachedDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = VoltaAmber) },
            title = { Text("Strip Limit Reached", fontWeight = FontWeight.Bold) },
            text = {
                Text("You have reached your limit of $maxStrips strips. Please contact your administrator to upgrade your subscription plan.")
            },
            confirmButton = {
                Button(onClick = { showLimitReachedDialog = false }) {
                    Text("Understood")
                }
            }
        )
    }

    // Outlet Rename Dialog
    renameOutletTarget?.let { (mac, outletN, currentName) ->
        RenameDialog(
            title = "Rename Outlet $outletN",
            initialValue = currentName,
            label = "Custom Name",
            onConfirm = { newName ->
                viewModel.renameOutlet(mac, outletN, newName)
                renameOutletTarget = null
            },
            onDismiss = { renameOutletTarget = null }
        )
    }

    // Strip Rename Dialog
    renameStripTarget?.let { strip ->
        RenameDialog(
            title = "Rename Power Strip",
            initialValue = strip.displayName,
            label = "Device Display Name",
            onConfirm = { newName ->
                viewModel.renameStrip(strip.mac, newName)
                renameStripTarget = null
            },
            onDismiss = { renameStripTarget = null }
        )
    }

    // Strip Delete Confirmation Dialog
    deleteConfirmTarget?.let { strip ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            title = { Text("Unbind & Delete Strip") },
            text = { Text("Are you sure you want to remove and unbind '${strip.displayName}' (${strip.mac}) from your account? This unlinks the strip from your user profile on the server.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStrip(strip.mac)
                        deleteConfirmTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VoltaRed)
                ) {
                    Text("Unbind Strip")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteConfirmTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Remote Strip Reboot Confirmation Dialog
    rebootConfirmTarget?.let { strip ->
        AlertDialog(
            onDismissRequest = { rebootConfirmTarget = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = VoltaCyan
                    )
                    Text(
                        text = "Reboot Power Strip",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Text(
                    text = "Reboot physical strip '${strip.displayName}'? It will briefly restart and reconnect.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mac = strip.mac
                        rebootConfirmTarget = null
                        viewModel.rebootStrip(mac)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VoltaCyan,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Reboot", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { rebootConfirmTarget = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = VoltaSlateCard
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 0. Home Switcher Bar
        item {
            HomeSwitcherBar(
                homes = homes,
                selectedHomeId = selectedHomeId,
                allStrips = strips,
                onSelectHome = { viewModel.setSelectedHomeId(it) },
                onOpenManageHomes = { showManageHomes = true }
            )
        }

        // Summary & Add Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
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
                                text = "Power Strips Inventory",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val capacityLabel = if (!settings.isStandalone && whoAmI != null) {
                                "$usedStrips of $maxStrips strips used (${homeFilteredStrips.count { it.online }} online)"
                            } else {
                                "${homeFilteredStrips.count { it.online }} of ${homeFilteredStrips.size} online • ${homeFilteredStrips.sumOf { it.outlets.size }} channels"
                            }
                            Text(
                                text = capacityLabel,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                if (isLimitReached) {
                                    showLimitReachedDialog = true
                                } else {
                                    showAddDialog = true
                                }
                            },
                            colors = if (isLimitReached) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else ButtonDefaults.buttonColors(),
                            modifier = Modifier.testTag("add_strip_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add", fontSize = 13.sp)
                        }
                    }

                    if (isLimitReached) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = VoltaAmber.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, VoltaAmber.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = VoltaAmber, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Plan capacity reached ($usedStrips of $maxStrips strips). Contact administrator to add more devices.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Strips List
        if (homeFilteredStrips.isEmpty()) {
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
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "No strips",
                            tint = VoltaBlue,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val currentHomeName = homes.find { it.id == selectedHomeId }?.name
                        Text(
                            text = if (selectedHomeId != null) "No strips in '$currentHomeName'"
                                   else if (strips.isEmpty()) "No Power Strips Registered"
                                   else "No strips in this scope",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (selectedHomeId != null) "Long-press any power strip in 'All Homes' to assign it here, or switch back to All Homes."
                                   else "Tap '+ Add' above or open the Setup tab to pair a new strip over Wi-Fi.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (selectedHomeId != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.setSelectedHomeId(null) },
                                colors = ButtonDefaults.buttonColors(containerColor = VoltaBlue)
                            ) {
                                Text("Switch to All Homes")
                            }
                        }
                    }
                }
            }
        } else {
            items(homeFilteredStrips, key = { it.mac }) { strip ->
                StripInventoryCard(
                    strip = strip,
                    onToggleMaster = { viewModel.toggleMaster(strip.mac, it) },
                    onToggleOutlet = { outletN, st -> viewModel.toggleOutlet(strip.mac, outletN, st) },
                    onToggleLock = { outletN -> viewModel.toggleOutletLock(strip.mac, outletN) },
                    onRenameStrip = { renameStripTarget = strip },
                    onRenameOutlet = { outlet -> renameOutletTarget = Triple(strip.mac, outlet.n, outlet.name) },
                    onDeleteStrip = { deleteConfirmTarget = strip },
                    onRequestGuardConfig = { guardSheetStrip = strip },
                    onRequestQuickTimer = { outletN -> timerSheetTarget = Pair(strip.mac, outletN) },
                    onRequestReboot = { rebootConfirmTarget = strip },
                    onOpenShortcutDrawer = { shortcutSheetStrip = strip }
                )
            }
        }
    }

    if (showAddDialog) {
        AddStripDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { mac, name, ip ->
                viewModel.addStrip(mac, name, ip)
                showAddDialog = false
            }
        )
    }

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

    shortcutSheetStrip?.let { targetStrip ->
        StripShortcutSheet(
            strip = targetStrip,
            homes = homes,
            onDismiss = { shortcutSheetStrip = null },
            onAssignHome = { newHomeId ->
                viewModel.assignStripToHome(targetStrip.mac, newHomeId)
            },
            onRequestRename = {
                renameStripTarget = targetStrip
            },
            onToggleOutletLock = { outletN ->
                viewModel.toggleOutletLock(targetStrip.mac, outletN)
            },
            onOpenGuard = {
                guardSheetStrip = targetStrip
            },
            onRequestReboot = {
                viewModel.rebootStrip(targetStrip.mac)
            }
        )
    }
}

@Composable
fun StripInventoryCard(
    strip: PowerStrip,
    onToggleMaster: (Boolean) -> Unit,
    onToggleOutlet: (Int, Boolean) -> Unit,
    onToggleLock: (Int) -> Unit,
    onRenameStrip: () -> Unit,
    onRenameOutlet: (Outlet) -> Unit,
    onDeleteStrip: () -> Unit,
    onRequestGuardConfig: () -> Unit = {},
    onRequestQuickTimer: (Int) -> Unit = {},
    onRequestReboot: () -> Unit = {},
    onOpenShortcutDrawer: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var isExpanded by remember { mutableStateOf(false) }
    val cleanMac = strip.mac.replace(":", "").uppercase()
    val maxTemp = strip.outlets.maxOfOrNull { it.tempC } ?: 28
    val totalEnergyKwh = strip.outlets.sumOf { it.energyKwh }
    val activeCount = strip.outlets.count { it.on }
    val currentGuard = strip.guard
    val guardTripped = currentGuard.tripped || currentGuard.tempTripped
    val guardEnabled = currentGuard.enabled
    val allOutletsOn = strip.online && strip.outlets.isNotEmpty() && activeCount == strip.outlets.size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(strip.mac) {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenShortcutDrawer()
                    }
                )
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VoltaSlateCard),
        border = BorderStroke(1.dp, if (guardTripped) VoltaRed else VoltaSlateBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Priority Alert Banner: Hardware Safety Guard Tripped
            if (guardTripped) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = VoltaRed.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, VoltaRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRequestGuardConfig() }
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = VoltaRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (currentGuard.tempTripped) "🔥 OVERHEAT TRIPPED (${currentGuard.tripTempC ?: 65.0}°C)"
                                       else "🚨 VOLTAGE TRIPPED (${currentGuard.faultType ?: "SURGE/BROWNOUT"})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = VoltaRed
                            )
                            Text(
                                text = "Outlets safely isolated. Tap to inspect & reset.",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = VoltaRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // STRIP HEADER (.strip-head) matching Web App
            // Primary row: Name (full available space), status dot, and action controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle icon ⠿
                Text(
                    text = "⠿",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(end = 6.dp)
                )

                // Glowing status dot
                val dotColor = when {
                    !strip.online -> VoltaRed
                    allOutletsOn -> VoltaGreenBright
                    activeCount > 0 -> Color(0xFF4ADE80)
                    else -> Color(0xFF64748B)
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(dotColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))

                // Strip Name + Inline rename pencil (occupying full available width so name is never prematurely cut off)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = strip.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = onRenameStrip,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename strip",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Header Actions: Drawer Menu, Reboot & Remove (single clean icon cluster)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Action: Strip Shortcut Drawer / Diagnostics (⋮)
                    IconButton(
                        onClick = onOpenShortcutDrawer,
                        modifier = Modifier.size(28.dp).testTag("strip_drawer_btn_${strip.mac}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Strip Settings & Diagnostics",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Action: Reboot 🔄
                    IconButton(
                        onClick = onRequestReboot,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart strip",
                            tint = VoltaCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Action: Delete / Unbind 🗑
                    IconButton(
                        onClick = onDeleteStrip,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove strip",
                            tint = VoltaRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Sub-row: Metadata Tags (Online status, MAC, Home Scope, and the ONE Guard Button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Online Tag
                TagBadge(
                    text = if (strip.online) "● Online" else "○ Offline",
                    color = if (strip.online) VoltaGreenText else VoltaRed,
                    bgColor = VoltaSlateSubCard
                )

                // Monospace MAC
                TagBadge(
                    text = cleanMac,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    bgColor = VoltaSlateSubCard
                )

                // Home Scope badge if assigned
                if (!strip.homeId.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = VoltaCyan.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, VoltaCyan.copy(alpha = 0.4f)),
                        modifier = Modifier.clickable { onOpenShortcutDrawer() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = VoltaCyan, modifier = Modifier.size(11.dp))
                            Text("Assigned Home", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = VoltaCyan)
                        }
                    }
                }

                // The ONE Guard Button (compact pill showing shield icon and state)
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = if (guardTripped) VoltaRed.copy(alpha = 0.2f)
                           else if (guardEnabled) Color(0x1F38BDF8)
                           else VoltaSlateSubCard,
                    border = BorderStroke(
                        1.dp,
                        if (guardTripped) VoltaRed
                        else if (guardEnabled) VoltaCyan.copy(alpha = 0.6f)
                        else VoltaSlateBorder
                    ),
                    modifier = Modifier.clickable { onRequestGuardConfig() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (guardTripped) VoltaRed else if (guardEnabled) VoltaCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (guardTripped) "Guard TRIPPED"
                                   else if (guardEnabled) "Guard Armed"
                                   else "Guard Off",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (guardTripped) VoltaRed else if (guardEnabled) VoltaCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // PHYSICAL POWER STRIP ENCLOSURE (.strip-physical)
            // Tap & hold 450ms anywhere on enclosure triggers the Strip Shortcut Drawer
            PhysicalStripEnclosure(
                strip = strip,
                onToggleOutlet = onToggleOutlet,
                onToggleMaster = onToggleMaster,
                onLongClickEnclosure = onOpenShortcutDrawer
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 6-METRIC TELEMETRY GRID (.strip-metrics)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isWide = maxWidth >= 550.dp
                if (isWide) {
                    // Single row of 6 on wide screens
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MiniMetricTile(
                            label = "ACTIVE POWER",
                            value = "${strip.totalPowerW.toInt()} W",
                            modifier = Modifier.weight(1f)
                        )
                        MiniMetricTile(
                            label = "TOTAL CURRENT",
                            value = "${String.format(Locale.US, "%.2f", strip.currentA)} A",
                            modifier = Modifier.weight(1f)
                        )
                        MiniMetricTile(
                            label = "GRID VOLTAGE",
                            value = "${String.format(Locale.US, "%.1f", strip.voltageV)} V",
                            modifier = Modifier.weight(1f)
                        )
                        MiniMetricTile(
                            label = "MAX TEMP",
                            value = "${maxTemp}°C",
                            modifier = Modifier.weight(1f)
                        )
                        MiniMetricTile(
                            label = "ENERGY",
                            value = "${String.format(Locale.US, "%.2f", totalEnergyKwh)} kWh",
                            modifier = Modifier.weight(1f)
                        )
                        MiniMetricTile(
                            label = "ACTIVE OUTLETS",
                            value = "$activeCount / ${strip.outlets.size}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    // Responsive 2 rows of 3 on mobile (each metric has ~100dp width)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MiniMetricTile(
                                label = "ACTIVE POWER",
                                value = "${strip.totalPowerW.toInt()} W",
                                modifier = Modifier.weight(1f)
                            )
                            MiniMetricTile(
                                label = "TOTAL CURRENT",
                                value = "${String.format(Locale.US, "%.2f", strip.currentA)} A",
                                modifier = Modifier.weight(1f)
                            )
                            MiniMetricTile(
                                label = "GRID VOLTAGE",
                                value = "${String.format(Locale.US, "%.1f", strip.voltageV)} V",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MiniMetricTile(
                                label = "MAX TEMP",
                                value = "${maxTemp}°C",
                                modifier = Modifier.weight(1f)
                            )
                            MiniMetricTile(
                                label = "ENERGY",
                                value = "${String.format(Locale.US, "%.2f", totalEnergyKwh)} kWh",
                                modifier = Modifier.weight(1f)
                            )
                            MiniMetricTile(
                                label = "ACTIVE OUTLETS",
                                value = "$activeCount / ${strip.outlets.size}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // EXPAND / COLLAPSE CHEVRON BUTTON (.strip-toggle-expand)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = VoltaSlateSubCard,
                    border = BorderStroke(1.dp, VoltaSlateBorder),
                    modifier = Modifier.clickable { isExpanded = !isExpanded }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "Collapse Outlets" else "Show Details (${strip.outlets.size} Outlets)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // DETAILED OUTLET SUB-ROWS (Collapsible)
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (outlet in strip.outlets) {
                        StripOutletTile(
                            outlet = outlet,
                            onToggle = { onToggleOutlet(outlet.n, !outlet.on) },
                            onToggleLock = { onToggleLock(outlet.n) },
                            onRename = { onRenameOutlet(outlet) },
                            onTimer = { onRequestQuickTimer(outlet.n) }
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun StripOutletTile(
    outlet: Outlet,
    onToggle: () -> Unit,
    onToggleLock: () -> Unit,
    onRename: () -> Unit,
    onTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (outlet.on) Color(0x1F16A34A) else Color(0xFF131C31)
    val borderColor = if (outlet.on) Color(0x5916A34A) else Color(0xFF24314F)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Sleek Custom Switch (.sw in web app) with comfortable touch target
            val thumbOffset by animateDpAsState(
                targetValue = if (outlet.on) 20.dp else 2.dp,
                label = "switch_thumb"
            )
            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (outlet.on) VoltaGreen else Color(0xFF334155))
                    .clickable(enabled = !outlet.locked) { onToggle() }
                    .padding(2.dp)
                    .testTag("switch_outlet_${outlet.n}"),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(x = thumbOffset.roundToPx(), y = 0) }
                        .size(24.dp)
                        .shadow(elevation = 2.dp, shape = CircleShape)
                        .background(Color.White, CircleShape)
                )
            }

            // Center Outlet Info: Title Row & Clean Specs Row
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Title row: Name + Warning Tags + edit pencil
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = outlet.name,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!outlet.overloadOk) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = VoltaRed.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, VoltaRed)
                        ) {
                            Text(
                                text = "⚠️ OVERLOAD",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = VoltaRed,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else if (!outlet.overheatOk) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = VoltaOrange.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, VoltaOrange)
                        ) {
                            Text(
                                text = "⚠️ OVERHEAT",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = VoltaOrange,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onRename,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename outlet",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Specs row: [ON/OFF] • [POWER W] • [CURRENT A] • [ENERGY kWh] • [TEMP °C]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (outlet.on) "ON" else "OFF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (outlet.on) VoltaGreenText else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = if (outlet.on) "${String.format(Locale.US, "%.1f", outlet.powerW)} W" else "0 W",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (outlet.on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.2f", outlet.currentA)} A",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (outlet.on && outlet.currentA > 0) VoltaCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.2f", outlet.energyKwh)} kWh",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "${outlet.tempC}°C",
                        fontSize = 11.sp,
                        color = if (outlet.tempC > 50) VoltaOrange else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Right Action Buttons: Dedicated standard touch targets that respond on FIRST touch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Quick Timer Shortcut Button (Standard IconButton touch delegate)
                IconButton(
                    onClick = onTimer,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("timer_button_${outlet.n}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(VoltaSlateSubCard, CircleShape)
                            .border(1.dp, VoltaSlateBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Quick Timer",
                            tint = if (outlet.on) VoltaCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Lock Toggle Button (Standard IconButton touch delegate for instant 1st touch response)
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("lock_button_${outlet.n}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                if (outlet.locked) VoltaAmber.copy(alpha = 0.2f) else VoltaSlateSubCard,
                                CircleShape
                            )
                            .border(
                                1.dp,
                                if (outlet.locked) VoltaAmber else VoltaSlateBorder,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (outlet.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (outlet.locked) "Locked" else "Unlocked",
                            tint = if (outlet.locked) VoltaAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddStripDialog(
    onDismiss: () -> Unit,
    onAdd: (mac: String, name: String, ip: String) -> Unit
) {
    var mac by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register & Claim Strip", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter the strip's 12-digit MAC address (from sticker or TONLY_TAP suffix). It will be linked directly to your signed-in Google account.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = { Text("MAC Address (12 Hex digits)") },
                    placeholder = { Text("e.g. 88D039132171") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_strip_mac_input")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. Living Room MTTL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Strip Local IP (optional)") },
                    placeholder = { Text("e.g. 192.168.1.150") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (mac.isNotBlank()) onAdd(mac, name, ip) },
                enabled = mac.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_strip_button")
            ) {
                Text("Claim & Register")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
