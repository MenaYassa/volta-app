package com.example.volta.ui.settings

import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.example.volta.theme.VoltaNavy
import com.example.volta.ui.tools.ToolsScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.volta.model.VoltaSettings
import com.example.volta.theme.VoltaAmber
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaRed
import com.example.volta.theme.VoltaYellow
import com.example.volta.ui.components.AlexaIntegrationCard
import com.example.volta.ui.components.GoogleUserProfileCard
import com.example.volta.ui.components.TagBadge
import com.example.volta.ui.components.VoltaLoginCard
import com.example.volta.viewmodel.VoltaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

@Composable
fun SettingsScreen(
    viewModel: VoltaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    var hasNotifPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasWifiPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        )
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { map ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasNotifPermission = map[Manifest.permission.POST_NOTIFICATIONS] ?: hasNotifPermission
                hasWifiPermission = map[Manifest.permission.NEARBY_WIFI_DEVICES] ?: hasWifiPermission
            }
            hasLocationPermission = map[Manifest.permission.ACCESS_FINE_LOCATION] ?: hasLocationPermission
            if (hasNotifPermission) {
                viewModel.sendTestNotification()
            }
        }
    )

    fun requestAllPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        multiplePermissionsLauncher.launch(perms.toTypedArray())
    }

    var isStandalone by remember(settings) { mutableStateOf(settings.isStandalone) }
    var serverUrl by remember(settings) { mutableStateOf(settings.serverUrl) }
    var serverToken by remember(settings) { mutableStateOf(settings.serverToken) }
    var costPerKwh by remember(settings) { mutableStateOf(settings.costPerKwh.toString()) }
    var currency by remember(settings) { mutableStateOf(settings.currency) }
    var alertOfflineMin by remember(settings) { mutableStateOf(settings.alertOfflineMin.toString()) }
    var notifyOffline by remember(settings) { mutableStateOf(settings.notifyOffline) }
    var notifySwitch by remember(settings) { mutableStateOf(settings.notifySwitch) }
    var tempAlertC by remember(settings) { mutableStateOf(settings.tempAlertC.toString()) }
    var notifyTemp by remember(settings) { mutableStateOf(settings.notifyTemp) }
    var voltageMin by remember(settings) { mutableStateOf(settings.voltageMin.toString()) }
    var voltageMax by remember(settings) { mutableStateOf(settings.voltageMax.toString()) }
    var notifyVoltage by remember(settings) { mutableStateOf(settings.notifyVoltage) }
    var latitude by remember(settings) { mutableStateOf(settings.latitude.toString()) }
    var longitude by remember(settings) { mutableStateOf(settings.longitude.toString()) }
    var city by remember(settings) { mutableStateOf(settings.city) }
    var country by remember(settings) { mutableStateOf(settings.country) }
    var timezone by remember(settings) { mutableStateOf(settings.timezone) }
    var cityQuery by remember(settings) { mutableStateOf(settings.city) }
    var showCityDropdown by remember { mutableStateOf(false) }
    var showManualCoords by remember { mutableStateOf(false) }

    val whoAmI by viewModel.whoAmI.collectAsState()
    val sunInfo by viewModel.sunInfo.collectAsState()
    val cityResults by viewModel.citySearchResults.collectAsState()
    val shouldFocusToken by viewModel.shouldFocusTokenOnSettings.collectAsState()

    var saveNotice by remember { mutableStateOf(false) }
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()

    // Automatically navigate, scroll to server token input, and pop open software keyboard in ONE single click
    LaunchedEffect(shouldFocusToken) {
        if (shouldFocusToken) {
            isStandalone = false
            delay(120)
            listState.animateScrollToItem(1) // Scroll to Controller Mode / Server URL & Token
            delay(80)
            focusRequester.requestFocus()
            keyboardController?.show()
            viewModel.onTokenFocused()
        }
    }

    var showAdvancedTools by remember { mutableStateOf(false) }

    if (showAdvancedTools) {
        BackHandler { showAdvancedTools = false }
        Column(modifier = modifier.fillMaxSize()) {
            Surface(
                color = VoltaNavy,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showAdvancedTools = false },
                        modifier = Modifier.testTag("back_from_advanced_tools")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Settings",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Advanced Tools",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Raw Microcontroller Terminal & Diagnostic Logs",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
            ToolsScreen(
                viewModel = viewModel,
                modifier = Modifier.weight(1f)
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Theme & Appearance Card
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Appearance & Theme",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isDarkTheme) "Dark Mode (Cyberpunk Industrial)" else "Light Mode (Daylight Crisp)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Theme Icon",
                            tint = if (isDarkTheme) VoltaYellow else VoltaBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isDarkTheme,
                            onClick = { if (!isDarkTheme) viewModel.toggleDarkTheme() },
                            leadingIcon = {
                                Icon(Icons.Default.DarkMode, contentDescription = "Dark", modifier = Modifier.size(16.dp))
                            },
                            label = { Text("Dark Theme") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dark_theme_chip")
                        )
                        FilterChip(
                            selected = !isDarkTheme,
                            onClick = { if (isDarkTheme) viewModel.toggleDarkTheme() },
                            leadingIcon = {
                                Icon(Icons.Default.LightMode, contentDescription = "Light", modifier = Modifier.size(16.dp))
                            },
                            label = { Text("Light Theme") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("light_theme_chip")
                        )
                    }
                }
            }
        }

        // Mode & Server Connection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Controller Operation Mode",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose whether this app operates as an independent local controller or connects to a central Volta web server.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isStandalone,
                            onClick = { isStandalone = true },
                            label = { Text("Local Controller") }
                        )
                        FilterChip(
                            selected = !isStandalone,
                            onClick = { isStandalone = false },
                            label = { Text("Remote Server Gateway") }
                        )
                    }

                    if (!isStandalone) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("Server Base URL") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Primary Google Identity & Profile Section
                        val currentWhoAmI = whoAmI
                        if (currentWhoAmI != null && currentWhoAmI.authorized && serverToken.isNotBlank()) {
                            GoogleUserProfileCard(
                                whoAmI = currentWhoAmI,
                                onSignOut = {
                                    viewModel.signOut()
                                    serverToken = ""
                                }
                            )
                        } else {
                            VoltaLoginCard(
                                viewModel = viewModel,
                                onSuccess = {
                                    viewModel.refreshWhoAmI()
                                    viewModel.refresh()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Advanced direct token editor (collapsible/secondary)
                        var showAdvancedToken by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAdvancedToken = !showAdvancedToken }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Advanced: Direct Bearer Token",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = if (showAdvancedToken) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (showAdvancedToken) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val trimmedToken = serverToken.trim()
                            val isValidStandardPrefix = trimmedToken.startsWith("volta_usr_")
                            val isValidLegacyPrefix = trimmedToken.startsWith("voltra_usr_")
                            OutlinedTextField(
                                value = serverToken,
                                onValueChange = { serverToken = it },
                                label = { Text("Auth Token (Bearer)") },
                                placeholder = { Text("Paste your server token (volta_usr_...)") },
                                supportingText = {
                                    when {
                                        trimmedToken.isBlank() -> Text("Standard prefix: volta_usr_... (legacy voltra_usr_... supported)")
                                        isValidStandardPrefix -> Text("✓ Standard Volta token", color = VoltaGreen, fontWeight = FontWeight.SemiBold)
                                        isValidLegacyPrefix -> Text("✓ Legacy Volta token recognized", color = VoltaGreen, fontWeight = FontWeight.SemiBold)
                                        else -> Text("Token should start with volta_usr_... or voltra_usr_...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .testTag("settings_server_token_input"),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                            )
                        }
                    }
                }
            }
        }

        // Electricity Tariff
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Electricity Tariff & Currency",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Used to estimate daily energy cost from cumulative kWh readings.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = costPerKwh,
                            onValueChange = { costPerKwh = it },
                            label = { Text("Cost per kWh") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = currency,
                            onValueChange = { currency = it },
                            label = { Text("Currency") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Device Event Alerts (Direct Android Notifications)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Device Event Triggers",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Automatic push alerts dispatched directly to this mobile device when critical conditions occur.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    SettingToggleRow(
                        title = "Strip Dropout Alert",
                        subtitle = "Alert when a strip stops reporting for $alertOfflineMin minutes",
                        checked = notifyOffline,
                        onCheckedChange = { notifyOffline = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    SettingToggleRow(
                        title = "Switch Event Alert",
                        subtitle = "Notify when outlets turn on or off",
                        checked = notifySwitch,
                        onCheckedChange = { notifySwitch = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    SettingToggleRow(
                        title = "High Temperature Alert",
                        subtitle = "Alert if strip internal temperature exceeds $tempAlertC°C",
                        checked = notifyTemp,
                        onCheckedChange = { notifyTemp = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    SettingToggleRow(
                        title = "Voltage Guard Alert",
                        subtitle = "Alert if grid voltage falls outside $voltageMin V - $voltageMax V",
                        checked = notifyVoltage,
                        onCheckedChange = { notifyVoltage = it }
                    )
                }
            }
        }

        // System Permissions & Hardware Access
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
                            text = "System Permissions & Hardware Status",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Permissions",
                            tint = if (hasNotifPermission && hasLocationPermission && hasWifiPermission) VoltaGreen else VoltaYellow
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Review operating system permissions granted to Volta for local device discovery, background monitoring, and instant push notifications.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Notification Permission
                    PermissionStatusRow(
                        title = "Push Notifications",
                        subtitle = "Real-time alerts for offline devices, high temperature, and voltage safety.",
                        isGranted = hasNotifPermission,
                        icon = Icons.Default.NotificationsActive
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 2. Location Permission
                    PermissionStatusRow(
                        title = "Precise Location (GPS)",
                        subtitle = "Required by Android OS for Wi-Fi AP scanning and sunrise/sunset solar calculation.",
                        isGranted = hasLocationPermission,
                        icon = Icons.Default.LocationOn
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 3. Nearby Wi-Fi Devices Permission
                    PermissionStatusRow(
                        title = "Nearby Wi-Fi Devices",
                        subtitle = "Direct Wi-Fi scanning to detect, pair, and configure MTTL power strips.",
                        isGranted = hasWifiPermission,
                        icon = Icons.Default.Wifi
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val allPermissionsGranted = hasNotifPermission && hasLocationPermission && hasWifiPermission

                    if (!allPermissionsGranted) {
                        Button(
                            onClick = { requestAllPermissions() },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = VoltaBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_missing_permissions_button")
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Grant Missing Permissions", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedButton(
                        onClick = { viewModel.sendTestNotification() },
                        enabled = hasNotifPermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("send_test_notification_button")
                    ) {
                        Text("Send Test Notification to Phone")
                    }
                }
            }
        }

        // Location, City Search Autocomplete & Solar Times
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
                            text = "Location & Solar Dawn/Dusk",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = "Solar",
                            tint = VoltaAmber
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Search city to automatically set location, local timezone, and live sunrise/sunset times for solar schedules.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // City Autocomplete Search Input
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = cityQuery,
                            onValueChange = {
                                cityQuery = it
                                showCityDropdown = true
                                if (it.trim().length >= 2) {
                                    viewModel.searchCities(it.trim())
                                }
                            },
                            label = { Text("Search City (e.g. Cairo, Dubai, London)") },
                            placeholder = { Text("Type city name...") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = VoltaBlue)
                            },
                            trailingIcon = {
                                if (cityQuery.isNotBlank()) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                cityQuery = ""
                                                showCityDropdown = false
                                            }
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("city_search_input")
                        )

                        DropdownMenu(
                            expanded = showCityDropdown && cityResults.isNotEmpty(),
                            onDismissRequest = { showCityDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            cityResults.take(6).forEach { res ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = "${res.name}, ${res.country}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "TZ: ${res.timezone} • (${String.format(Locale.US, "%.2f", res.latitude)}, ${String.format(Locale.US, "%.2f", res.longitude)})",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.LocationCity, contentDescription = null, tint = VoltaBlue)
                                    },
                                    onClick = {
                                        cityQuery = res.name
                                        city = res.name
                                        country = res.country
                                        timezone = res.timezone
                                        latitude = res.latitude.toString()
                                        longitude = res.longitude.toString()
                                        showCityDropdown = false
                                        viewModel.selectCity(res)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Solar & Local Clock preview
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = VoltaBlue, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "$city, $country",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                TagBadge(text = timezone, color = VoltaBlue)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Sunrise", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "🌅 ${sunInfo?.sunrise ?: "06:11"}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VoltaAmber
                                    )
                                }
                                Column {
                                    Text("Sunset", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "🌇 ${sunInfo?.sunset ?: "18:04"}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF97316)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Local Time", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "🕒 ${sunInfo?.localTime?.take(5) ?: "--:--"}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Advanced Manual Coordinates Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showManualCoords = !showManualCoords }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Advanced: Exact Coordinates",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = if (showManualCoords) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (showManualCoords) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = latitude,
                                onValueChange = { latitude = it },
                                label = { Text("Latitude") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = longitude,
                                onValueChange = { longitude = it },
                                label = { Text("Longitude") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Subscription & Plan Status
        if (!isStandalone) {
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = VoltaBlue)
                                Text(
                                    text = "Subscription Plan & Limits",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TagBadge(
                                text = whoAmI?.status?.uppercase() ?: "ACTIVE",
                                color = if (whoAmI?.subscriptionExpired == true) VoltaRed else VoltaGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val used = whoAmI?.strips?.size ?: 0
                        val max = whoAmI?.maxStrips ?: 10
                        Text(
                            text = "$used of $max strips used in account",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val currentExpires = whoAmI?.expiresAt
                        val expiryText = if (currentExpires != null) {
                            val days = ((currentExpires - System.currentTimeMillis() / 1000) / 86400).toInt()
                            if (days > 0) "Expires in $days days" else "Expired"
                        } else {
                            "Lifetime / Free Tier (1 free lifetime strip included)"
                        }
                        Text(
                            text = "Account Plan: $expiryText",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (whoAmI?.isAdmin == true) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = VoltaAmber.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, VoltaAmber.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = VoltaAmber, modifier = Modifier.size(18.dp))
                                    Column {
                                        Text("Master Administrator Privileges Active", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = VoltaAmber)
                                        Text("Full cross-tenant access, unlimited strip allocations, and device transfers enabled.", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Amazon Alexa Voice Control & Directive Parity Card
            item {
                AlexaIntegrationCard(
                    serverUrl = serverUrl,
                    serverToken = serverToken
                )
            }
        }

        // Save Button
        item {
            Button(
                onClick = {
                    val safeUrl = if (serverUrl.isBlank() || serverUrl.contains("192.168.1.100") || serverUrl.contains("132.145")) {
                        "https://volta.03092017.xyz"
                    } else {
                        serverUrl.trim()
                    }
                    val updated = VoltaSettings(
                        serverUrl = safeUrl,
                        serverToken = serverToken.trim(),
                        isStandalone = isStandalone,
                        costPerKwh = costPerKwh.toDoubleOrNull() ?: 2.18,
                        currency = currency.ifBlank { "EGP" },
                        ntfyTopic = "",
                        alertOfflineMin = alertOfflineMin.toIntOrNull() ?: 10,
                        notifyOffline = notifyOffline,
                        notifySwitch = notifySwitch,
                        tempAlertC = tempAlertC.toIntOrNull() ?: 60,
                        notifyTemp = notifyTemp,
                        voltageMin = voltageMin.toDoubleOrNull() ?: 200.0,
                        voltageMax = voltageMax.toDoubleOrNull() ?: 250.0,
                        notifyVoltage = notifyVoltage,
                        latitude = latitude.toDoubleOrNull() ?: 30.0444,
                        longitude = longitude.toDoubleOrNull() ?: 31.2357,
                        city = city,
                        country = country,
                        timezone = timezone
                    )
                    viewModel.updateSettings(updated)
                    saveNotice = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_settings_button")
            ) {
                Text("Save Configuration")
            }

            if (saveNotice) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✓ Settings successfully saved to local store.",
                    color = VoltaGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Advanced Tools & Raw Terminal Button at the end of Settings
        item {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { showAdvancedTools = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("advanced_settings_button"),
                border = BorderStroke(1.dp, VoltaAmber.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = VoltaAmber
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Advanced Diagnostics & Raw Terminal",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = VoltaAmber
                )
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = if (isGranted) VoltaGreen.copy(alpha = 0.15f) else VoltaRed.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) VoltaGreen else VoltaRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        TagBadge(
            text = if (isGranted) "Granted" else "Missing",
            color = if (isGranted) VoltaGreen else VoltaRed
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VoltaGreen)
        )
    }
}
