package com.example.volta.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
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

/**
 * Returns a suitable Material Icon for the given home icon keyword.
 */
fun getHomeIconVector(iconName: String): ImageVector {
    return when (iconName.lowercase()) {
        "apartment", "flat", "building" -> Icons.Default.Apartment
        "business", "office", "work" -> Icons.Default.Business
        "location", "pin" -> Icons.Default.LocationOn
        else -> Icons.Default.Home
    }
}

/**
 * Modern Home Switcher Bar matching the Volta Web App:
 * Features a home selector pill (`🏠 All Homes ▼` or `🏠 [Home Name] ▼`) with strip count
 * and a direct "+ Manage" button that opens the multi-home manager.
 */
@Composable
fun HomeSwitcherBar(
    homes: List<VoltaHome>,
    selectedHomeId: String?,
    allStrips: List<PowerStrip>,
    onSelectHome: (String?) -> Unit,
    onOpenManageHomes: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedDropdown by remember { mutableStateOf(false) }

    val currentHome = remember(homes, selectedHomeId) {
        if (selectedHomeId == null) null else homes.find { it.id == selectedHomeId }
    }

    val currentStripsCount = remember(allStrips, selectedHomeId) {
        if (selectedHomeId == null) allStrips.size
        else allStrips.count { it.homeId == selectedHomeId }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, VoltaSlateBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Main Home Selector Pill
            Box {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (selectedHomeId != null) VoltaBlue.copy(alpha = 0.22f) else Color(0xFF1E293B),
                    border = BorderStroke(
                        1.2.dp,
                        if (selectedHomeId != null) VoltaCyan else Color(0xFF334155)
                    ),
                    modifier = Modifier
                        .clickable { expandedDropdown = !expandedDropdown }
                        .testTag("home_selector_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            imageVector = getHomeIconVector(currentHome?.icon ?: "home"),
                            contentDescription = "Home",
                            tint = if (selectedHomeId != null) VoltaCyan else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = currentHome?.name ?: "All Homes",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Count badge
                        Surface(
                            shape = CircleShape,
                            color = if (selectedHomeId != null) VoltaCyan.copy(alpha = 0.25f) else Color(0xFF334155)
                        ) {
                            Text(
                                text = "$currentStripsCount",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (selectedHomeId != null) VoltaCyan else Color(0xFFCBD5E1),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                        Icon(
                            imageVector = if (expandedDropdown) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = "Expand",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Dropdown menu listing user homes
                DropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false },
                    modifier = Modifier
                        .background(Color(0xFF0F172A))
                        .border(1.dp, VoltaSlateBorder, RoundedCornerShape(8.dp))
                ) {
                    // Option 1: All Homes
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    tint = if (selectedHomeId == null) VoltaCyan else Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "All Homes",
                                    fontWeight = if (selectedHomeId == null) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedHomeId == null) VoltaCyan else Color.White,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "(${allStrips.size} strips)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                                if (selectedHomeId == null) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = VoltaCyan,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            expandedDropdown = false
                            onSelectHome(null)
                        }
                    )

                    if (homes.isNotEmpty()) {
                        HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 4.dp))
                    }

                    // User Created Homes
                    homes.forEach { home ->
                        val isSelected = selectedHomeId == home.id
                        val count = allStrips.count { it.homeId == home.id }
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = getHomeIconVector(home.icon),
                                        contentDescription = null,
                                        tint = if (isSelected) VoltaCyan else Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = home.name,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) VoltaCyan else Color.White,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = "($count strips)",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = VoltaCyan,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                expandedDropdown = false
                                onSelectHome(home.id)
                            }
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 4.dp))

                    // Action: Manage Homes
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = VoltaCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Manage Homes...",
                                    fontWeight = FontWeight.SemiBold,
                                    color = VoltaCyan,
                                    fontSize = 13.sp
                                )
                            }
                        },
                        onClick = {
                            expandedDropdown = false
                            onOpenManageHomes()
                        }
                    )
                }
            }

            // Quick "+ Manage" action pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.clickable { onOpenManageHomes() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Manage Homes",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Manage",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }
        }
    }
}

/**
 * Manage Homes Dialog matching the Web App "+ Manage Homes" Modal:
 * Allows creating new homes, viewing assigned strip counts, renaming, and deleting homes.
 */
@Composable
fun ManageHomesDialog(
    homes: List<VoltaHome>,
    allStrips: List<PowerStrip>,
    onCreateHome: (String, String) -> Unit,
    onUpdateHome: (String, String, String) -> Unit,
    onDeleteHome: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newHomeName by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("home") }
    var isCreating by remember { mutableStateOf(false) }

    var editingHome by remember { mutableStateOf<VoltaHome?>(null) }
    var editName by remember { mutableStateOf("") }
    var deleteConfirmHome by remember { mutableStateOf<VoltaHome?>(null) }

    val iconOptions = listOf(
        Pair("home", "Home"),
        Pair("apartment", "Apartment"),
        Pair("business", "Office"),
        Pair("location", "Location")
    )

    // Delete Confirmation Sub-Dialog
    deleteConfirmHome?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmHome = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = VoltaRed) },
            title = { Text("Delete Home '${target.name}'?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Strips assigned to this home will be unassigned and remain visible in 'All Homes'.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = target.id
                        deleteConfirmHome = null
                        onDeleteHome(id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VoltaRed, contentColor = Color.White)
                ) {
                    Text("Delete Home")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmHome = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = VoltaSlateCard
        )
    }

    // Rename Sub-Dialog
    editingHome?.let { target ->
        AlertDialog(
            onDismissRequest = { editingHome = null },
            title = { Text("Rename Home", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Home Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VoltaCyan,
                        focusedLabelColor = VoltaCyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            onUpdateHome(target.id, editName.trim(), target.icon)
                            editingHome = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VoltaBlue)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingHome = null }) {
                    Text("Cancel")
                }
            },
            containerColor = VoltaSlateCard
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = VoltaCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Text("Manage Homes", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: Add New Home
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VoltaSlateSubCard,
                    border = BorderStroke(1.dp, VoltaSlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Add New Home / Location",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = VoltaCyan
                        )
                        OutlinedTextField(
                            value = newHomeName,
                            onValueChange = { newHomeName = it },
                            placeholder = { Text("e.g. Living Room, Beach Villa, Office", fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VoltaCyan,
                                focusedLabelColor = VoltaCyan
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Icon selector row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            iconOptions.forEach { (iconKey, label) ->
                                val isSelected = selectedIcon == iconKey
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) VoltaCyan.copy(alpha = 0.2f) else Color(0xFF1E293B),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) VoltaCyan else Color(0xFF334155)
                                    ),
                                    modifier = Modifier.clickable { selectedIcon = iconKey }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = getHomeIconVector(iconKey),
                                            contentDescription = label,
                                            tint = if (isSelected) VoltaCyan else Color(0xFF94A3B8),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            color = if (isSelected) VoltaCyan else Color(0xFFCBD5E1)
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (newHomeName.isNotBlank()) {
                                    isCreating = true
                                    onCreateHome(newHomeName.trim(), selectedIcon)
                                    newHomeName = ""
                                    isCreating = false
                                }
                            },
                            enabled = newHomeName.isNotBlank() && !isCreating,
                            colors = ButtonDefaults.buttonColors(containerColor = VoltaBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Home", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }

                // Section 2: Existing Homes List
                Text(
                    text = "Existing Homes (${homes.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (homes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No homes created yet. Create one above to organize your strips.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(homes, key = { it.id }) { home ->
                            val assignedCount = allStrips.count { it.homeId == home.id }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = VoltaSlateSubCard,
                                border = BorderStroke(1.dp, VoltaSlateBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = getHomeIconVector(home.icon),
                                            contentDescription = null,
                                            tint = VoltaCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = home.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "$assignedCount ${if (assignedCount == 1) "strip" else "strips"} assigned",
                                                fontSize = 11.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                editingHome = home
                                                editName = home.name
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Rename",
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { deleteConfirmHome = home },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = VoltaRed.copy(alpha = 0.85f),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
            ) {
                Text("Done", color = Color.White)
            }
        },
        containerColor = VoltaSlateCard
    )
}
