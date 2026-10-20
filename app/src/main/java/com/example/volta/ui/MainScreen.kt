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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaCyan
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaGreenBright
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaNavy
import com.example.volta.theme.VoltaRed
import com.example.volta.theme.VoltaYellow
import com.example.volta.ui.analytics.AnalyticsScreen
import com.example.volta.ui.components.AccountExpiredScreen
import com.example.volta.ui.components.StatusDot
import com.example.volta.ui.components.VoltaSignInDialog
import com.example.volta.ui.control.ControlScreen
import com.example.volta.ui.schedules.SchedulesScreen
import com.example.volta.ui.settings.SettingsScreen
import com.example.volta.ui.setup.SetupScreen
import com.example.volta.viewmodel.VoltaViewModel

enum class VoltaTab(val title: String, val icon: ImageVector) {
    CONTROL("Control", Icons.Default.PowerSettingsNew),
    ANALYTICS("Analytics", Icons.Default.Insights),
    SCHEDULES("Schedules", Icons.Default.AccessTime),
    SETUP("Setup", Icons.Default.Build),
    SETTINGS("Settings", Icons.Default.Tune)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: VoltaViewModel,
    modifier: Modifier = Modifier
) {
    val selectedTabIndex by viewModel.selectedTab.collectAsState()
    val authRequired by viewModel.authRequired.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val strips by viewModel.strips.collectAsState()
    val whoAmI by viewModel.whoAmI.collectAsState()
    var showSignInDialog by remember { mutableStateOf(false) }

    val onlineCount = strips.count { it.online }
    val totalCount = strips.size

    val serverSubtitle = if (settings.isStandalone) {
        "Local Controller • Standalone"
    } else {
        val cleanUrl = settings.serverUrl
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        if (cleanUrl.isNotBlank()) cleanUrl else "Offline"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // Modernized Glassmorphic Command Header
            Surface(
                color = Color(0xFF0B132B),
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0B132B), Color(0xFF141F38), Color(0xFF0F172A))
                            )
                        )
                        .statusBarsPadding()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Top Brand, Fleet Status & Profile Action Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left Brand Block with Glowing Icon & Gateway Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_volta_logo),
                                    contentDescription = "Volta",
                                    modifier = Modifier.size(34.dp)
                                )

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "VOLTA",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.2.sp,
                                            color = Color.White
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(99.dp),
                                            color = Color(0x1A38BDF8),
                                            border = BorderStroke(1.dp, Color(0x3338BDF8))
                                        ) {
                                            Text(
                                                text = if (settings.isStandalone) "LOCAL" else "CLOUD",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = VoltaCyan,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        StatusDot(
                                            isOnline = if (settings.isStandalone) onlineCount > 0 else (onlineCount > 0 || settings.serverUrl.isNotBlank()),
                                            modifier = Modifier.size(6.5.dp)
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
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Right Controls: Pill Fleet Badge, Avatar & Quick Actions
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Dynamic Fleet Live Pill
                                Surface(
                                    shape = RoundedCornerShape(99.dp),
                                    color = if (onlineCount > 0) Color(0x1F22C55E) else Color(0x1AE2E8F0),
                                    border = BorderStroke(
                                        1.dp,
                                        if (onlineCount > 0) Color(0x4D22C55E) else Color(0x26E2E8F0)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.5.dp)
                                                .background(
                                                    if (onlineCount > 0) VoltaGreenBright else Color(0xFF64748B),
                                                    CircleShape
                                                )
                                        )
                                        Text(
                                            text = "$onlineCount/$totalCount live",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (onlineCount > 0) Color.White else Color(0xFF94A3B8),
                                            softWrap = false,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // Interactive Profile Avatar
                                val initials = if (whoAmI != null && whoAmI?.name.orEmpty().isNotBlank()) {
                                    whoAmI?.name.orEmpty().split(" ")
                                        .filter { it.isNotBlank() }
                                        .take(2)
                                        .map { it.first().uppercaseChar() }
                                        .joinToString("")
                                } else if (whoAmI != null && whoAmI?.email.orEmpty().isNotBlank()) {
                                    whoAmI?.email.orEmpty().take(2).uppercase()
                                } else "U"

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))
                                            ),
                                            CircleShape
                                        )
                                        .border(1.5.dp, Color(0x6638BDF8), CircleShape)
                                        .clickable {
                                            if (authRequired) showSignInDialog = true
                                            else viewModel.setSelectedTab(VoltaTab.SETTINGS.ordinal)
                                        }
                                        .testTag("top_user_profile_avatar")
                                ) {
                                    Text(
                                        text = initials,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                }

                                // Quick Settings Shortcut
                                Surface(
                                    shape = CircleShape,
                                    color = if (selectedTabIndex == VoltaTab.SETTINGS.ordinal) Color(0x332563EB) else Color(0x1FFFFFFF),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selectedTabIndex == VoltaTab.SETTINGS.ordinal) VoltaBlue else Color(0x26FFFFFF)
                                    ),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable { viewModel.setSelectedTab(VoltaTab.SETTINGS.ordinal) }
                                        .testTag("top_settings_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings",
                                            tint = if (selectedTabIndex == VoltaTab.SETTINGS.ordinal) VoltaCyan else Color(0xFFE2E8F0),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Compact, Professional Navigation Tab Row
                        SecondaryScrollableTabRow(
                            selectedTabIndex = selectedTabIndex,
                            modifier = Modifier.height(42.dp),
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                            edgePadding = 12.dp,
                            indicator = {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(selectedTabIndex),
                                    height = 2.5.dp,
                                    color = VoltaBlue
                                )
                            },
                            divider = {},
                            tabs = {
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
                                                    tint = if (isSelected) Color.White else Color(
                                                        0xFF94A3B8
                                                    )
                                                )
                                                Text(
                                                    text = tab.title,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color.White else Color(
                                                        0xFF94A3B8
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                                    )
                                }
                            })

                        // Security Auth Banner if Token Missing or 401 Unauthorized (unless expired lock screen is active)
                        val isAccountExpired = !settings.isStandalone && whoAmI != null && (whoAmI?.subscriptionExpired == true || whoAmI?.status == "expired" || whoAmI?.status == "canceled")
                        if (authRequired && !isAccountExpired) {
                            Surface(
                                color = VoltaRed,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSignInDialog = true }
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
                                            text = "Authentication Required: Sign in with Google or enter user token.",
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
                                            text = "Sign In",
                                            color = VoltaRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                        // Subscription Expiry Warning Banner (<= 30 days amber, <= 7 days or expired red)
                        if (!settings.isStandalone && whoAmI != null) {
                            val expiresAt = whoAmI?.expiresAt
                            val nowSeconds = remember { System.currentTimeMillis() / 1000 }
                            val daysRemaining = if (expiresAt != null) ((expiresAt - nowSeconds) / 86400).toInt() else null
                            val isExpired = whoAmI?.subscriptionExpired == true || (daysRemaining != null && daysRemaining <= 0)
                            val isUrgent = daysRemaining != null && daysRemaining in 1..7
                            val isWarning = daysRemaining != null && daysRemaining in 8..30

                            if (isExpired || isUrgent || isWarning) {
                                val bannerColor = if (isExpired || isUrgent) VoltaRed else Color(0xFFF59E0B)
                                val bannerTitle = when {
                                    isExpired -> "Subscription Expired"
                                    isUrgent -> "Subscription Expiring Soon"
                                    else -> "Renewal Notice"
                                }
                                val bannerText = when {
                                    isExpired -> "Your plan has expired. Please contact administrator to renew."
                                    isUrgent -> "Only $daysRemaining days left! Contact admin to renew."
                                    else -> "Your subscription ends in $daysRemaining days."
                                }

                                Surface(
                                    color = bannerColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("subscription_warning_banner")
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
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "$bannerTitle: $bannerText",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
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
    ) { innerPadding ->
        val isAccountLocked = !settings.isStandalone && whoAmI != null && (whoAmI?.subscriptionExpired == true || whoAmI?.status == "expired" || whoAmI?.status == "canceled")
        if (isAccountLocked) {
            AccountExpiredScreen(
                viewModel = viewModel,
                whoAmI = whoAmI,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val isWide = maxWidth >= 840.dp
                val contentModifier = if (isWide) {
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = (maxWidth - 840.dp) / 2)
                } else {
                    Modifier.fillMaxSize()
                }

                Box(modifier = contentModifier) {
                    when (VoltaTab.values()[selectedTabIndex.coerceIn(0, VoltaTab.values().size - 1)]) {
                        VoltaTab.CONTROL -> ControlScreen(viewModel)
                        VoltaTab.ANALYTICS -> AnalyticsScreen(viewModel)
                        VoltaTab.SCHEDULES -> SchedulesScreen(viewModel)
                        VoltaTab.SETUP -> SetupScreen(viewModel)
                        VoltaTab.SETTINGS -> SettingsScreen(viewModel)
                    }
                }
            }
        }
    }

    if (showSignInDialog) {
        VoltaSignInDialog(
            viewModel = viewModel,
            onDismiss = { showSignInDialog = false }
        )
    }
}
