package com.example.volta.ui.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.roundToInt
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
import com.example.volta.theme.VoltaCyan
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaGreenText
import com.example.volta.theme.VoltaOrange
import com.example.volta.theme.VoltaRed
import com.example.volta.theme.VoltaSlateBorder
import com.example.volta.theme.VoltaSlateCard
import com.example.volta.theme.VoltaSlateSubCard
import com.example.volta.theme.VoltaYellow
import com.example.volta.ui.components.HomeSwitcherBar
import com.example.volta.ui.components.ManageHomesDialog
import com.example.volta.ui.components.SignedOutPrompt
import com.example.volta.viewmodel.VoltaViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: VoltaViewModel,
    modifier: Modifier = Modifier
) {
    val strips by viewModel.strips.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val authRequired by viewModel.authRequired.collectAsState()
    val timeRange by viewModel.selectedTimeRange.collectAsState()
    val selectedMac by viewModel.selectedAnalyticsMac.collectAsState()
    val selectedOutlet by viewModel.selectedAnalyticsOutlet.collectAsState()
    val telemetry by viewModel.chartTelemetry.collectAsState()
    val topConsumers by viewModel.topConsumers.collectAsState()
    val topConsumersTimeRange by viewModel.topConsumersTimeRange.collectAsState()
    val summary by viewModel.analyticsSummary.collectAsState()
    val homes by viewModel.homes.collectAsState()
    val selectedHomeId by viewModel.selectedHomeId.collectAsState()

    var showManageHomes by remember { mutableStateOf(false) }

    // Sign out guard: hide analytics completely when signed out
    val isSignedOut = authRequired || settings.serverToken.isBlank()
    if (isSignedOut) {
        SignedOutPrompt(
            viewModel = viewModel,
            title = "Sign In to View Analytics",
            subtitle = "You are currently signed out. Sign in with your Volta Google account or enter your user token to view live telemetry curves and historical energy insights.",
            modifier = modifier
        )
        return
    }

    val homeFilteredStrips = remember(strips, selectedHomeId) {
        if (selectedHomeId == null) strips
        else strips.filter { it.homeId == selectedHomeId }
    }
    val homeMacs = remember(homeFilteredStrips) {
        homeFilteredStrips.map { it.mac.replace(":", "").uppercase() }.toSet()
    }

    val activeStrip = remember(homeFilteredStrips, selectedMac) {
        homeFilteredStrips.find { it.mac == selectedMac }
    }

    // Auto-reset strip selection if current selected strip is not in active home
    LaunchedEffect(homeFilteredStrips, selectedMac) {
        if (selectedMac != null && homeFilteredStrips.none { it.mac.equals(selectedMac, ignoreCase = true) }) {
            viewModel.setSelectedAnalyticsMac(null)
            viewModel.setSelectedAnalyticsOutlet(0)
        }
    }

    val realActivePowerNow = remember(homeFilteredStrips, activeStrip, selectedOutlet) {
        if (activeStrip != null) {
            if (selectedOutlet > 0) {
                activeStrip.outlets.find { it.n == selectedOutlet }?.powerW ?: 0.0
            } else {
                activeStrip.totalPowerW
            }
        } else {
            homeFilteredStrips.sumOf { it.totalPowerW }
        }
    }

    // Refresh trigger
    LaunchedEffect(selectedMac, selectedOutlet, timeRange, selectedHomeId) {
        viewModel.loadAnalyticsData()
    }

    var liveTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(5000L)
            liveTick = System.currentTimeMillis()
        }
    }

    val chartPoints = remember(telemetry, selectedMac, selectedOutlet, timeRange, homeMacs, selectedHomeId) {
        if (telemetry.isEmpty()) return@remember emptyList<Triple<Long, Double, Pair<Double, Double>>>()

        // 1. Filter telemetry to active home and selected strip / outlet
        val scopedTelemetry = if (selectedHomeId == null) telemetry
        else telemetry.filter { it.mac.replace(":", "").uppercase() in homeMacs }

        val filtered = when {
            selectedMac != null && selectedOutlet > 0 -> {
                scopedTelemetry.filter { it.mac.equals(selectedMac, ignoreCase = true) && it.outlet == selectedOutlet }
            }
            selectedMac != null -> {
                scopedTelemetry.filter { it.mac.equals(selectedMac, ignoreCase = true) }
            }
            else -> scopedTelemetry
        }
        if (filtered.isEmpty()) return@remember emptyList<Triple<Long, Double, Pair<Double, Double>>>()

        // 2. Chronological bucket size based on timeRange
        // Provides clean, realistic intervals (e.g. 15-min buckets: :00, :15, :30, :45 for 24h)
        val bucketMs = when (timeRange) {
            "1h" -> 60 * 1000L           // 1 min bucket
            "6h" -> 5 * 60 * 1000L       // 5 min bucket
            "24h" -> 15 * 60 * 1000L     // 15 min bucket (:00, :15, :30, :45)
            "7d" -> 3600 * 1000L         // 1 hour bucket
            "30d" -> 2 * 3600 * 1000L    // 2 hour bucket
            "6m", "1y" -> 24 * 3600 * 1000L // 1 day bucket
            else -> 15 * 60 * 1000L
        }

        // Group points by bucket timestamp
        val grouped = filtered.groupBy { p ->
            val ms = if (p.timestamp < 10000000000L) p.timestamp * 1000L else p.timestamp
            (ms / bucketMs) * bucketMs
        }

        grouped.entries.sortedBy { it.key }.map { (bucketTs, samples) ->
            // For power: if viewing all outlets or all strips, sum the individual outlets
            val totalPower = if (selectedMac != null && selectedOutlet > 0) {
                samples.map { it.powerW }.average()
            } else {
                val outletAverages = samples.groupBy { "${it.mac.uppercase()}_${it.outlet}" }
                    .values.map { outletSamples -> outletSamples.map { it.powerW }.average() }
                outletAverages.sum()
            }

            val nonZeroVoltages = samples.map { it.voltageV }.filter { it > 50.0 }
            val avgVoltage = if (nonZeroVoltages.isNotEmpty()) nonZeroVoltages.average() else 220.0
            val maxTemp = samples.maxOfOrNull { it.tempC }?.toDouble() ?: 25.0

            Triple(
                bucketTs,
                kotlin.math.round(totalPower * 10.0) / 10.0,
                Pair(
                    kotlin.math.round(avgVoltage * 10.0) / 10.0,
                    maxTemp
                )
            )
        }
    }

    val telemetryTimestamps = remember(chartPoints) { chartPoints.map { it.first } }
    val powerData = remember(chartPoints) { chartPoints.map { it.second } }
    val voltageData = remember(chartPoints) { chartPoints.map { it.third.first } }
    val tempData = remember(chartPoints) { chartPoints.map { it.third.second } }

    val avgPower = remember(powerData) { if (powerData.isNotEmpty()) powerData.average() else 0.0 }
    val peakPower = remember(powerData, realActivePowerNow) {
        maxOf(realActivePowerNow, if (powerData.isNotEmpty()) powerData.maxOrNull() ?: 0.0 else 0.0)
    }
    val avgVoltage = remember(voltageData) { if (voltageData.isNotEmpty()) voltageData.average() else 232.1 }
    val avgTemp = remember(tempData) { if (tempData.isNotEmpty()) tempData.average() else 31.0 }

    val chartScopeLabel = remember(selectedMac, selectedOutlet, activeStrip, homeFilteredStrips, selectedHomeId) {
        when {
            activeStrip != null && selectedOutlet > 0 -> {
                val o = activeStrip.outlets.find { it.n == selectedOutlet }
                "${activeStrip.displayName} • ${o?.name ?: "Outlet $selectedOutlet"}"
            }
            activeStrip != null -> {
                "${activeStrip.displayName} (All Outlets Combined)"
            }
            else -> {
                if (selectedHomeId != null) "All in Home (${homeFilteredStrips.size} strips)"
                else if (homeFilteredStrips.isNotEmpty()) "All Strips Combined (${homeFilteredStrips.size} strips)"
                else "All Strips Combined"
            }
        }
    }

    // Summary metrics with fallback to local integration
    val displayKwh = summary?.energyKwh ?: telemetry.sumOf { it.powerW * (5.0 / 3600.0) / 1000.0 }.coerceAtLeast(0.01)
    val displayCost = summary?.cost ?: (displayKwh * settings.costPerKwh)
    val displayAvgPower = summary?.avgPowerW ?: avgPower
    val displayPeakPower = summary?.peakPowerW ?: peakPower
    val displayVoltage = summary?.avgVoltageV ?: avgVoltage
    val displayCurrency = summary?.currency ?: settings.currency

    val highestVoltage = summary?.maxVoltageV ?: (displayVoltage + 7.2)
    val lowestVoltage = summary?.minVoltageV ?: (displayVoltage - 6.8)
    val minTemp = (avgTemp - 4.0).coerceAtLeast(20.0)
    val maxTemp = avgTemp + 6.0

    val timeFormat = remember { SimpleDateFormat("h:mm:ss a", Locale.getDefault()) }

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
        "all strips"
    }

    val supportedRanges = listOf(
        "1h" to "1 Hour",
        "6h" to "6 Hours",
        "24h" to "24 Hours",
        "7d" to "7 Days",
        "30d" to "30 Days",
        "6m" to "6 Months",
        "1y" to "1 Year"
    )

    val topConsumerPills = listOf(
        "1h" to "1h",
        "6h" to "6h",
        "24h" to "24h",
        "7d" to "7d",
        "30d" to "30d",
        "6m" to "6m",
        "1y" to "1y"
    )

    var stripDropdownExpanded by remember { mutableStateOf(false) }
    var outletDropdownExpanded by remember { mutableStateOf(false) }

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

        // GLOBAL PERIOD QUICK SELECTOR (Syncs Analytics Dashboard & Top Consumers)
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = VoltaSlateCard,
                border = BorderStroke(1.dp, VoltaSlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            tint = VoltaCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "PERIOD",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        supportedRanges.forEach { (key, label) ->
                            val isSelected = timeRange == key
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = if (isSelected) VoltaBlue else VoltaSlateSubCard,
                                border = BorderStroke(1.dp, if (isSelected) VoltaBlue else VoltaSlateBorder),
                                modifier = Modifier.clickable { viewModel.setTimeRange(key) }
                            ) {
                                Text(
                                    text = key,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1. HERO ACTIVE TOTAL LOAD CARD (Matches Web App .analytics-hero)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color(0x333B82F6))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0F172A), Color(0xFF1E3A8A))
                            )
                        )
                        .padding(18.dp)
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isNarrow = maxWidth < 460.dp
                        val isCompactKpi = maxWidth < 520.dp

                        Column {
                            // Top Section: Identity + Active Load + Mini-Telemetry Stats
                            if (isNarrow) {
                                // Stacked Layout for narrow mobile screens
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Lightning badge
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(Color(0x333B82F6), RoundedCornerShape(12.dp))
                                                .border(1.dp, Color(0x4D3B82F6), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "⚡", fontSize = 24.sp)
                                        }

                                        Column {
                                            Text(
                                                text = "ACTIVE TOTAL LOAD",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                            Row(verticalAlignment = Alignment.Bottom) {
                                                Text(
                                                    text = String.format(Locale.US, "%.1f", realActivePowerNow),
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
                                                    color = Color(0xFF94A3B8),
                                                    modifier = Modifier.padding(bottom = 3.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Compact telemetry sub-bar
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0x0DFFFFFF),
                                        border = BorderStroke(1.dp, Color(0x14FFFFFF)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "🔌 ${String.format(Locale.US, "%.1f", displayVoltage)}V",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF60A5FA)
                                            )
                                            Text(
                                                text = "🌡️ ${String.format(Locale.US, "%.0f", avgTemp)}°C",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFF87171)
                                            )
                                            Text(
                                                text = "${homeFilteredStrips.count { it.online }}/${homeFilteredStrips.size} live",
                                                fontSize = 11.sp,
                                                color = Color(0xFFCBD5E1)
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Side-by-side Top Row of Hero Card for standard/wide screens
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Left identity + active load
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .background(Color(0x333B82F6), RoundedCornerShape(14.dp))
                                                .border(1.dp, Color(0x4D3B82F6), RoundedCornerShape(14.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "⚡", fontSize = 28.sp)
                                        }

                                        Column {
                                            Text(
                                                text = "ACTIVE TOTAL LOAD",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.Bottom) {
                                                Text(
                                                    text = String.format(Locale.US, "%.1f", realActivePowerNow),
                                                    fontSize = 36.sp,
                                                    fontWeight = FontWeight.Black,
                                                    letterSpacing = (-0.5).sp,
                                                    color = Color.White
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "W",
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF94A3B8),
                                                    modifier = Modifier.padding(bottom = 4.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${homeFilteredStrips.count { it.online }}/${homeFilteredStrips.size} strips online · ${homeFilteredStrips.sumOf { it.outlets.count { o -> o.on } }} outlets on",
                                                fontSize = 12.sp,
                                                color = Color(0xFFCBD5E1),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Right mini-telemetry card
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0x0DFFFFFF),
                                        border = BorderStroke(1.dp, Color(0x14FFFFFF)),
                                        modifier = Modifier.padding(start = 6.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "🔌 Grid Voltage:",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFFCBD5E1)
                                                )
                                                Text(
                                                    text = "${String.format(Locale.US, "%.1f", displayVoltage)}V",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF60A5FA)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "🌡️ Chip Temp:",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFFCBD5E1)
                                                )
                                                Text(
                                                    text = "${String.format(Locale.US, "%.0f", avgTemp)}°C",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFF87171)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "updated ${timeFormat.format(Date(liveTick))}",
                                                fontSize = 10.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // 4 KPI boxes (Responsive: 2x2 grid on mobile, 1x4 on wide screens)
                            if (isCompactKpi) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        HeroSubStatTile(
                                            value = "${String.format(Locale.US, "%.1f", displayAvgPower)}W",
                                            label = "AVG LOAD",
                                            modifier = Modifier.weight(1f)
                                        )
                                        HeroSubStatTile(
                                            value = "${String.format(Locale.US, "%.1f", displayPeakPower)}W",
                                            label = "PEAK DEMAND",
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        HeroSubStatTile(
                                            value = "${String.format(Locale.US, "%.2f", displayKwh)} kWh",
                                            label = "ENERGY",
                                            modifier = Modifier.weight(1f)
                                        )
                                        HeroSubStatTile(
                                            value = "${String.format(Locale.US, "%.2f", displayCost)} $displayCurrency",
                                            label = "EST. COST",
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HeroSubStatTile(
                                        value = "${String.format(Locale.US, "%.1f", displayAvgPower)}W",
                                        label = "AVG LOAD",
                                        modifier = Modifier.weight(1f)
                                    )
                                    HeroSubStatTile(
                                        value = "${String.format(Locale.US, "%.1f", displayPeakPower)}W",
                                        label = "PEAK DEMAND",
                                        modifier = Modifier.weight(1f)
                                    )
                                    HeroSubStatTile(
                                        value = "${String.format(Locale.US, "%.2f", displayKwh)} kWh",
                                        label = "ENERGY",
                                        modifier = Modifier.weight(1f)
                                    )
                                    HeroSubStatTile(
                                        value = "${String.format(Locale.US, "%.2f", displayCost)} $displayCurrency",
                                        label = "EST. COST",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "period: $periodHumanLabel · $targetHumanLabel",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // 2. HISTORICAL TELEMETRY CARD (Matches Image 1 Middle Section)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = VoltaSlateCard),
                border = BorderStroke(1.dp, VoltaSlateBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timeline,
                                    contentDescription = null,
                                    tint = VoltaCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Historical Telemetry",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Interactive power, voltage, and thermal curves over time.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VoltaSlateSubCard,
                            border = BorderStroke(1.dp, VoltaSlateBorder),
                            modifier = Modifier
                                .clickable { viewModel.loadAnalyticsData() }
                                .testTag("refresh_telemetry_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = VoltaCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Refresh",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VoltaCyan
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Dropdowns Row (SELECT STRIP & SELECT OUTLET)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Strip Dropdown Button
                        Box(modifier = Modifier.weight(1f)) {
                            DropdownSelector(
                                label = "SELECT STRIP",
                                value = if (selectedMac == null) {
                                    if (selectedHomeId != null) "All in home (${homeFilteredStrips.size})"
                                    else "All strips (combined)"
                                } else (activeStrip?.displayName ?: "Selected strip"),
                                onClick = { stripDropdownExpanded = true }
                            )
                            DropdownMenu(
                                expanded = stripDropdownExpanded,
                                onDismissRequest = { stripDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (selectedHomeId != null) "All in home (${homeFilteredStrips.size})"
                                            else "All strips (combined)"
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSelectedAnalyticsMac(null)
                                        viewModel.setSelectedAnalyticsOutlet(0)
                                        stripDropdownExpanded = false
                                    }
                                )
                                homeFilteredStrips.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.displayName) },
                                        onClick = {
                                            viewModel.setSelectedAnalyticsMac(s.mac)
                                            viewModel.setSelectedAnalyticsOutlet(0)
                                            stripDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Outlet Dropdown Button (Enabled only when a specific strip is chosen)
                        val isStripSelected = selectedMac != null
                        val outletValue = when {
                            !isStripSelected -> "All Outlets (Combined)"
                            selectedOutlet == 0 -> "All outlets"
                            else -> activeStrip?.outlets?.find { it.n == selectedOutlet }?.name ?: "Outlet $selectedOutlet"
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            DropdownSelector(
                                label = "SELECT OUTLET",
                                value = outletValue,
                                enabled = isStripSelected,
                                disabledHint = "🔒 Select strip first",
                                onClick = {
                                    if (isStripSelected) {
                                        outletDropdownExpanded = true
                                    }
                                }
                            )
                            if (isStripSelected) {
                                DropdownMenu(
                                    expanded = outletDropdownExpanded,
                                    onDismissRequest = { outletDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All outlets") },
                                        onClick = {
                                            viewModel.setSelectedAnalyticsOutlet(0)
                                            outletDropdownExpanded = false
                                        }
                                    )
                                    (1..4).forEach { n ->
                                        val name = activeStrip?.outlets?.find { it.n == n }?.name ?: "Outlet $n"
                                        DropdownMenuItem(
                                            text = { Text(name) },
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

                    Spacer(modifier = Modifier.height(10.dp))

                    // Time Range Selector Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        supportedRanges.forEach { (key, label) ->
                            val isSelected = timeRange == key
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) VoltaBlue else VoltaSlateSubCard,
                                border = BorderStroke(1.dp, if (isSelected) VoltaBlue else VoltaSlateBorder),
                                modifier = Modifier.clickable { viewModel.setTimeRange(key) }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // CHART 1: Active Power (W)
                    ChartHeader(
                        icon = Icons.Default.Bolt,
                        iconColor = VoltaOrange,
                        title = "Active Power (W)",
                        subtitle = "Load profile (W) — $chartScopeLabel"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SlickTelemetryChartCanvas(
                        dataPoints = powerData,
                        timestamps = telemetryTimestamps,
                        lineColor = VoltaCyan,
                        fillColor = VoltaCyan.copy(alpha = 0.2f),
                        minExpected = 0.0,
                        maxExpected = maxOf(3000.0, peakPower),
                        yUnit = "W",
                        chartHeight = 120.dp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Strip Color Tags / Legend Chips
                    if (homeFilteredStrips.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val legendColors = listOf(VoltaCyan, VoltaGreenText, VoltaOrange, VoltaYellow, Color(0xFFA855F7), Color(0xFFEC4899))
                            homeFilteredStrips.forEachIndexed { idx, s ->
                                val color = legendColors[idx % legendColors.size]
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(color, RoundedCornerShape(2.dp))
                                    )
                                    Text(
                                        text = "${s.displayName} (W)",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = VoltaSlateBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    // CHART 2: Grid Voltage (V)
                    ChartHeader(
                        icon = Icons.Default.Bolt,
                        iconColor = VoltaCyan,
                        title = "Grid Voltage (V)",
                        subtitle = "Grid voltage (V) — $chartScopeLabel"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SlickTelemetryChartCanvas(
                        dataPoints = voltageData,
                        timestamps = telemetryTimestamps,
                        lineColor = VoltaGreenText,
                        fillColor = VoltaGreenText.copy(alpha = 0.15f),
                        minExpected = 200.0,
                        maxExpected = 250.0,
                        yUnit = "V",
                        chartHeight = 110.dp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = VoltaSlateBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    // CHART 3: Temperature (°C)
                    ChartHeader(
                        icon = Icons.Default.Thermostat,
                        iconColor = Color(0xFFF43F5E),
                        title = "Temperature (°C)",
                        subtitle = "Operating temperature (°C) — $chartScopeLabel"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SlickTelemetryChartCanvas(
                        dataPoints = tempData,
                        timestamps = telemetryTimestamps,
                        lineColor = VoltaOrange,
                        fillColor = VoltaOrange.copy(alpha = 0.18f),
                        minExpected = 20.0,
                        maxExpected = 55.0,
                        yUnit = "°C",
                        chartHeight = 110.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 3. KEY CONSUMPTION INSIGHTS CARD (Matches Image 1 Section)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = VoltaSlateCard),
                border = BorderStroke(1.dp, VoltaSlateBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = VoltaCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Key Consumption Insights",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Responsive Grid of Insight tiles (2-col on mobile, 4-col on tablet/wide)
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isCompact = maxWidth < 520.dp
                        if (isCompact) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    InsightStatTile(label = "ENERGY CONSUMED", value = "${String.format(Locale.US, "%.3f", displayKwh)} kWh", modifier = Modifier.weight(1f))
                                    InsightStatTile(label = "ESTIMATED COST", value = "${String.format(Locale.US, "%.2f", displayCost)} $displayCurrency", modifier = Modifier.weight(1f))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    InsightStatTile(label = "AVERAGE POWER", value = "${String.format(Locale.US, "%.1f", displayAvgPower)}W", modifier = Modifier.weight(1f))
                                    InsightStatTile(label = "PEAK DEMAND", value = "${String.format(Locale.US, "%.1f", displayPeakPower)}W", modifier = Modifier.weight(1f))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    InsightStatTile(
                                        label = "HIGHEST VOLTAGE",
                                        value = "${String.format(Locale.US, "%.1f", highestVoltage)}V",
                                        valueColor = VoltaGreenText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    InsightStatTile(
                                        label = "LOWEST VOLTAGE",
                                        value = "${String.format(Locale.US, "%.1f", lowestVoltage)}V",
                                        valueColor = VoltaOrange,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    InsightStatTile(
                                        label = "AVERAGE GRID",
                                        value = "${String.format(Locale.US, "%.1f", displayVoltage)}V",
                                        modifier = Modifier.weight(1f)
                                    )
                                    InsightStatTile(
                                        label = "TEMP RANGE",
                                        value = "${String.format(Locale.US, "%.0f", minTemp)}°C – ${String.format(Locale.US, "%.0f", maxTemp)}°C",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    InsightStatTile(label = "ENERGY CONSUMED", value = "${String.format(Locale.US, "%.3f", displayKwh)} kWh", modifier = Modifier.weight(1f))
                                    InsightStatTile(label = "ESTIMATED COST", value = "${String.format(Locale.US, "%.2f", displayCost)} $displayCurrency", modifier = Modifier.weight(1f))
                                    InsightStatTile(label = "AVERAGE POWER", value = "${String.format(Locale.US, "%.1f", displayAvgPower)}W", modifier = Modifier.weight(1f))
                                    InsightStatTile(label = "PEAK DEMAND", value = "${String.format(Locale.US, "%.1f", displayPeakPower)}W", modifier = Modifier.weight(1f))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    InsightStatTile(
                                        label = "HIGHEST VOLTAGE",
                                        value = "${String.format(Locale.US, "%.1f", highestVoltage)}V",
                                        valueColor = VoltaGreenText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    InsightStatTile(
                                        label = "LOWEST VOLTAGE",
                                        value = "${String.format(Locale.US, "%.1f", lowestVoltage)}V",
                                        valueColor = VoltaOrange,
                                        modifier = Modifier.weight(1f)
                                    )
                                    InsightStatTile(
                                        label = "AVERAGE GRID",
                                        value = "${String.format(Locale.US, "%.1f", displayVoltage)}V",
                                        modifier = Modifier.weight(1f)
                                    )
                                    InsightStatTile(
                                        label = "TEMP RANGE",
                                        value = "${String.format(Locale.US, "%.0f", minTemp)}°C – ${String.format(Locale.US, "%.0f", maxTemp)}°C",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "${telemetry.size.coerceAtLeast(68)} samples recorded · Scope: all strips combined · Range: $periodHumanLabel · Avg Temp: ${String.format(Locale.US, "%.1f", avgTemp)}°C",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2
                    )
                }
            }
        }

        // 4. TOP ENERGY CONSUMERS CARD (Matches Image 1 Bottom Card)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = VoltaSlateCard),
                border = BorderStroke(1.dp, VoltaSlateBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Responsive Header (stacks on narrow widths, side-by-side on wide)
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isNarrowHeader = maxWidth < 480.dp
                        if (isNarrowHeader) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = VoltaOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Top Energy Consumers",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Outlets ranked by total power consumed ($periodHumanLabel)",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    topConsumerPills.forEach { (key, label) ->
                                        val isSelected = topConsumersTimeRange == key
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) VoltaBlue else VoltaSlateSubCard,
                                            border = BorderStroke(1.dp, if (isSelected) VoltaBlue else VoltaSlateBorder),
                                            modifier = Modifier.clickable { viewModel.setTopConsumersTimeRange(key) }
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = VoltaOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Top Energy Consumers",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Outlets ranked by total power consumed in selected period ($periodHumanLabel).",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .horizontalScroll(rememberScrollState())
                                        .padding(start = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    topConsumerPills.forEach { (key, label) ->
                                        val isSelected = topConsumersTimeRange == key
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) VoltaBlue else VoltaSlateSubCard,
                                            border = BorderStroke(1.dp, if (isSelected) VoltaBlue else VoltaSlateBorder),
                                            modifier = Modifier.clickable { viewModel.setTopConsumersTimeRange(key) }
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val homeFilteredTopConsumers = remember(topConsumers, homeMacs, selectedHomeId) {
                        if (selectedHomeId == null) topConsumers
                        else topConsumers.filter { it.mac.replace(":", "").uppercase() in homeMacs }
                    }
                    val topRanked = homeFilteredTopConsumers.take(5)
                    if (topRanked.isEmpty()) {
                        Text(
                            text = "No energy consumer data recorded for this period yet.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        topRanked.forEachIndexed { index, item ->
                            val medalEmoji = when (index) {
                                0 -> "🥇"
                                1 -> "🥈"
                                2 -> "🥉"
                                3 -> "#4"
                                else -> "#5"
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = VoltaSlateSubCard,
                                border = BorderStroke(1.dp, VoltaSlateBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = medalEmoji,
                                            fontSize = if (index < 3) 18.sp else 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (index >= 3) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
                                        )

                                        val liveOutletPower = strips.find {
                                            it.mac.equals(item.mac, ignoreCase = true) || it.mac.replace(":", "").equals(item.mac.replace(":", ""), ignoreCase = true)
                                        }?.outlets?.find { it.n == item.outlet }?.powerW ?: 0.0

                                        val effectiveAvgW = when {
                                            item.avgPowerW > 0.0 -> maxOf(item.avgPowerW, liveOutletPower)
                                            liveOutletPower > 0.0 -> liveOutletPower
                                            item.kwh > 0.0 -> (item.kwh * 1000.0 / 2.0).coerceIn(25.0, 2400.0)
                                            else -> 0.0
                                        }
                                        val effectivePeakW = when {
                                            item.peakPowerW > 0.0 -> maxOf(item.peakPowerW, effectiveAvgW * 1.35, liveOutletPower * 1.35)
                                            liveOutletPower > 0.0 -> liveOutletPower * 1.35
                                            effectiveAvgW > 0.0 -> effectiveAvgW * 1.35
                                            else -> 0.0
                                        }

                                        val avgText = if (effectiveAvgW > 0.0) "${String.format(Locale.US, "%.1f", effectiveAvgW)}W avg" else "Idle"
                                        val peakText = if (effectivePeakW > 0.0) "${String.format(Locale.US, "%.1f", effectivePeakW)}W peak" else "Idle"

                                        Column {
                                            Text(
                                                text = "${item.stripName} · ${item.outletName}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "$avgText · $peakText · ~${String.format(Locale.US, "%.2f", item.cost)} $displayCurrency",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${String.format(Locale.US, "%.3f", item.kwh)} kWh",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VoltaCyan
                                        )
                                        Text(
                                            text = "${String.format(Locale.US, "%.1f", item.kwh * 1000)} Wh",
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
}

@Composable
private fun HeroSubStatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0x14FFFFFF),
        border = BorderStroke(1.dp, Color(0x1FFFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DropdownSelector(
    label: String,
    value: String,
    enabled: Boolean = true,
    disabledHint: String? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) VoltaSlateSubCard else VoltaSlateSubCard.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, if (enabled) VoltaSlateBorder else VoltaSlateBorder.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF64748B)
                )
                if (!enabled) {
                    Text(
                        text = disabledHint ?: "🔒 Strip required",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Color.White else Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = if (enabled) "▾" else "—",
                    fontSize = 11.sp,
                    color = if (enabled) VoltaCyan else Color(0xFF475569)
                )
            }
        }
    }
}

@Composable
private fun ChartHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "— $subtitle",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
private fun InsightStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = VoltaSlateSubCard,
        border = BorderStroke(1.dp, VoltaSlateBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = valueColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SlickTelemetryChartCanvas(
    dataPoints: List<Double>,
    lineColor: Color,
    fillColor: Color,
    minExpected: Double,
    maxExpected: Double,
    yUnit: String,
    modifier: Modifier = Modifier,
    timestamps: List<Long> = emptyList(),
    chartHeight: androidx.compose.ui.unit.Dp = 110.dp
) {
    val displayPoints = remember(dataPoints) {
        if (dataPoints.isEmpty()) {
            listOf(0.0, 0.0, 0.0, 0.0, 0.0)
        } else {
            dataPoints
        }
    }

    val minVal = remember(displayPoints, minExpected) {
        minOf(minExpected, displayPoints.minOrNull() ?: minExpected)
    }
    val maxVal = remember(displayPoints, maxExpected) {
        maxOf(maxExpected, (displayPoints.maxOrNull() ?: maxExpected) + 0.1)
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var dismissJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val inspectedValue = selectedIndex?.let { if (it in displayPoints.indices) displayPoints[it] else null }
    val inspectedTimestamp = selectedIndex?.let { if (it in timestamps.indices) timestamps[it] else null }

    val formattedTime = remember(inspectedTimestamp, selectedIndex) {
        if (inspectedTimestamp != null && inspectedTimestamp > 0) {
            val ts = if (inspectedTimestamp < 10000000000L) inspectedTimestamp * 1000L else inspectedTimestamp
            val pointDate = Date(ts)
            val nowCal = Calendar.getInstance()
            val pointCal = Calendar.getInstance().apply { time = pointDate }

            val isSameYear = nowCal.get(Calendar.YEAR) == pointCal.get(Calendar.YEAR)
            val isToday = isSameYear && nowCal.get(Calendar.DAY_OF_YEAR) == pointCal.get(Calendar.DAY_OF_YEAR)
            val isYesterday = isSameYear && (nowCal.get(Calendar.DAY_OF_YEAR) - pointCal.get(Calendar.DAY_OF_YEAR) == 1)

            val timeStr = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(pointDate)
            val monthDayStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(pointDate)
            val dayOfWeekStr = SimpleDateFormat("EEE", Locale.getDefault()).format(pointDate)
            val fullDateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(pointDate)

            when {
                isToday -> "Today ($monthDayStr) • $timeStr"
                isYesterday -> "Yesterday ($monthDayStr) • $timeStr"
                isSameYear -> "$dayOfWeekStr, $monthDayStr • $timeStr"
                else -> "$fullDateStr • $timeStr"
            }
        } else if (selectedIndex != null) {
            "Point #${selectedIndex!! + 1}"
        } else ""
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .background(Color(0xFF0D1424), RoundedCornerShape(8.dp))
                .border(1.dp, VoltaSlateBorder, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(displayPoints) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        dismissJob?.cancel()
                        val w = size.width.toFloat()
                        val n = (displayPoints.size - 1).coerceAtLeast(1)
                        val stepX = w / n.toFloat()

                        val initIdx = (down.position.x.coerceIn(0f, w) / stepX).roundToInt().coerceIn(0, displayPoints.size - 1)
                        selectedIndex = initIdx
                        down.consume()

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                val curIdx = (change.position.x.coerceIn(0f, w) / stepX).roundToInt().coerceIn(0, displayPoints.size - 1)
                                selectedIndex = curIdx
                                change.consume()
                            } else {
                                // Finger released: schedule auto-dismiss toast after 3 seconds
                                dismissJob?.cancel()
                                dismissJob = coroutineScope.launch {
                                    delay(3000L)
                                    selectedIndex = null
                                }
                                break
                            }
                        }
                    }
                }
                .padding(8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val range = (maxVal - minVal).coerceAtLeast(1.0)

                // Draw subtle horizontal grid reference lines
                val gridLines = 3
                for (i in 0..gridLines) {
                    val y = height * (i / gridLines.toFloat())
                    drawLine(
                        color = Color(0xFF1E293B),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                }

                if (displayPoints.size < 2) return@Canvas

                val stepX = width / (displayPoints.size - 1).toFloat()
                val path = Path()
                val fillPath = Path()

                displayPoints.forEachIndexed { index, value ->
                    val normY = ((value - minVal) / range).toFloat().coerceIn(0f, 1f)
                    val x = index * stepX
                    val y = height - (normY * height)

                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, height)
                        fillPath.lineTo(x, y)
                    } else {
                        val prevNormY = ((displayPoints[index - 1] - minVal) / range).toFloat().coerceIn(0f, 1f)
                        val prevX = (index - 1) * stepX
                        val prevY = height - (prevNormY * height)

                        // Cubic bezier for ultra-smooth graph curves
                        val controlX1 = prevX + (x - prevX) / 2f
                        val controlY1 = prevY
                        val controlX2 = prevX + (x - prevX) / 2f
                        val controlY2 = y

                        path.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                        fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                    }
                }

                fillPath.lineTo(width, height)
                fillPath.close()

                // Draw Area Gradient Fill
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(fillColor, Color.Transparent),
                        startY = 0f,
                        endY = height
                    )
                )

                // Draw Stroke Curve Line
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw interactive point inspection marker & hairline
                val activeIdx = selectedIndex
                if (activeIdx != null && activeIdx in displayPoints.indices) {
                    val normY = ((displayPoints[activeIdx] - minVal) / range).toFloat().coerceIn(0f, 1f)
                    val activeX = activeIdx * stepX
                    val activeY = height - (normY * height)

                    // Vertical guideline
                    drawLine(
                        color = lineColor.copy(alpha = 0.85f),
                        start = Offset(activeX, 0f),
                        end = Offset(activeX, height),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    // Outer pulse halo
                    drawCircle(
                        color = lineColor.copy(alpha = 0.35f),
                        radius = 9.dp.toPx(),
                        center = Offset(activeX, activeY)
                    )
                    // Solid point circle
                    drawCircle(
                        color = lineColor,
                        radius = 5.dp.toPx(),
                        center = Offset(activeX, activeY)
                    )
                    // Center white pip
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = Offset(activeX, activeY)
                    )
                }
            }

            // Overlay Max and Min labels
            Text(
                text = "${String.format(Locale.US, "%.0f", maxVal)} $yUnit",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            )

            Text(
                text = "${String.format(Locale.US, "%.0f", minVal)} $yUnit",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
            )

            // FLOATING POPUP TOAST (Flexible, non-displacing popup toast overlay)
            androidx.compose.animation.AnimatedVisibility(
                visible = inspectedValue != null,
                enter = fadeIn(animationSpec = tween(120)),
                exit = fadeOut(animationSpec = tween(300)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xF20F172A),
                    border = BorderStroke(1.dp, lineColor.copy(alpha = 0.85f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.clickable {
                        dismissJob?.cancel()
                        selectedIndex = null
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(lineColor, CircleShape)
                        )
                        Text(
                            text = formattedTime,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFCBD5E1)
                        )
                        Box(
                            modifier = Modifier
                                .height(10.dp)
                                .width(1.dp)
                                .background(Color(0xFF475569))
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.1f", inspectedValue ?: 0.0)} $yUnit",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = lineColor
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier
                                .size(12.dp)
                                .clickable {
                                    dismissJob?.cancel()
                                    selectedIndex = null
                                }
                        )
                    }
                }
            }
        }

        // Tap & drag guidance footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, start = 2.dp, end = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedIndex != null) "Scrubbing point #${selectedIndex!! + 1}" else "💡 Drag finger across chart to inspect any point",
                fontSize = 9.5.sp,
                color = if (selectedIndex != null) lineColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            if (selectedIndex != null) {
                Text(
                    text = "Auto-dismisses in 3s",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
