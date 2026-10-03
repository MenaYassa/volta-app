package com.example.volta.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaNavy
import com.example.volta.theme.VoltaRed
import com.example.volta.theme.VoltaYellow
import com.example.volta.ui.analytics.AnalyticsScreen
import com.example.volta.ui.components.StatusDot
import com.example.volta.ui.control.ControlScreen
import com.example.volta.ui.schedules.SchedulesScreen
import com.example.volta.ui.settings.SettingsScreen
import com.example.volta.ui.setup.SetupScreen
import com.example.volta.ui.strips.StripsScreen
import com.example.volta.ui.tools.ToolsScreen
import com.example.volta.viewmodel.VoltaViewModel

enum class VoltaTab(val title: String, val icon: ImageVector) {
    CONTROL("Control", Icons.Default.PowerSettingsNew),
    ANALYTICS("Analytics", Icons.Default.Insights),
    STRIPS("Strips", Icons.Default.Dns),
    SCHEDULES("Schedules", Icons.Default.AccessTime),
    SETUP("Setup", Icons.Default.Build),
    TOOLS("Tools", Icons.Default.Terminal),
    SETTINGS("Settings", Icons.Default.Tune)
}

@Composable
fun MainScreen(
    viewModel: VoltaViewModel,
    modifier: Modifier = Modifier
) {
    val selectedTabIndex by viewModel.selectedTab.collectAsState()
    val authRequired by viewModel.authRequired.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val strips by viewModel.strips.collectAsState()

    val onlineCount = strips.count { it.online }
    val totalCount = strips.size

    val serverSubtitle = if (settings.isStandalone) {
        "Local Controller • Standalone"
    } else {
        val cleanUrl = settings.serverUrl
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        if (cleanUrl.isNotBlank()) "Server • $cleanUrl" else "Gateway Offline"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // Streamlined, Compact Professional Header
            Surface(
                color = VoltaNavy,
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(VoltaNavy, Color(0xFF0F2347))
                            )
                        )
                        .statusBarsPadding()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Compact Top Title Bar (No overlap, smaller height)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left Column: Brand name + Connected server under it
                            Column(
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "⚡",
                                        fontSize = 17.sp
                                    )
                                    Text(
                                        text = "VOLTA",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.2.sp,
                                        color = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                // Connected server status placed directly UNDER Volta name
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StatusDot(
                                        isOnline = if (settings.isStandalone) onlineCount > 0 else (onlineCount > 0 || settings.serverUrl.isNotBlank()),
                                        modifier = Modifier.size(7.dp)
                                    )
                                    Text(
                                        text = serverSubtitle,
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8),
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Right Action Controls: Devices Count Pill & Settings Button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Connected devices count pill
                                Box(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(14.dp))
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(if (onlineCount > 0) VoltaGreen else Color(0xFF64748B), CircleShape)
                                        )
                                        Text(
                                            text = "$onlineCount/$totalCount live",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                }

                                // Quick Settings Button (Theme toggle moved inside Settings)
                                IconButton(
                                    onClick = { viewModel.setSelectedTab(VoltaTab.SETTINGS.ordinal) },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(
                                            if (selectedTabIndex == VoltaTab.SETTINGS.ordinal) VoltaBlue.copy(alpha = 0.25f)
                                            else Color.White.copy(alpha = 0.09f),
                                            CircleShape
                                        )
                                        .testTag("top_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = if (selectedTabIndex == VoltaTab.SETTINGS.ordinal) VoltaBlue else Color(0xFFCBD5E1),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }

                        // Compact, Professional Navigation Tab Row
                        ScrollableTabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                            edgePadding = 12.dp,
                            divider = {},
                            indicator = { tabPositions ->
                                if (selectedTabIndex < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                        height = 2.5.dp,
                                        color = VoltaBlue
                                    )
                                }
                            },
                            modifier = Modifier.height(42.dp)
                        ) {
                            VoltaTab.values().forEachIndexed { index, tab ->
                                val isSelected = selectedTabIndex == index
                                Tab(
                                    selected = isSelected,
                                    onClick = { viewModel.setSelectedTab(index) },
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = tab.title,
                                                modifier = Modifier.size(14.dp),
                                                tint = if (isSelected) Color.White else Color(0xFF94A3B8)
                                            )
                                            Text(
                                                text = tab.title,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else Color(0xFF94A3B8)
                                            )
                                        }
                                    },
                                    modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                                )
                            }
                        }

                        // Security Auth Banner if Token Missing or 401 Unauthorized
                        if (authRequired) {
                            Surface(
                                color = VoltaRed,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.navigateToTokenConfiguration() }
                                    .testTag("auth_required_banner")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Token required: Server requires authentication.",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Configure Token",
                                            color = VoltaRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (VoltaTab.values()[selectedTabIndex]) {
                VoltaTab.CONTROL -> ControlScreen(viewModel)
                VoltaTab.ANALYTICS -> AnalyticsScreen(viewModel)
                VoltaTab.STRIPS -> StripsScreen(viewModel)
                VoltaTab.SCHEDULES -> SchedulesScreen(viewModel)
                VoltaTab.SETUP -> SetupScreen(viewModel)
                VoltaTab.TOOLS -> ToolsScreen(viewModel)
                VoltaTab.SETTINGS -> SettingsScreen(viewModel)
            }
        }
    }
}
