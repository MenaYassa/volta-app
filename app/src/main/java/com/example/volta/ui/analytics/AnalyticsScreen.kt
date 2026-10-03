package com.example.volta.ui.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.model.PowerStrip
import com.example.volta.model.TelemetryPoint
import com.example.volta.model.TopConsumer
import com.example.volta.theme.VoltaAmber
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaRed
import com.example.volta.theme.VoltaYellow
import com.example.volta.viewmodel.VoltaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Reset-Tolerant Cumulative Energy Integration
 * Walks chronological meter samples per outlet and sums positive increments only.
 * Ignores drops caused by strip reboots or power loss resets.
 * Fallback: integrates positive power over time (avg_w * seconds / 3600 / 1000).
 */
fun calculateResetTolerantEnergyKwh(points: List<TelemetryPoint>): Double {
    if (points.isEmpty()) return 0.0
    val byOutlet = points.groupBy { "${it.mac}_${it.outlet}" }
    var totalKwh = 0.0

    for ((_, outletPoints) in byOutlet) {
        val sorted = outletPoints.sortedBy { it.timestamp }
        var outletKwh = 0.0
        var meterIncrementsSum = 0.0
        var hasPositiveMeter = false

        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            val delta = curr.energyKwh - prev.energyKwh
            if (delta > 0.00001) {
                meterIncrementsSum += delta
                hasPositiveMeter = true
            } else if (delta < -0.00001 && curr.energyKwh > 0.00001) {
                // Strip hardware rebooted and zeroed meter: count new positive accumulation after reset
                meterIncrementsSum += curr.energyKwh
                hasPositiveMeter = true
            }
        }

        if (hasPositiveMeter && meterIncrementsSum > 0.0001) {
            outletKwh = meterIncrementsSum
        } else {
            // Fallback: integrate positive power samples over time interval
            for (i in 1 until sorted.size) {
                val prev = sorted[i - 1]
                val curr = sorted[i]
                val dtSec = ((curr.timestamp - prev.timestamp) / 1000.0).coerceIn(1.0, 300.0)
                val avgW = (prev.powerW + curr.powerW) / 2.0
                if (avgW > 0.0) {
                    outletKwh += (avgW * dtSec) / (3600.0 * 1000.0)
                }
            }
            if (outletKwh <= 0.0 && sorted.isNotEmpty()) {
                val avgW = sorted.map { it.powerW }.average()
                outletKwh = (avgW * 1.0) / 1000.0
            }
        }
        totalKwh += outletKwh
    }
    return totalKwh
}

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
    val topConsumersTimeRange by viewModel.topConsumersTimeRange.collectAsState()
    val summary by viewModel.analyticsSummary.collectAsState()

    var selectedMetric by remember { mutableStateOf("power") } // "power", "voltage", "temp", "all"

    val activeStrip = remember(strips, selectedMac) {
        strips.find { it.mac == selectedMac }
    }

    // Live Demand is ALWAYS real-time active wattage from strips (using total_power_w or live outlet wattage)
    val realActivePowerNow = remember(strips, activeStrip, selectedOutlet) {
        if (activeStrip != null) {
            if (selectedOutlet > 0) {
                activeStrip.outlets.find { it.n == selectedOutlet }?.powerW ?: 0.0
            } else {
                activeStrip.totalPowerW
            }
        } else {
            strips.sumOf { it.totalPowerW }
        }
    }

    // Initial load on parameter change (Strip selection, channel filter, or time range)
    LaunchedEffect(selectedMac, selectedOutlet, timeRange) {
        viewModel.loadAnalyticsData()
    }

    // Keep KPI values refreshed every ~3 seconds without full chart re-fetch
    var liveKpiTick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(3000L)
            liveKpiTick = System.currentTimeMillis()
        }
    }

    // Memoize telemetry data transformations
    val powerData = remember(telemetry) { telemetry.map { it.powerW } }
    val voltageData = remember(telemetry) { telemetry.map { it.voltageV } }
    val tempData = remember(telemetry) { telemetry.map { it.tempC.toDouble() } }

    val avgPower = remember(powerData) { if (powerData.isNotEmpty()) powerData.average() else 0.0 }
    val peakPower = remember(powerData) { if (powerData.isNotEmpty()) powerData.maxOrNull() ?: 0.0 else 0.0 }
    val avgVoltage = remember(voltageData) { if (voltageData.isNotEmpty()) voltageData.average() else 220.0 }
    val avgTemp = remember(tempData) { if (tempData.isNotEmpty()) tempData.average() else 25.0 }

    val livePeakPower = remember(peakPower, realActivePowerNow, liveKpiTick) {
        maxOf(peakPower, realActivePowerNow)
    }
    val liveAvgPower = remember(avgPower, powerData.size, realActivePowerNow, liveKpiTick) {
        if (powerData.isNotEmpty() && realActivePowerNow > 0) {
            ((avgPower * powerData.size) + realActivePowerNow) / (powerData.size + 1)
        } else avgPower
    }

    // Reset-Tolerant Cumulative kWh
    val totalKwh = remember(telemetry, liveKpiTick) {
        calculateResetTolerantEnergyKwh(telemetry)
    }

    val estimatedCost = remember(totalKwh, settings.costPerKwh, liveKpiTick) {
        totalKwh * settings.costPerKwh
    }

    // Canonical summary metrics directly from POST /api/analytics/summary (with local calculation fallback)
    val displayKwh = summary?.energyKwh ?: totalKwh
    val displayCost = summary?.cost ?: estimatedCost
    val displayAvgPower = summary?.avgPowerW ?: liveAvgPower
    val displayPeakPower = summary?.peakPowerW ?: livePeakPower
    val displayVoltage = summary?.avgVoltageV ?: avgVoltage
    val displayCurrency = summary?.currency ?: settings.currency

    // Strictly top 5 energy consumers as requested
    val topFiveConsumers = remember(topConsumers) {
        topConsumers.take(5)
    }

    // Format human-friendly context line: e.g. "period: last 7 days · Bedroom TV"
    val periodHumanLabel = when (timeRange) {
        "1h" -> "last 1 hour"
        "6h" -> "last 6 hours"
        "24h" -> "last 24 hours"
        "7d" -> "last 7 days"
        "30d" -> "last 30 days"
        "6m" -> "last 6 months"
        "1y" -> "last 1 year"
        else -> "last $timeRange"
    }

    val targetHumanLabel = if (activeStrip != null) {
        if (selectedOutlet > 0) {
            val oName = activeStrip.outlets.find { it.n == selectedOutlet }?.name ?: "Outlet $selectedOutlet"
            "${activeStrip.displayName} · $oName"
        } else {
            activeStrip.displayName
        }
    } else {
        "All strips"
    }

    val heroContextLine = "period: $periodHumanLabel · $targetHumanLabel"

    // Supported periods: 1h, 6h, 24h, 7d, 30d, 6 months, 1 year
    val supportedRanges = listOf(
        "1h" to "1h",
        "6h" to "6h",
        "24h" to "24h",
        "7d" to "7d",
        "30d" to "30d",
        "6m" to "6m",
        "1y" to "1y"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Header with Status & Refresh Action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Energy Analytics",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            color = VoltaGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(VoltaGreen, CircleShape)
                                )
                                Text(
                                    text = "LIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = VoltaGreen
                                )
                            }
                        }
                    }
                    Text(
                        text = "Real-time telemetry and reset-tolerant energy tracking",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { viewModel.loadAnalyticsData() },
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .testTag("refresh_analytics_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = VoltaBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Empty State Banner if no strips exist
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
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = VoltaBlue,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Power Strips Paired",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pair a strip via the Setup tab to start capturing live energy and power telemetry.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // 2. Executive Hero Power & Cost Card (Follows Selection + Period)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    text = if (selectedMac == null) "LIVE ACTIVE DEMAND" else "${activeStrip?.displayName?.uppercase()} DEMAND",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                // Context line: period: last 7 days · Bedroom TV
                                Text(
                                    text = heroContextLine,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = VoltaBlue
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = String.format("%.1f", realActivePowerNow),
                                        fontSize = 38.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (realActivePowerNow > 0) VoltaGreen else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "W",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (realActivePowerNow > 0) VoltaGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = if (realActivePowerNow > 0) VoltaGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(14.dp))

                        // 4-Column Executive KPI Metrics (Computed over chosen window)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ExecutiveKpi(
                                label = "Avg Load",
                                value = "${String.format("%.1f", displayAvgPower)} W",
                                color = VoltaBlue,
                                modifier = Modifier.weight(1f)
                            )
                            ExecutiveKpi(
                                label = "Peak Demand",
                                value = "${String.format("%.1f", displayPeakPower)} W",
                                color = VoltaRed,
                                modifier = Modifier.weight(1f)
                            )
                            ExecutiveKpi(
                                label = "Energy",
                                value = "${String.format("%.2f", displayKwh)} kWh",
                                color = VoltaYellow,
                                modifier = Modifier.weight(1f)
                            )
                            ExecutiveKpi(
                                label = "Est. Cost",
                                value = "${String.format("%.2f", displayCost)} $displayCurrency",
                                color = VoltaGreen,
                                modifier = Modifier.weight(1.1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Grid Health Bar
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Grid Voltage: ${String.format("%.1f", displayVoltage)} V (Stable)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Temp: ${String.format("%.0f", avgTemp)}°C (Nominal)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 3. Interactive Filter Bar (Device & Outlet Segment)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (selectedMac == null) {
                                Surface(
                                    color = VoltaBlue.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Viewing All Devices",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VoltaBlue,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Device Chips Row
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedMac == null,
                                    onClick = { viewModel.setSelectedAnalyticsMac(null) },
                                    label = { Text("All Devices (${strips.size})", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = VoltaBlue.copy(alpha = 0.15f),
                                        selectedLabelColor = VoltaBlue
                                    ),
                                    modifier = Modifier.testTag("filter_all_devices")
                                )
                            }
                            items(strips) { strip ->
                                FilterChip(
                                    selected = selectedMac == strip.mac,
                                    onClick = { viewModel.setSelectedAnalyticsMac(strip.mac) },
                                    label = { Text(strip.displayName, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = VoltaBlue.copy(alpha = 0.15f),
                                        selectedLabelColor = VoltaBlue
                                    ),
                                    modifier = Modifier.testTag("filter_strip_${strip.mac}")
                                )
                            }
                        }

                        // Show outlet pills only if a specific strip is selected
                        if (activeStrip != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AnalyticsOutletPill(
                                    label = "All",
                                    sublabel = "Master",
                                    isSelected = selectedOutlet == 0,
                                    onClick = { viewModel.setSelectedAnalyticsOutlet(0) },
                                    modifier = Modifier.weight(1.1f)
                                )

                                (1..4).forEach { outletNum ->
                                    val customName = activeStrip.outlets.find { it.n == outletNum }?.name
                                    AnalyticsOutletPill(
                                        label = "#$outletNum",
                                        sublabel = customName?.take(6) ?: "Out $outletNum",
                                        isSelected = selectedOutlet == outletNum,
                                        onClick = { viewModel.setSelectedAnalyticsOutlet(outletNum) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Time Horizon Segmented Control (Supported: 1h, 6h, 24h, 7d, 30d, 6m, 1y)
            item {
                Column {
                    Text(
                        text = "Chart & Analytics Window",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        supportedRanges.forEach { (key, label) ->
                            val isSelected = timeRange == key
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable { viewModel.setTimeRange(key) }
                                    .testTag("time_range_$key"),
                                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                shape = RoundedCornerShape(9.dp),
                                shadowElevation = if (isSelected) 2.dp else 0.dp
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) VoltaBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5. Metric Tabs Selector
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple("power", "⚡ Power (Watts)", VoltaBlue),
                        Triple("voltage", "🔌 Grid Voltage (V)", VoltaYellow),
                        Triple("temp", "🌡️ Temperature (°C)", VoltaRed),
                        Triple("all", "📊 All Telemetry", VoltaBlue)
                    ).forEach { (key, label, accentColor) ->
                        val isSelected = selectedMetric == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMetric = key },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.15f),
                                selectedLabelColor = accentColor
                            ),
                            modifier = Modifier.testTag("metric_tab_$key")
                        )
                    }
                }
            }

            // 6. Interactive Professional Telemetry Charts (with zero stutter & touch inspection)
            if (selectedMetric == "power" || selectedMetric == "all") {
                item {
                    ProfessionalTelemetryChart(
                        dataPoints = powerData,
                        lineColor = VoltaBlue,
                        unit = "W",
                        title = "Power Consumption Trend",
                        icon = Icons.Default.Bolt
                    )
                }
            }

            if (selectedMetric == "voltage" || selectedMetric == "all") {
                item {
                    ProfessionalTelemetryChart(
                        dataPoints = voltageData,
                        lineColor = VoltaAmber,
                        unit = "V",
                        title = "Grid Voltage Stability",
                        icon = Icons.Default.Timeline
                    )
                }
            }

            if (selectedMetric == "temp" || selectedMetric == "all") {
                item {
                    ProfessionalTelemetryChart(
                        dataPoints = tempData,
                        lineColor = VoltaRed,
                        unit = "°C",
                        title = "Internal Temperature",
                        icon = Icons.Default.Thermostat
                    )
                }
            }

            // 7. Live Outlet Power Distribution (Channel Breakdown)
            if (activeStrip != null && activeStrip.totalPowerW > 0) {
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PieChart,
                                        contentDescription = null,
                                        tint = VoltaBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Channel Load Distribution",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "${String.format("%.1f", activeStrip.totalPowerW)} W total",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VoltaBlue
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Stacked Multi-Color Segment Bar
                            val channelColors = listOf(VoltaBlue, VoltaGreen, VoltaYellow, VoltaAmber)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                activeStrip.outlets.forEachIndexed { index, outlet ->
                                    val pct = if (activeStrip.totalPowerW > 0) {
                                        (outlet.powerW / activeStrip.totalPowerW).toFloat().coerceIn(0f, 1f)
                                    } else 0f
                                    if (pct > 0.01f) {
                                        Box(
                                            modifier = Modifier
                                                .weight(pct.coerceAtLeast(0.01f))
                                                .height(10.dp)
                                                .background(channelColors.getOrElse(index) { VoltaBlue })
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Channel legend row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                activeStrip.outlets.forEachIndexed { index, outlet ->
                                    val pct = if (activeStrip.totalPowerW > 0) (outlet.powerW / activeStrip.totalPowerW * 100).toInt() else 0
                                    val col = channelColors.getOrElse(index) { VoltaBlue }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(col, CircleShape)
                                        )
                                        Column {
                                            Text(
                                                text = "Out ${outlet.n}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${String.format("%.0f", outlet.powerW)}W ($pct%)",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 8. Top 5 Energy Consumers Card (Strictly Top 5, Capped with Independent Period Selector)
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Leaderboard,
                                    contentDescription = null,
                                    tint = VoltaYellow,
                                    modifier = Modifier.size(18.dp)
                                )
                                val topPeriodLabel = when (topConsumersTimeRange) {
                                    "1h" -> "last 1 hour"
                                    "6h" -> "last 6 hours"
                                    "24h" -> "last 24 hours"
                                    "7d" -> "last 7 days"
                                    "30d" -> "last 30 days"
                                    "6m" -> "last 6 months"
                                    "1y" -> "last 1 year"
                                    else -> "last $topConsumersTimeRange"
                                }
                                Text(
                                    text = "Top 5 Energy Consumers ($topPeriodLabel)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Independent Period Selector Row for Top Consumers
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            supportedRanges.forEach { (key, label) ->
                                val isSelected = topConsumersTimeRange == key
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setTopConsumersTimeRange(key) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = VoltaYellow.copy(alpha = 0.18f),
                                        selectedLabelColor = VoltaYellow
                                    ),
                                    modifier = Modifier.testTag("top_consumer_range_$key")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (topFiveConsumers.isEmpty()) {
                            Text(
                                text = "Recording telemetry consumption data…",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            val maxKwh = remember(topFiveConsumers) {
                                (topFiveConsumers.maxOfOrNull { it.kwh } ?: 1.0).coerceAtLeast(0.001)
                            }
                            topFiveConsumers.forEachIndexed { index, item ->
                                val medalEmoji = when (index) {
                                    0 -> "🥇"
                                    1 -> "🥈"
                                    2 -> "🥉"
                                    else -> "#${index + 1}"
                                }

                                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = medalEmoji,
                                                fontSize = if (index < 3) 16.sp else 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Column {
                                                Text(
                                                    text = item.outletName,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = item.stripName,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "${String.format("%.2f", item.kwh)} kWh",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = VoltaBlue
                                            )
                                            Text(
                                                text = "${String.format("%.2f", item.cost)} ${settings.currency}",
                                                fontSize = 11.sp,
                                                color = VoltaGreen,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { (item.kwh / maxKwh).toFloat().coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = VoltaBlue,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }

                                if (index < topFiveConsumers.size - 1) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 9. Standby Energy & Cost Saving Insights Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = VoltaGreen.copy(alpha = 0.08f)
                    ),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(VoltaGreen.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EnergySavingsLeaf,
                                contentDescription = null,
                                tint = VoltaGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Standby Power Optimization",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Automating nighttime shutoff on inactive chargers and workstations can reduce standby consumption by up to 22%.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutiveKpi(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AnalyticsOutletPill(
    label: String,
    sublabel: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) VoltaBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val bgColor = if (isSelected) VoltaBlue else Color.Transparent

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(listOf(borderColor, borderColor))
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = sublabel,
                fontSize = 9.sp,
                color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
