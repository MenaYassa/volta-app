package com.example.volta.ui.strips

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.volta.theme.VoltaRed
import com.example.volta.ui.components.InteractiveToggle
import com.example.volta.ui.components.RenameDialog
import com.example.volta.ui.components.StatBox
import com.example.volta.ui.components.StatusDot
import com.example.volta.ui.components.TagBadge
import com.example.volta.viewmodel.VoltaViewModel

@Composable
fun StripsScreen(
    viewModel: VoltaViewModel,
    modifier: Modifier = Modifier
) {
    val strips by viewModel.strips.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var renameStripTarget by remember { mutableStateOf<PowerStrip?>(null) }
    var renameOutletTarget by remember { mutableStateOf<Triple<String, Int, String>?>(null) }
    var deleteConfirmTarget by remember { mutableStateOf<PowerStrip?>(null) }

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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                            Text(
                                text = "${strips.count { it.online }} of ${strips.size} online • ${strips.sumOf { it.outlets.size }} managed channels",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("add_strip_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Strips List
        if (strips.isEmpty()) {
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
                        Text(
                            text = "No Power Strips Registered",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap '+ Add' above or open the Setup tab to pair a new strip over Wi-Fi.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(strips) { strip ->
                StripInventoryCard(
                    strip = strip,
                    onToggleMaster = { viewModel.toggleMaster(strip.mac, it) },
                    onToggleOutlet = { outletN, st -> viewModel.toggleOutlet(strip.mac, outletN, st) },
                    onToggleLock = { outletN -> viewModel.toggleOutletLock(strip.mac, outletN) },
                    onRenameStrip = { renameStripTarget = strip },
                    onRenameOutlet = { outlet -> renameOutletTarget = Triple(strip.mac, outlet.n, outlet.name) },
                    onDeleteStrip = { deleteConfirmTarget = strip }
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
}

@Composable
fun StripInventoryCard(
    strip: PowerStrip,
    onToggleMaster: (Boolean) -> Unit,
    onToggleOutlet: (Int, Boolean) -> Unit,
    onToggleLock: (Int) -> Unit,
    onRenameStrip: () -> Unit,
    onRenameOutlet: (Outlet) -> Unit,
    onDeleteStrip: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
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
                        Text(
                            text = strip.displayName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "MAC: ${strip.mac} • ${strip.ip.ifBlank { "Auto IP" }}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Quick Master Toggle
                    InteractiveToggle(
                        checked = strip.isAnyOn,
                        onCheckedChange = onToggleMaster
                    )

                    // Context Menu
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Strip Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename Strip") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    menuExpanded = false
                                    onRenameStrip()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (strip.isAnyOn) "Turn All Off" else "Turn All On") },
                                leadingIcon = {
                                    Icon(
                                        if (strip.isAnyOn) Icons.Default.PowerOff else Icons.Default.Power,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleMaster(!strip.isAnyOn)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Strip", color = VoltaRed) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = VoltaRed, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteStrip()
                                }
                            )
                        }
                    }

                    // Expand / Collapse Chevron
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand Outlets",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Quick Metrics Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TagBadge(
                    text = if (strip.online) "ONLINE" else "OFFLINE",
                    color = if (strip.online) VoltaGreen else VoltaRed,
                    bgColor = (if (strip.online) VoltaGreen else VoltaRed).copy(alpha = 0.12f)
                )
                TagBadge(text = "${strip.voltageV} V")
                TagBadge(text = "${String.format("%.1f", strip.totalPowerW)} W")
                TagBadge(text = "${String.format("%.2f", strip.totalEnergyKwh)} kWh")
            }

            // Collapsible Outlets Section
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    strip.outlets.forEachIndexed { index, outlet ->
                        StripOutletRow(
                            outlet = outlet,
                            onToggle = { onToggleOutlet(outlet.n, it) },
                            onToggleLock = { onToggleLock(outlet.n) },
                            onRename = { onRenameOutlet(outlet) }
                        )
                        if (index < strip.outlets.size - 1) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StripOutletRow(
    outlet: Outlet,
    onToggle: (Boolean) -> Unit,
    onToggleLock: () -> Unit,
    onRename: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = if (outlet.on) VoltaGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = outlet.n.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (outlet.on) VoltaGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = outlet.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (outlet.locked) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = VoltaRed,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
                Text(
                    text = "${String.format("%.1f", outlet.powerW)} W • ${outlet.tempC}°C",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            InteractiveToggle(
                checked = outlet.on,
                onCheckedChange = { if (!outlet.locked) onToggle(it) },
                enabled = !outlet.locked
            )

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Outlet Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename Outlet") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (outlet.locked) "Unlock Outlet" else "Lock Outlet") },
                        leadingIcon = {
                            Icon(
                                if (outlet.locked) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleLock()
                        }
                    )
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
        title = { Text("Register Power Strip", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Text("Register")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
