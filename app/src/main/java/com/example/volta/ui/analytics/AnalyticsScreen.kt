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
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Refresh
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

    val totalPowerNow = strips.sumOf { it.totalPowerW }
    val powerData = telemetry.map { it.powerW }
    val voltageData = telemetry.map { it.voltageV }
    val tempData = telemetry.map { it.tempC.toDouble() }

    val avgPower = if (powerData.isNotEmpty()) powerData.average() else 0.0
    val peakPower = if (powerData.isNotEmpty()) powerData.maxOrNull() ?: 0.0 else 0.0
    val totalKwh = telemetry.map { it.energyKwh }.let {
        if (it.isNotEmpty()) (it.maxOrNull() ?: 0.0) - (it.minOrNull() ?: 0.0) else 0.0
    }.coerceAtLeast(0.0)
    val estimatedCost = totalKwh * settings.costPerKwh

    var stripDropdownExpanded by remember { mutableStateOf(false) }
    var outletDropdownExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Summary Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBox(
                    value = "${String.format("%.1f", totalPowerNow)} W",
                    label = "total power now",
                    modifier = Modifier.weight(1f),
                    highlightColor = VoltaYellow
                )
                StatBox(
                    value = "${String.format("%.1f", avgPower)} W",
                    label = "average load",
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
                    label = "peak power (${timeRange})",
                    modifier = Modifier.weight(1f),
                    highlightColor = VoltaRed
                )
                StatBox(
                    value = "${String.format("%.2f", estimatedCost)} ${settings.currency}",
                    label = "estimated cost (${String.format("%.2f", totalKwh)} kWh)",
                    modifier = Modifier.weight(1f),
                    highlightColor = VoltaGreen
                )
            }
        }

        // Strip & Outlet Dropdowns Filter
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Filter Telemetry",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Strip Selector
                        ExposedDropdownMenuBox(
                            expanded = stripDropdownExpanded,
                            onExpandedChange = { stripDropdownExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            val activeStrip = strips.find { it.mac == selectedMac }
                            OutlinedTextField(
                                value = activeStrip?.displayName ?: "All Strips",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Strip") },
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
                                    text = { Text("All Strips") },
                                    onClick = {
                                        viewModel.setSelectedAnalyticsMac(null)
                                        stripDropdownExpanded = false
                                    }
                                )
                                strips.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.displayName) },
                                        onClick = {
                                            viewModel.setSelectedAnalyticsMac(s.mac)
                                            stripDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Outlet Selector
                        ExposedDropdownMenuBox(
                            expanded = outletDropdownExpanded,
                            onExpandedChange = { outletDropdownExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = if (selectedOutlet == 0) "All Outlets" else "Outlet $selectedOutlet",
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
                                    text = { Text("All Outlets") },
                                    onClick = {
                                        viewModel.setSelectedAnalyticsOutlet(0)
                                        outletDropdownExpanded = false
                                    }
                                )
                                (1..4).forEach { n ->
                                    DropdownMenuItem(
                                        text = { Text("Outlet $n") },
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

        // Time Range Chips & Refresh
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

        // Power Consumption Chart
        item {
            Text(
                text = "⚡ Power Consumption (Watts)",
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

        // Voltage Chart
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

        // Temperature Chart
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
                        text = "Outlets ranked by cumulative kilowatt-hours consumed.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (topConsumers.isEmpty()) {
                        Text(
                            text = "Accumulating telemetry data…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        val maxKwh = (topConsumers.maxOfOrNull { it.kwh } ?: 1.0).coerceAtLeast(0.01)
                        topConsumers.forEachIndexed { index, item ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "#${index + 1} ${item.outletName} (${item.stripName})",
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
