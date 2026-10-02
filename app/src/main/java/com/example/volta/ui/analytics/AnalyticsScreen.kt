package com.example.volta.ui.analytics

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.theme.VoltaAmber
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaRed
import com.example.volta.theme.VoltaYellow
import com.example.volta.ui.components.StatBox
import com.example.volta.ui.components.TagBadge
import com.example.volta.ui.components.TelemetryLineChart
import com.example.volta.viewmodel.VoltaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: VoltaViewModel,
    modifier: Modifier = Modifier
) {
    val strips by viewModel.strips.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val timeRange by viewModel.selectedTimeRange.collectAsState()
    val selectedMac by viewModel.selectedAnalyticsMac.collectAsState()
    val selectedOutlet by viewModel.selectedAnalyticsOutlet.collectAsState()
    val telemetry by viewModel.chartTelemetry.collectAsState()
    val topConsumers by viewModel.topConsumers.collectAsState()

    var selectedMetric by remember { mutableStateOf("power") } // "power", "voltage", "temp", "all"

    val activeStrip = strips.find { it.mac == selectedMac }
    val realActivePowerNow = if (activeStrip != null) {
        if (selectedOutlet > 0) {
            activeStrip.outlets.find { it.n == selectedOutlet }?.powerW ?: 0.0
        } else {
            activeStrip.totalPowerW
        }
    } else {
        strips.sumOf { it.totalPowerW }
    }

    val powerData = telemetry.map { it.powerW }
    val voltageData = telemetry.map { it.voltageV }
    val tempData = telemetry.map { it.tempC.toDouble() }

    val avgPower = if (powerData.isNotEmpty()) powerData.average() else 0.0
    val peakPower = if (powerData.isNotEmpty()) powerData.maxOrNull() ?: 0.0 else 0.0

    val avgVoltage = if (voltageData.isNotEmpty()) voltageData.average() else 220.0
    val avgTemp = if (tempData.isNotEmpty()) tempData.average() else 25.0

    // Cumulative kWh calculated from real points
    val totalKwh = if (telemetry.isNotEmpty()) {
        val minEnergy = telemetry.minOfOrNull { it.energyKwh } ?: 0.0
        val maxEnergy = telemetry.maxOfOrNull { it.energyKwh } ?: 0.0
        val delta = maxEnergy - minEnergy
        if (delta > 0.0001) delta else (avgPower * 1.0) / 1000.0
    } else 0.0

    val estimatedCost = totalKwh * settings.costPerKwh

    var stripDropdownExpanded by remember { mutableStateOf(false) }
    var outletDropdownExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Empty State if no real strips under control
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
                            imageVector = Icons.Default.Info,
                            contentDescription = "No Strips",
                            tint = VoltaBlue,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Power Strips Paired Yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Analytics reflect live measurements from the strips you control. To connect or claim a strip to your user account, open the 'Setup' tab.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        // Live Real-Time Overview Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBox(
                    value = "${String.format("%.1f", realActivePowerNow)} W",
                    label = "real-time load now",
                    modifier = Modifier.weight(1f),
                    highlightColor = VoltaYellow
                )
                StatBox(
                    value = "${String.format("%.1f", avgPower)} W",
                    label = "avg load (${timeRange})",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBox(
                    value = "${String.format("%.1f", peakPower)} W",
                    label = "peak load (${timeRange})",
                    modifier = Modifier.weight(1f),
                    highlightColor = VoltaRed
                )
                StatBox(
                    value = "${String.format("%.2f", estimatedCost)} ${settings.currency}",
                    label = "energy cost (${String.format("%.2f", totalKwh)} kWh)",
                    modifier = Modifier.weight(1f),
                    highlightColor = VoltaGreen
                )
            }
        }

        // Device & Outlet Dropdown Selectors
        if (strips.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Device & Channel Filter",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TagBadge(
                                text = if (selectedOutlet == 0) "All Channels" else "Outlet $selectedOutlet",
                                color = VoltaBlue
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Strip Selector Dropdown
                            ExposedDropdownMenuBox(
                                expanded = stripDropdownExpanded,
                                onExpandedChange = { stripDropdownExpanded = it },
                                modifier = Modifier.weight(1.3f)
                            ) {
                                OutlinedTextField(
                                    value = activeStrip?.displayName ?: "All My Strips",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Power Strip") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stripDropdownExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = stripDropdownExpanded,
                                    onDismissRequest = { stripDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All My Strips (${strips.size})") },
                                        onClick = {
                                            viewModel.setSelectedAnalyticsMac(null)
                                            stripDropdownExpanded = false
                                        }
                                    )
                                    strips.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text("${s.displayName} (${s.mac})") },
                                            onClick = {
                                                viewModel.setSelectedAnalyticsMac(s.mac)
                                                stripDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Outlet Selector Dropdown
                            ExposedDropdownMenuBox(
                                expanded = outletDropdownExpanded,
                                onExpandedChange = { outletDropdownExpanded = it },
                                modifier = Modifier.weight(1f)
                            ) {
                                val outletLabel = if (selectedOutlet == 0) {
                                    "All Outlets"
                                } else {
                                    val oName = activeStrip?.outlets?.find { it.n == selectedOutlet }?.name
                                    if (oName != null) "O$selectedOutlet: $oName" else "Outlet $selectedOutlet"
                                }
                                OutlinedTextField(
                                    value = outletLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Outlet") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = outletDropdownExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = outletDropdownExpanded,
                                    onDismissRequest = { outletDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All Outlets (1-4)") },
                                        onClick = {
                                            viewModel.setSelectedAnalyticsOutlet(0)
                                            outletDropdownExpanded = false
                                        }
                                    )
                                    (1..4).forEach { n ->
                                        val oName = activeStrip?.outlets?.find { it.n == n }?.name
                                        DropdownMenuItem(
                                            text = { Text(if (oName != null) "$n: $oName" else "Outlet $n") },
                                            onClick = {
                                                viewModel.setSelectedAnalyticsOutlet(n)
                                                outletDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Time Range Filter Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val ranges = listOf("1h" to "1 Hour", "6h" to "6 Hours", "24h" to "24 Hours", "7d" to "7 Days")
                    items(ranges) { (key, label) ->
                        FilterChip(
                            selected = timeRange == key,
                            onClick = { viewModel.setTimeRange(key) },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.testTag("filter_range_$key")
                        )
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.loadAnalyticsData() },
                    modifier = Modifier.testTag("refresh_analytics_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Charts",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Metric View Tabs (Toggle between Power, Voltage, Temperature, All)
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val metrics = listOf(
                    "power" to "⚡ Power (W)",
                    "voltage" to "🔌 Voltage (V)",
                    "temp" to "🌡️ Temp (°C)",
                    "all" to "📊 All Graphs"
                )
                items(metrics) { (key, label) ->
                    FilterChip(
                        selected = selectedMetric == key,
                        onClick = { selectedMetric = key },
                        label = { Text(label, fontSize = 12.sp, fontWeight = if (selectedMetric == key) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("metric_chip_$key")
                    )
                }
            }
        }

        // Charts
        if (telemetry.isEmpty() && strips.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Awaiting Telemetry for ${activeStrip?.displayName ?: "Selected Strip"}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Real telemetry readings are recorded every 30s as the strip communicates with the server.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Power Consumption Chart
            if (selectedMetric == "power" || selectedMetric == "all") {
                item {
                    Text(
                        text = "⚡ Real Power Consumption (Watts)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TelemetryLineChart(
                        dataPoints = powerData,
                        lineColor = VoltaBlue,
                        unit = "W"
                    )
                }
            }

            // Grid Voltage Chart
            if (selectedMetric == "voltage" || selectedMetric == "all") {
                item {
                    Text(
                        text = "🔌 Grid Voltage (Volts)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TelemetryLineChart(
                        dataPoints = voltageData,
                        lineColor = VoltaAmber,
                        unit = "V"
                    )
                }
            }

            // Temperature Chart
            if (selectedMetric == "temp" || selectedMetric == "all") {
                item {
                    Text(
                        text = "🌡️ Internal Temperature (°C)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TelemetryLineChart(
                        dataPoints = tempData,
                        lineColor = VoltaRed,
                        unit = "°C"
                    )
                }
            }
        }

        // Top Energy Consumers Leaderboard
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Top Energy Consumers (${timeRange})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.ElectricBolt,
                            contentDescription = "Leaderboard",
                            tint = VoltaYellow
                        )
                    }
                    Text(
                        text = "Outlets belonging to your account ranked by kilowatt-hours consumed.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (topConsumers.isEmpty()) {
                        Text(
                            text = if (strips.isEmpty()) "No strips paired yet." else "Recording outlet consumption data…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        val maxKwh = (topConsumers.maxOfOrNull { it.kwh } ?: 1.0).coerceAtLeast(0.001)
                        topConsumers.forEachIndexed { index, item ->
                            val medal = when (index) {
                                0 -> "🥇 "
                                1 -> "🥈 "
                                2 -> "🥉 "
                                else -> "#${index + 1} "
                            }
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "$medal${item.outletName} (${item.stripName})",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${String.format("%.2f", item.kwh)} kWh • ${String.format("%.2f", item.cost)} ${settings.currency}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VoltaBlue
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (item.kwh / maxKwh).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    color = VoltaBlue,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                            if (index < topConsumers.size - 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
