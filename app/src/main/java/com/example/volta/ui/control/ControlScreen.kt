package com.example.volta.ui.control

import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import java.util.Collections
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.model.Outlet
import com.example.volta.model.PowerStrip
import com.example.volta.theme.VoltaAmber
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaCyan
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaGreenBg
import com.example.volta.theme.VoltaGreenBright
import com.example.volta.theme.VoltaGreenText
import com.example.volta.theme.VoltaOrange
import com.example.volta.theme.VoltaRed
import com.example.volta.theme.VoltaSlateBorder
import com.example.volta.theme.VoltaSlateCard
import com.example.volta.theme.VoltaSlateSubCard
import com.example.volta.theme.VoltaYellow
import com.example.volta.ui.components.GuardConfigSheet
import com.example.volta.ui.components.HomeSwitcherBar
import com.example.volta.ui.components.ManageHomesDialog
import com.example.volta.ui.components.PhysicalStripEnclosure
import com.example.volta.ui.components.RenameDialog
import com.example.volta.ui.components.SignedOutPrompt
import com.example.volta.ui.components.StatusDot
import com.example.volta.ui.components.StripShortcutSheet
import com.example.volta.ui.components.TagBadge
import com.example.volta.ui.schedules.CreateTimerSheet
import com.example.volta.viewmodel.VoltaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(
    viewModel: VoltaViewModel,
    modifier: Modifier = Modifier
) {
    val strips by viewModel.strips.collectAsState()
    val timers by viewModel.timers.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val authRequired by viewModel.authRequired.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val homes by viewModel.homes.collectAsState()
    val selectedHomeId by viewModel.selectedHomeId.collectAsState()

    // Enforce authentication gate: no strips or controls visible when signed out
    val isSignedOut = authRequired || settings.serverToken.isBlank()
    if (isSignedOut) {
        SignedOutPrompt(
            viewModel = viewModel,
            title = "Sign In to Control Strips",
            subtitle = "You are currently signed out. Sign in with your Volta Google account or enter your user token to view live strip telemetry and toggle outlets.",
            modifier = modifier
        )
        return
    }

    var selectedFilterMac by remember { mutableStateOf<String?>(null) }
    var renameDialogTarget by remember { mutableStateOf<Pair<String, Outlet>?>(null) }
    var renameStripTarget by remember { mutableStateOf<PowerStrip?>(null) }
    var rebootConfirmTarget by remember { mutableStateOf<PowerStrip?>(null) }
    var timerSheetTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var guardSheetMac by remember { mutableStateOf<String?>(null) }
    var shortcutSheetMac by remember { mutableStateOf<String?>(null) }
    val guardSheetStrip = remember(strips, guardSheetMac) {
        if (guardSheetMac == null) null else strips.find { it.mac == guardSheetMac }
    }
    val shortcutSheetStrip = remember(strips, shortcutSheetMac) {
        if (shortcutSheetMac == null) null else strips.find { it.mac == shortcutSheetMac }
    }
    var showManageHomes by remember { mutableStateOf(false) }
    var lastUpdateTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var allStripsExpanded by remember { mutableStateOf(false) }
    var deleteConfirmTarget by remember { mutableStateOf<PowerStrip?>(null) }

    // Auto-refresh timestamp ticker every 5s matching the web app
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(5000L)
            lastUpdateTick = System.currentTimeMillis()
        }
    }

    // Scoped filtering: first by selected home, then by specific strip chip
    val homeFilteredStrips = remember(strips, selectedHomeId) {
        if (selectedHomeId == null) strips
        else strips.filter { it.homeId == selectedHomeId }
    }
    val filteredStrips = remember(homeFilteredStrips, selectedFilterMac) {
        if (selectedFilterMac == null) homeFilteredStrips
        else homeFilteredStrips.filter { it.mac == selectedFilterMac }
    }

    var draggedMac by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var orderedStrips by remember(filteredStrips) { mutableStateOf(filteredStrips) }

    LaunchedEffect(filteredStrips) {
        if (draggedMac == null) {
            orderedStrips = filteredStrips
        }
    }

    val totalLoadW = homeFilteredStrips.sumOf { it.totalPowerW }
    val totalOutlets = homeFilteredStrips.sumOf { it.outlets.size }
    val activeOutletsCount = homeFilteredStrips.sumOf { it.outlets.count { o -> o.on } }
    val onlineStripsCount = homeFilteredStrips.count { it.online }

    val timeFormat = remember { SimpleDateFormat("h:mm:ss a", Locale.getDefault()) }

    // Hardware Safety Guard Configuration Sheet
    guardSheetStrip?.let { strip ->
        GuardConfigSheet(
            strip = strip,
            onDismiss = { guardSheetMac = null },
            onSave = { enabled, minV, maxV, safeDelayMin, tempEnabled, maxTempC ->
                viewModel.updateVoltageGuard(strip.mac, enabled, minV, maxV, safeDelayMin, tempEnabled, maxTempC)
            },
            onReset = {
                viewModel.resetVoltageGuard(strip.mac)
                guardSheetMac = null
            }
        )
    }

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

    // Remote Strip Delete / Unclaim Confirmation Dialog
    deleteConfirmTarget?.let { strip ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = VoltaRed
                    )
                    Text(
                        text = "Remove Power Strip",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Text(
                    text = "Remove '${strip.displayName}' (${strip.mac}) from your account? You can reclaim it anytime in Setup using its MAC.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mac = strip.mac
                        deleteConfirmTarget = null
                        viewModel.deleteStrip(mac)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VoltaRed,
                        contentColor = Color.White
                    )
                ) {
                    Text("Remove", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTarget = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = VoltaSlateCard
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 0. HOME SWITCHER BAR matching Web App (.home-switcher-bar)
        item {
            HomeSwitcherBar(
                homes = homes,
                selectedHomeId = selectedHomeId,
                allStrips = strips,
                onSelectHome = { viewModel.setSelectedHomeId(it) },
                onOpenManageHomes = { showManageHomes = true }
            )
        }

        // TOP HERO: Unified Overview Card with Radial Gauge & Fleet KPIs matching Web App
        item {
            val anyGuardTripped = homeFilteredStrips.any { it.guard.tripped || it.guard.tempTripped }
            UnifiedOverviewCard(
                totalLoadW = totalLoadW,
                onlineStripsCount = onlineStripsCount,
                totalStripsCount = homeFilteredStrips.size,
                activeOutletsCount = activeOutletsCount,
                totalOutletsCount = totalOutlets,
                anyGuardTripped = anyGuardTripped,
                onRefresh = {
                    viewModel.refresh()
                    lastUpdateTick = System.currentTimeMillis()
                },
                lastUpdateText = timeFormat.format(Date(lastUpdateTick)),
                allExpanded = allStripsExpanded,
                onToggleExpandAll = {
                    allStripsExpanded = !allStripsExpanded
                }
            )
        }

        // Horizontal Device Filter Chips (if multiple strips in current view)
        if (homeFilteredStrips.size > 1) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedFilterMac == null,
                            onClick = { selectedFilterMac = null },
                            label = { Text("All in Home (${homeFilteredStrips.size})", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VoltaBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    items(homeFilteredStrips) { s ->
                        FilterChip(
                            selected = selectedFilterMac == s.mac,
                            onClick = { selectedFilterMac = s.mac },
                            leadingIcon = {
                                StatusDot(isOnline = s.online, modifier = Modifier.size(6.dp), isConnecting = s.isConnectingCloud)
                            },
                            label = {
                                Text("${s.displayName} (${String.format(Locale.US, "%.0f", s.totalPowerW)}W)", fontSize = 11.sp)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VoltaBlue,
                                selectedLabelColor = Color.White
                            )
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
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = VoltaSlateCard),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "No strips",
                            tint = VoltaCyan,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val currentHomeName = homes.find { it.id == selectedHomeId }?.name
                        Text(
                            text = if (selectedHomeId != null) "No strips in '$currentHomeName'"
                                   else if (strips.isEmpty()) "No Power Strips Connected"
                                   else "No strips match filter",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (selectedHomeId != null) "Long-press any power strip in 'All Homes' to assign it here, or tap below to switch back to All Homes."
                                   else if (strips.isEmpty()) "To pair a strip, go to the Setup tab and use the SoftAP handshake."
                                   else "Select 'All in Home' to view all strips in this scope.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (selectedHomeId != null) {
                            Spacer(modifier = Modifier.height(14.dp))
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
            itemsIndexed(orderedStrips, key = { _, it -> it.mac }) { index, strip ->
                val isBeingDragged = draggedMac == strip.mac
                val canMoveUp = index > 0
                val canMoveDown = index < orderedStrips.lastIndex

                SlickPowerStripCard(
                    strip = strip,
                    allExpanded = allStripsExpanded,
                    isDragging = isBeingDragged,
                    dragOffsetY = if (isBeingDragged) dragOffsetY else 0f,
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    onDragStart = {
                        draggedMac = strip.mac
                        dragOffsetY = 0f
                    },
                    onDragDelta = { dy ->
                        dragOffsetY += dy
                        val curIdx = orderedStrips.indexOfFirst { it.mac == strip.mac }
                        val swapThreshold = 200f
                        if (curIdx != -1) {
                            if (dragOffsetY > swapThreshold && curIdx < orderedStrips.lastIndex) {
                                val mutable = orderedStrips.toMutableList()
                                Collections.swap(mutable, curIdx, curIdx + 1)
                                orderedStrips = mutable
                                dragOffsetY -= swapThreshold
                            } else if (dragOffsetY < -swapThreshold && curIdx > 0) {
                                val mutable = orderedStrips.toMutableList()
                                Collections.swap(mutable, curIdx, curIdx - 1)
                                orderedStrips = mutable
                                dragOffsetY += swapThreshold
                            }
                        }
                    },
                    onDragEnd = {
                        val finalMacs = orderedStrips.map { it.mac }
                        draggedMac = null
                        dragOffsetY = 0f
                        viewModel.reorderStrips(finalMacs)
                    },
                    onMoveUp = {
                        if (index > 0) {
                            val mutable = orderedStrips.toMutableList()
                            Collections.swap(mutable, index, index - 1)
                            orderedStrips = mutable
                            viewModel.reorderStrips(mutable.map { it.mac })
                        }
                    },
                    onMoveDown = {
                        if (index < orderedStrips.lastIndex) {
                            val mutable = orderedStrips.toMutableList()
                            Collections.swap(mutable, index, index + 1)
                            orderedStrips = mutable
                            viewModel.reorderStrips(mutable.map { it.mac })
                        }
                    },
                    homeName = homes.find { it.id == strip.homeId }?.name,
                    onToggleOutlet = { outletN, st -> viewModel.toggleOutlet(strip.mac, outletN, st) },
                    onToggleMaster = { st -> viewModel.toggleMaster(strip.mac, st) },
                    onToggleLock = { outletN -> viewModel.toggleOutletLock(strip.mac, outletN) },
                    onRequestRenameOutlet = { outlet -> renameDialogTarget = Pair(strip.mac, outlet) },
                    onRequestRenameStrip = { renameStripTarget = strip },
                    onRequestQuickTimer = { outletN -> timerSheetTarget = Pair(strip.mac, outletN) },
                    onRequestReboot = { rebootConfirmTarget = strip },
                    onRequestDelete = { deleteConfirmTarget = strip },
                    onOpenGuard = { guardSheetMac = strip.mac },
                    onOpenShortcutDrawer = { shortcutSheetMac = strip.mac }
                )
            }
        }
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

    // Strip Long-Press Shortcut Drawer / Sheet
    shortcutSheetStrip?.let { targetStrip ->
        StripShortcutSheet(
            strip = targetStrip,
            homes = homes,
            onDismiss = { shortcutSheetMac = null },
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
                guardSheetMac = targetStrip.mac
            },
            onRequestReboot = {
                viewModel.rebootStrip(targetStrip.mac)
            }
        )
    }
}

@Composable
fun UnifiedOverviewCard(
    totalLoadW: Double,
    onlineStripsCount: Int,
    totalStripsCount: Int,
    activeOutletsCount: Int,
    totalOutletsCount: Int,
    anyGuardTripped: Boolean,
    onRefresh: () -> Unit,
    lastUpdateText: String,
    allExpanded: Boolean,
    onToggleExpandAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
            // Top Section: SFN Radial Ring Gauge on Left + Live Watts & Status on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SFN Radial Ring Gauge
                UnifiedRadialRing(
                    totalWatts = totalLoadW,
                    onlineRatioText = "$onlineStripsCount/$totalStripsCount",
                    size = 84.dp
                )

                // Power readouts & Household Title
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TOTAL HOUSEHOLD LOAD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format(Locale.US, "%.1f", totalLoadW),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "W",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Active Live Stream · $lastUpdateText",
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0x1FFFFFFF), thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // 4 Column Grid matching web .unified-kpis-wrap
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UnifiedKpiCol(
                    label = "🔌 Total Strips",
                    value = "$totalStripsCount",
                    sub = "Fleet capacity",
                    valueColor = Color.White,
                    modifier = Modifier.weight(1f)
                )
                UnifiedKpiCol(
                    label = "🟢 Connected",
                    value = "$onlineStripsCount",
                    sub = "Online now",
                    valueColor = VoltaGreenBright,
                    modifier = Modifier.weight(1f)
                )
                UnifiedKpiCol(
                    label = "⚡ Outlets",
                    value = "$activeOutletsCount",
                    sub = "of $totalOutletsCount ON",
                    valueColor = VoltaCyan,
                    modifier = Modifier.weight(1f)
                )
                UnifiedKpiCol(
                    label = "🛡️ Protection",
                    value = if (anyGuardTripped) "TRIPPED" else "Armed",
                    sub = if (anyGuardTripped) "Isolated" else "Dual Safety",
                    valueColor = if (anyGuardTripped) VoltaRed else Color(0xFF38BDF8),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Actions Row directly under Hero Card matching web app
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = VoltaSlateCard,
                border = BorderStroke(1.dp, VoltaSlateBorder),
                modifier = Modifier.clickable { onRefresh() }.testTag("refresh_now_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = VoltaCyan, modifier = Modifier.size(13.dp))
                    Text("Refresh", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = VoltaCyan)
                }
            }
            Text("Updated $lastUpdateText", fontSize = 11.sp, color = Color(0xFF64748B))
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = VoltaSlateCard,
            border = BorderStroke(1.dp, VoltaSlateBorder),
            modifier = Modifier.clickable { onToggleExpandAll() }.testTag("toggle_expand_all_button")
        ) {
            Text(
                text = if (allExpanded) "Hide Outlets" else "Show All Outlets",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun UnifiedRadialRing(
    totalWatts: Double,
    onlineRatioText: String,
    modifier: Modifier = Modifier,
    maxWatts: Float = 3520f,
    size: Dp = 84.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        val pct = (totalWatts.toFloat() / maxWatts).coerceIn(0f, 1f)
        val sweepAngle = 360f * pct

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 6.5.dp.toPx()
            val diameter = size.toPx() - strokeWidth
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)

            // Track circle
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )

            // Progress arc
            if (sweepAngle > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color(0xFF3B82F6),
                            Color(0xFF22C55E),
                            Color(0xFFEAB308),
                            Color(0xFF3B82F6)
                        )
                    ),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format(Locale.US, "%.0f W", totalWatts),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = "Fleet Load",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun UnifiedKpiCol(
    label: String,
    value: String,
    sub: String,
    valueColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF94A3B8),
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            color = valueColor,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = sub,
            fontSize = 9.sp,
            color = Color(0xFF64748B),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun SlickPowerStripCard(
    strip: PowerStrip,
    allExpanded: Boolean,
    onToggleOutlet: (Int, Boolean) -> Unit,
    onToggleMaster: (Boolean) -> Unit,
    onToggleLock: (Int) -> Unit,
    onRequestRenameOutlet: (Outlet) -> Unit,
    onRequestRenameStrip: () -> Unit,
    onRequestQuickTimer: (Int) -> Unit,
    onOpenGuard: () -> Unit,
    modifier: Modifier = Modifier,
    homeName: String? = null,
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onDragStart: () -> Unit = {},
    onDragDelta: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onRequestReboot: () -> Unit = {},
    onRequestDelete: () -> Unit = {},
    onOpenShortcutDrawer: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var isExpanded by remember(allExpanded) { mutableStateOf(allExpanded) }
    val cleanMac = strip.mac.replace(":", "").uppercase()
    val maxTemp = strip.outlets.maxOfOrNull { it.tempC } ?: 28
    val totalEnergyKwh = strip.outlets.sumOf { it.energyKwh }
    val activeCount = strip.outlets.count { it.on }
    val currentGuard = strip.guard
    val guardTripped = currentGuard.tripped || currentGuard.tempTripped
    val guardEnabled = currentGuard.enabled
    val allOutletsOn = strip.online && strip.outlets.isNotEmpty() && activeCount == strip.outlets.size

    Card(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 10f else 1f)
            .graphicsLayer {
                translationY = if (isDragging) dragOffsetY else 0f
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
                shadowElevation = if (isDragging) 16f else 0f
            }
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
        border = BorderStroke(1.dp, if (guardTripped) VoltaRed else if (isDragging) VoltaCyan else VoltaSlateBorder)
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
                        .clickable { onOpenGuard() }
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

            // STRIP HEADER matching Web App (.strip-head)
            // Primary row: Name (full available space), status dot, and action controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interactive Drag handle icon ⠿ with Drag & Drop Gesture + Click Menu
                var showReorderMenu by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .testTag("drag_handle_${strip.mac}")
                        .padding(end = 6.dp)
                        .pointerInput(strip.mac) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDragDelta(dragAmount.y)
                                },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() }
                            )
                        }
                        .clickable {
                            showReorderMenu = true
                        }
                ) {
                    Text(
                        text = "⠿",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDragging) VoltaCyan else Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    DropdownMenu(
                        expanded = showReorderMenu,
                        onDismissRequest = { showReorderMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Move Up ⬆") },
                            onClick = {
                                showReorderMenu = false
                                onMoveUp()
                            },
                            enabled = canMoveUp
                        )
                        DropdownMenuItem(
                            text = { Text("Move Down ⬇") },
                            onClick = {
                                showReorderMenu = false
                                onMoveDown()
                            },
                            enabled = canMoveDown
                        )
                    }
                }

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
                        onClick = onRequestRenameStrip,
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

                // Header Actions: Reboot & Remove (single clean icon cluster)
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

                    // Action: Remove / Unclaim 🗑
                    IconButton(
                        onClick = onRequestDelete,
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

            // Sub-row: Metadata Tags (Online status, Home assignment name, and the ONE Guard Button)
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

                // Home Name badge if assigned (replaces generic 'Home Scoped' tag)
                if (!strip.homeId.isNullOrBlank() || homeName != null) {
                    val assignedName = homeName ?: "Home"
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = VoltaCyan.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, VoltaCyan.copy(alpha = 0.4f)),
                        modifier = Modifier.clickable { onOpenShortcutDrawer() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = VoltaCyan,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = assignedName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = VoltaCyan
                            )
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
                    modifier = Modifier.clickable { onOpenGuard() }
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

            // SFN PHYSICAL POWER STRIP ENCLOSURE (.strip-physical)
            // Tap & hold 450ms anywhere on enclosure triggers the Strip Shortcut Drawer
            PhysicalStripEnclosure(
                strip = strip,
                onToggleOutlet = onToggleOutlet,
                onToggleMaster = onToggleMaster,
                onLongClickEnclosure = onOpenShortcutDrawer
            )

            Spacer(modifier = Modifier.height(8.dp))

            // EXPAND / COLLAPSE BUTTON (.strip-toggle-expand)
            // Collapses by default both the 6 telemetry data cards AND individual outlets
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isExpanded) VoltaSlateSubCard else Color.Transparent,
                    border = BorderStroke(1.dp, VoltaSlateBorder),
                    modifier = Modifier
                        .clickable { isExpanded = !isExpanded }
                        .testTag("expand_strip_${strip.mac}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "Hide telemetry & outlets" else "Telemetry & outlets (${strip.outlets.size})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse telemetry and outlets" else "Expand telemetry and outlets",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // EXPANDABLE DRAWER: 6 TELEMETRY METRIC TILES + DETAILED OUTLET CARDS
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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

                    // DETAILED OUTLET CARDS DRAWER (.outs-grid)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (outlet in strip.outlets) {
                            SlickOutletTile(
                                outlet = outlet,
                                onToggle = { onToggleOutlet(outlet.n, !outlet.on) },
                                onToggleLock = { onToggleLock(outlet.n) },
                                onRename = { onRequestRenameOutlet(outlet) },
                                onTimer = { onRequestQuickTimer(outlet.n) }
                            )
                        }
                    }
                }
            }
        }
    }
}



@Composable
private fun MiniMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF131C31),
        border = BorderStroke(1.dp, Color(0xFF24314F))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MiniTelemetryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = VoltaSlateSubCard,
        border = BorderStroke(1.dp, VoltaSlateBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PillBadge(
    text: String,
    color: Color,
    dotColor: Color? = null
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = VoltaSlateSubCard,
        border = BorderStroke(1.dp, VoltaSlateBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(dotColor, CircleShape)
                )
            }
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun SlickOutletTile(
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
