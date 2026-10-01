package com.example.volta.ui.strips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.volta.model.PowerStrip
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaRed
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Description Card & Add Strip Button
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
                            text = "Registered Strips",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("add_strip_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Strip")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Rename power strips and individual outlets. Names are saved locally and survive app reboots.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Strips List
        items(strips, key = { it.mac }) { strip ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusDot(isOnline = strip.online)
                            Text(
                                text = strip.displayName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row {
                            IconButton(onClick = { renameStripTarget = strip }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Rename Strip",
                                    tint = VoltaBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { deleteConfirmTarget = strip }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Strip",
                                    tint = VoltaRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Tech metadata
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TagBadge(text = "MAC: ${strip.mac}")
                        TagBadge(text = "IP: ${strip.ip}")
                        TagBadge(text = "FW: ${strip.fw}")
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // Outlets
                    Text(
                        text = "Outlets & Labels:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    strip.outlets.forEach { outlet ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Outlet ${outlet.n}:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(68.dp)
                                )
                                Text(
                                    text = outlet.name,
                                    fontSize = 14.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.toggleOutletLock(strip.mac, outlet.n) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (outlet.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = "Lock",
                                        tint = if (outlet.locked) VoltaRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { renameOutletTarget = Triple(strip.mac, outlet.n, outlet.name) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Rename Outlet",
                                        tint = VoltaBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Strip Dialog
    if (showAddDialog) {
        var newMac by remember { mutableStateOf("") }
        var newName by remember { mutableStateOf("") }
        var newIp by remember { mutableStateOf("192.168.1.150") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Register Power Strip") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter the 12-character MAC address of the MTTL-W01 strip and its LAN IP.")
                    OutlinedTextField(
                        value = newMac,
                        onValueChange = { newMac = it },
                        label = { Text("MAC Address (e.g. A020A6112233)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Friendly Name (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newIp,
                        onValueChange = { newIp = it },
                        label = { Text("LAN IP Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newMac.isNotBlank()) {
                            viewModel.addStrip(newMac, newName, newIp)
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Add Strip")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Strip Dialog
    val renameStrip = renameStripTarget
    if (renameStrip != null) {
        var currentName by remember { mutableStateOf(renameStrip.name) }
        AlertDialog(
            onDismissRequest = { renameStripTarget = null },
            title = { Text("Rename Strip") },
            text = {
                OutlinedTextField(
                    value = currentName,
                    onValueChange = { currentName = it },
                    label = { Text("Strip Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameStrip(renameStrip.mac, currentName)
                        renameStripTarget = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameStripTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Outlet Dialog
    val renameOutlet = renameOutletTarget
    if (renameOutlet != null) {
        val (mac, outletN, oldName) = renameOutlet
        var newOutletName by remember { mutableStateOf(oldName) }
        AlertDialog(
            onDismissRequest = { renameOutletTarget = null },
            title = { Text("Rename Outlet $outletN") },
            text = {
                OutlinedTextField(
                    value = newOutletName,
                    onValueChange = { newOutletName = it },
                    label = { Text("Outlet Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameOutlet(mac, outletN, newOutletName)
                        renameOutletTarget = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameOutletTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Strip Confirmation Dialog
    val deleteStrip = deleteConfirmTarget
    if (deleteStrip != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            title = { Text("Delete Strip?") },
            text = {
                Text("Are you sure you want to remove '${deleteStrip.displayName}' (${deleteStrip.mac}) and its historical telemetry from Volta?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStrip(deleteStrip.mac)
                        deleteConfirmTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VoltaRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
