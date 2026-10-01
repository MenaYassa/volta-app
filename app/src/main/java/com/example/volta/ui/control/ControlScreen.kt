package com.example.volta.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.model.Outlet
import com.example.volta.model.PowerStrip
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaGreenBright
import com.example.volta.theme.VoltaRed
import com.example.volta.theme.VoltaYellow
import com.example.volta.ui.components.InteractiveToggle
import com.example.volta.ui.components.StatBox
import com.example.volta.ui.components.StatusDot
import com.example.volta.ui.components.TagBadge
import com.example.volta.viewmodel.VoltaViewModel

@Composable
fun ControlScreen(
    viewModel: VoltaViewModel,
    modifier: Modifier = Modifier
) {
    val strips by viewModel.strips.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val onlineStripsCount = strips.count { it.online }
    val totalOutlets = strips.sumOf { it.outlets.size }
    val activeOutletsCount = strips.sumOf { it.outlets.count { o -> o.on } }
    val totalLoadW = strips.sumOf { it.totalPowerW }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Overview Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBox(
                    value = "$onlineStripsCount / ${strips.size}",
                    label = "online strips",
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    value = "$activeOutletsCount / $totalOutlets",
                    label = "outlets on",
                    modifier = Modifier.weight(1f),
                    highlightColor = VoltaGreen
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBox(
                    value = "${String.format("%.1f", totalLoadW)} W",
                    label = "total load",
                    modifier = Modifier.weight(1f),
                    highlightColor = VoltaYellow
                )
                StatBox(
                    value = "Real-time",
                    label = "confirmed by strip",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Refresh Action Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { viewModel.refresh() },
                    enabled = !isRefreshing,
                    modifier = Modifier.testTag("refresh_strips_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isRefreshing) "Refreshing…" else "Refresh now")
                }

                Text(
                    text = "Auto-poll active (5s)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Strips List
        if (strips.isEmpty()) {
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
                        Text(
                            text = "No Power Strips Found",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Go to the 'Strips' tab or 'Setup' to register a new MTTL-W01 strip.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(strips, key = { it.mac }) { strip ->
                PowerStripCard(
                    strip = strip,
                    onToggleOutlet = { outletN, st -> viewModel.toggleOutlet(strip.mac, outletN, st) },
                    onToggleMaster = { st -> viewModel.toggleMaster(strip.mac, st) },
                    onToggleLock = { outletN -> viewModel.toggleOutletLock(strip.mac, outletN) }
                )
            }
        }
    }
}

@Composable
fun PowerStripCard(
    strip: PowerStrip,
    onToggleOutlet: (Int, Boolean) -> Unit,
    onToggleMaster: (Boolean) -> Unit,
    onToggleLock: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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

                // Master Toggle Switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Master",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    InteractiveToggle(
                        checked = strip.isAnyOn,
                        onCheckedChange = { onToggleMaster(it) },
                        modifier = Modifier.testTag("master_switch_${strip.mac}")
                    )
                }
            }

            // Strip Metrics Pill Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TagBadge(text = if (strip.online) "ONLINE" else "OFFLINE", color = if (strip.online) VoltaGreen else VoltaRed)
                TagBadge(text = "${strip.voltageV}V")
                TagBadge(text = "${String.format("%.1f", strip.totalPowerW)}W")
                TagBadge(text = "${String.format("%.2f", strip.totalEnergyKwh)} kWh")
                TagBadge(text = "${strip.rssi} dBm")
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Outlets List
            strip.outlets.forEach { outlet ->
                OutletRow(
                    outlet = outlet,
                    onToggle = { onToggleOutlet(outlet.n, it) },
                    onToggleLock = { onToggleLock(outlet.n) }
                )
                if (outlet.n < strip.outlets.size) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OutletRow(
    outlet: Outlet,
    onToggle: (Boolean) -> Unit,
    onToggleLock: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = if (outlet.on) VoltaGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = outlet.n.toString(),
                    fontSize = 13.sp,
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
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = VoltaRed,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Text(
                    text = "${String.format("%.1f", outlet.powerW)} W • ${String.format("%.2f", outlet.energyKwh)} kWh • ${outlet.tempC}°C",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onToggleLock,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (outlet.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (outlet.locked) "Unlock Outlet" else "Lock Outlet",
                    tint = if (outlet.locked) VoltaRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            InteractiveToggle(
                checked = outlet.on,
                onCheckedChange = { if (!outlet.locked) onToggle(it) },
                enabled = !outlet.locked,
                modifier = Modifier.testTag("switch_outlet_${outlet.n}")
            )
        }
    }
}
