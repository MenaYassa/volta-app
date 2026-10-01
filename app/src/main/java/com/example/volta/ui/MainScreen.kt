package com.example.volta.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaNavy
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

enum class VoltaTab(val title: String) {
    CONTROL("Control"),
    ANALYTICS("Analytics"),
    STRIPS("Strips"),
    SCHEDULES("Schedules"),
    SETUP("Setup"),
    TOOLS("Advanced"),
    SETTINGS("Settings")
}

@Composable
fun MainScreen(
    viewModel: VoltaViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val strips by viewModel.strips.collectAsState()

    val onlineCount = strips.count { it.online }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // Volta Gradient Hero Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(VoltaNavy, Color(0xFF1E3A8A))
                        )
                    )
                    .statusBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                ) {
                    // Top Bar with Brand, Status & Theme Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusDot(isOnline = onlineCount > 0)
                            Text(
                                text = "⚡ Volta",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = connectionStatus,
                                fontSize = 11.sp,
                                color = Color(0xFFCBD5E1),
                                maxLines = 1
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "$onlineCount live",
                                fontSize = 11.sp,
                                color = Color(0xFFCBD5E1)
                            )
                            IconButton(
                                onClick = { viewModel.toggleDarkTheme() },
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                    .testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle Theme",
                                    tint = VoltaYellow,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tab Navigation Row
                    ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        edgePadding = 0.dp,
                        divider = {},
                        indicator = { tabPositions ->
                            if (selectedTabIndex < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                    height = 3.dp,
                                    color = Color.White
                                )
                            }
                        }
                    ) {
                        VoltaTab.values().forEachIndexed { index, tab ->
                            val isSelected = selectedTabIndex == index
                            Tab(
                                selected = isSelected,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Text(
                                        text = tab.title,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                                    )
                                },
                                modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                            )
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
