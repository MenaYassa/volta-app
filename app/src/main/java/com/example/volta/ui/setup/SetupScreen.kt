package com.example.volta.ui.setup

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.volta.network.DiscoveredStripAp
import com.example.volta.network.WifiProvisioningHelper
import com.example.volta.theme.VoltaAmber
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaCyan
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaNavy
import com.example.volta.theme.VoltaRed
import com.example.volta.ui.VoltaTab
import com.example.volta.ui.components.TagBadge
import com.example.volta.ui.components.WebGoogleSignInDialog
import com.example.volta.viewmodel.VoltaViewModel

@Composable
fun SetupScreen(
    viewModel: VoltaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()

    val discoveredDialInIp by viewModel.discoveredDialInIp.collectAsState()
    val discoveredStrips by viewModel.discoveredStrips.collectAsState()
    val isScanningStrips by viewModel.isScanningStrips.collectAsState()
    val isConnectingWifi by viewModel.isConnectingWifi.collectAsState()
    val connectedStripAp by viewModel.connectedStripAp.collectAsState()
    val wifiConnectionStatus by viewModel.wifiConnectionStatus.collectAsState()

    val isProvisioning by viewModel.isProvisioning.collectAsState()
    val provisioningStatus by viewModel.provisioningStatus.collectAsState()
    val whoAmI by viewModel.whoAmI.collectAsState()

    var showLimitReachedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(provisioningStatus) {
        val status = provisioningStatus?.lowercase() ?: ""
        if (status.contains("limit reached") || status.contains("strip limit")) {
            showLimitReachedDialog = true
        }
    }

    if (showLimitReachedDialog) {
        val maxStrips = whoAmI?.maxStrips ?: 5
        AlertDialog(
            onDismissRequest = { showLimitReachedDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = VoltaAmber) },
            title = { Text("Strip Limit Reached", fontWeight = FontWeight.Bold) },
            text = {
                Text("You have reached your limit of $maxStrips strips. Please contact your administrator to upgrade your subscription plan.")
            },
            confirmButton = {
                Button(onClick = { showLimitReachedDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasNotifPermission = granted
            if (granted) viewModel.sendTestNotification()
        }
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasLocationPermission = permissions.values.any { it }
            if (hasLocationPermission) {
                viewModel.scanForStrips()
            }
        }
    )

    val savedHomeWifiSsid by viewModel.savedHomeWifiSsid.collectAsState()
    val savedHomeWifiPass by viewModel.savedHomeWifiPass.collectAsState()

    // Streamlined Form fields: Automatically populated from persistent local cache
    var targetSsid by remember { mutableStateOf(savedHomeWifiSsid.ifBlank { "" }) }
    var targetPass by remember { mutableStateOf(savedHomeWifiPass) }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(savedHomeWifiSsid, savedHomeWifiPass) {
        if (savedHomeWifiSsid.isNotBlank() && targetSsid.isBlank()) {
            targetSsid = savedHomeWifiSsid
        }
        if (savedHomeWifiPass.isNotBlank() && targetPass.isBlank()) {
            targetPass = savedHomeWifiPass
        }
    }

    // Direct connect input field for manual Suffix or SSID
    var directInput by remember { mutableStateOf("") }

    var showGoogleSignInDialog by remember { mutableStateOf(false) }
    var showTokenMasked by remember { mutableStateOf(true) }
    var manualClaimMac by remember { mutableStateOf("") }
    val isClaiming by viewModel.isClaiming.collectAsState()
    val claimStatus by viewModel.claimStatus.collectAsState()

    if (showGoogleSignInDialog) {
        WebGoogleSignInDialog(
            viewModel = viewModel,
            serverUrl = settings.serverUrl,
            onTokenReceived = {
                showGoogleSignInDialog = false
            },
            onDismiss = { showGoogleSignInDialog = false }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Google Authentication & Account Association Card
        item {
            val userToken = settings.serverToken
            val isAuthenticated = userToken.isNotBlank()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_account_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAuthenticated) MaterialTheme.colorScheme.surface else VoltaAmber.copy(alpha = 0.10f)
                ),
                border = BorderStroke(1.dp, if (isAuthenticated) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else VoltaAmber.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Account",
                                tint = if (isAuthenticated) VoltaBlue else VoltaAmber,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isAuthenticated) {
                                        whoAmI?.name?.ifBlank { whoAmI?.email } ?: "Google Account Linked"
                                    } else {
                                        "Google Sign-In Required"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isAuthenticated) {
                                        whoAmI?.email?.ifBlank { "Strips automatically bind to this account" } ?: "Strips automatically bind to this account"
                                    } else {
                                        "Sign in to link and claim strips to your dashboard"
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!isAuthenticated) {
                            Button(
                                onClick = { showGoogleSignInDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = VoltaAmber),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp).testTag("setup_signin_button")
                            ) {
                                Text("Sign In", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            TagBadge(
                                text = "Authenticated",
                                color = VoltaGreen,
                                bgColor = VoltaGreen.copy(alpha = 0.12f)
                            )
                        }
                    }

                    if (isAuthenticated) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Key, contentDescription = "Token", modifier = Modifier.size(15.dp), tint = VoltaAmber)
                                Spacer(modifier = Modifier.width(6.dp))
                                val displayToken = if (showTokenMasked) {
                                    if (userToken.length > 12) "${userToken.take(6)}••••••••${userToken.takeLast(4)}" else "••••••••"
                                } else {
                                    userToken
                                }
                                Text(
                                    text = "Token: $displayToken",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { showTokenMasked = !showTokenMasked },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showTokenMasked) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle token visibility",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Volta Account Token", userToken))
                                        Toast.makeText(context, "Google account token copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(30.dp).testTag("copy_token_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Token",
                                        modifier = Modifier.size(16.dp),
                                        tint = VoltaBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dynamic Server Discovery Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Provisioning Gateway",
                                tint = VoltaAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cloud Provisioning Gateway: Ready",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Dynamic backend discovery active. Hardware connects securely without manual IP setup.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TagBadge(
                        text = "Online",
                        color = VoltaGreen,
                        bgColor = VoltaGreen.copy(alpha = 0.12f)
                    )
                }
            }
        }

        // Step 1: Connect to Strip SoftAP
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
                        Column {
                            Text(
                                text = "1. Connect to Strip SoftAP",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Join the strip's TONLY_TAP_<suffix> or U+TAP_<suffix> hotspot",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.WifiFind,
                            contentDescription = "Search Strip",
                            tint = VoltaBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Hold the strip physical power button for 5 seconds until the LED blinks rapidly to enter AP mode.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!hasLocationPermission) {
                                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES)
                                    } else {
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                    }
                                    locationPermissionLauncher.launch(perms)
                                } else {
                                    viewModel.scanForStrips()
                                }
                            },
                            enabled = !isScanningStrips,
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("search_strip_button")
                        ) {
                            if (isScanningStrips) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scanning…")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Search for Strip")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_wifi_settings_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Wi-Fi Settings", fontSize = 12.sp)
                        }
                    }

                    // Scan Status Text with Action Buttons
                    wifiConnectionStatus?.let { status ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = status,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (status.startsWith("✓")) VoltaGreen else if (status.startsWith("No") || status.startsWith("Connection note") || status.contains("Location") || status.contains("throttled")) VoltaAmber else MaterialTheme.colorScheme.onSurface
                                )

                                if (status.contains("Location (GPS)")) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = {
                                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                        },
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Open Location Settings (Turn on GPS)", fontSize = 11.sp)
                                    }
                                } else if (status.contains("Wi-Fi is turned off")) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = {
                                            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                                        },
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Open Wi-Fi Settings", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Discovered Strip Hotspots List
                    if (discoveredStrips.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Detected Strip Hotspots (${discoveredStrips.size}):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            discoveredStrips.forEach { ap ->
                                val isConnected = connectedStripAp?.ssid == ap.ssid
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isConnected) VoltaGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = if (isConnected) CardDefaults.outlinedCardBorder() else null
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Wifi,
                                                    contentDescription = "Signal",
                                                    tint = if (isConnected) VoltaGreen else VoltaBlue,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = ap.ssid,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Auto-Password: ${ap.password}",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = VoltaAmber
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("Strip Password", ap.password))
                                                    Toast.makeText(context, "Password '${ap.password}' copied!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copy Password",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            Button(
                                                onClick = { viewModel.connectToStrip(ap) },
                                                enabled = !isConnectingWifi,
                                                colors = if (isConnected) ButtonDefaults.buttonColors(containerColor = VoltaGreen) else ButtonDefaults.buttonColors(),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                if (isConnected) {
                                                    Icon(Icons.Default.Check, contentDescription = "Connected", modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Connected", fontSize = 11.sp)
                                                } else {
                                                    Text("Connect", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Direct Connect by Suffix or SSID (Reliable, instantaneous method)
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Or Direct Connect by Suffix / SSID",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Type the 6-7 character suffix or full SSID to connect directly:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val effectiveSsid = remember(directInput) {
                        val clean = directInput.trim()
                        when {
                            clean.startsWith("TONLY_TAP_", ignoreCase = true) -> clean
                            clean.startsWith("U+TAP_", ignoreCase = true) -> clean
                            clean.isNotBlank() -> "TONLY_TAP_$clean"
                            else -> "TONLY_TAP_"
                        }
                    }
                    val derivedPass = remember(effectiveSsid) {
                        WifiProvisioningHelper.deriveStripPassword(effectiveSsid)
                    }

                    OutlinedTextField(
                        value = directInput,
                        onValueChange = { directInput = it },
                        label = { Text("SSID or Suffix") },
                        placeholder = { Text("e.g. 132171 or TONLY_TAP_132171") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("direct_ssid_input")
                    )

                    if (directInput.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Target AP: $effectiveSsid • Auto-Password: $derivedPass",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = VoltaAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val suffix = WifiProvisioningHelper.deriveMacSuffix(effectiveSsid)
                                val ap = DiscoveredStripAp(
                                    ssid = effectiveSsid,
                                    password = derivedPass,
                                    macSuffix = suffix
                                )
                                viewModel.connectToStrip(ap)
                            },
                            enabled = !isConnectingWifi && directInput.isNotBlank(),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("direct_connect_button")
                        ) {
                            Text("Connect to Strip AP", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Strip Password", derivedPass))
                                Toast.makeText(context, "Password '$derivedPass' copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            enabled = directInput.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Pass", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Step 2: Streamlined Home Wi-Fi & Execute Handshake (ONLY 2 INPUTS!)
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
                        Column {
                            Text(
                                text = "2. Home Wi-Fi Credentials",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Saved in local cache for fast multi-strip pairing.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.WifiTethering,
                            contentDescription = "Handshake",
                            tint = VoltaGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Input 1: Home Wi-Fi SSID
                    OutlinedTextField(
                        value = targetSsid,
                        onValueChange = {
                            targetSsid = it
                            viewModel.saveHomeWifiCredentials(it, targetPass)
                        },
                        label = { Text("Home Wi-Fi Network (SSID)") },
                        placeholder = { Text("e.g. MyHomeNetwork_2.4G") },
                        supportingText = {
                            Text("Must be a 2.4 GHz Wi-Fi network (strip will fail on 5 GHz-only SSIDs)", fontSize = 11.sp, color = VoltaAmber)
                        },
                        leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = VoltaBlue) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_wifi_ssid_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Input 2: Home Wi-Fi Password
                    OutlinedTextField(
                        value = targetPass,
                        onValueChange = {
                            targetPass = it
                            viewModel.saveHomeWifiCredentials(targetSsid, it)
                        },
                        label = { Text("Home Wi-Fi Password") },
                        placeholder = { Text("Wi-Fi network password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_wifi_pass_input")
                    )

                    if (savedHomeWifiSsid.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = VoltaGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Cached Wi-Fi credentials loaded",
                                    fontSize = 11.sp,
                                    color = VoltaGreen
                                )
                            }
                            Text(
                                text = "Clear Cache",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.clearHomeWifiCredentials()
                                        targetSsid = ""
                                        targetPass = ""
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Automated Details Pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (connectedStripAp != null) {
                                Text(
                                    text = "✓ Strip Connected: ${connectedStripAp?.ssid} (at 192.168.1.1:30300)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VoltaGreen
                                )
                            } else {
                                Text(
                                    text = "• Strip AP Address: 192.168.1.1:30300 (Automated)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "• Direct Account Claim: Bound to your Google account",
                                fontSize = 11.sp,
                                color = VoltaGreen
                            )
                        }
                    }

                    if (connectedStripAp == null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VoltaAmber.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, VoltaAmber.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Hotspot Reminder",
                                    tint = VoltaAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Ensure your phone's Wi-Fi is connected to the strip's hotspot (e.g. TONLY_TAP_xxxxxx) in Step 1 or Wi-Fi settings before tapping execute.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            val candidate = connectedStripAp?.macSuffix
                                ?: directInput.ifBlank { discoveredStrips.firstOrNull()?.macSuffix ?: "" }
                            viewModel.saveHomeWifiCredentials(targetSsid, targetPass)
                            viewModel.runProvisioning(
                                ssid = targetSsid,
                                pass = targetPass,
                                candidateMac = candidate
                            )
                        },
                        enabled = !isProvisioning && targetSsid.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("execute_pairing_handshake_button")
                    ) {
                        if (isProvisioning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Executing Handshake & Auto-Claim…")
                        } else {
                            Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Execute Pairing Handshake")
                        }
                    }

                    // Handshake Result Status
                    val currentStatus = provisioningStatus
                    if (currentStatus != null) {
                        val isPlanLimit = currentStatus.contains("PLAN_LIMIT_REACHED", ignoreCase = true) ||
                                          currentStatus.contains("Plan limit", ignoreCase = true)
                        Spacer(modifier = Modifier.height(12.dp))
                        if (isPlanLimit) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("plan_limit_reached_card"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = VoltaAmber.copy(alpha = 0.12f)),
                                border = BorderStroke(1.dp, VoltaAmber)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = VoltaAmber, modifier = Modifier.size(20.dp))
                                        Text("Subscription Plan Limit Reached", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = currentStatus.removePrefix("PLAN_LIMIT_REACHED:").trim(),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { viewModel.setSelectedTab(VoltaTab.SETTINGS.ordinal) },
                                        colors = ButtonDefaults.buttonColors(containerColor = VoltaAmber),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("View Subscription Plan Details", fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = currentStatus,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (currentStatus.startsWith("Error")) MaterialTheme.colorScheme.error else VoltaGreen
                                )
                            }
                        }
                    }
                }
            }
        }

        // Method B: Manual Claim Strip to Account
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_claim_strip_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = VoltaCyan.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Link Strip",
                                    tint = VoltaCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Claim Strip to Account",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Link an existing or pre-configured strip via MAC address",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "If your strip has already joined Wi-Fi (or you configured it with another device/CLI), enter its 12-digit MAC address to claim ownership under your Volta account:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val cleanMac = manualClaimMac.trim().uppercase().replace(":", "")
                    OutlinedTextField(
                        value = manualClaimMac,
                        onValueChange = { manualClaimMac = it },
                        label = { Text("Strip MAC Address") },
                        placeholder = { Text("e.g. 88D039132171") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                    if (text.isNotBlank()) {
                                        manualClaimMac = text.trim()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste MAC", modifier = Modifier.size(18.dp))
                            }
                        },
                        supportingText = {
                            if (cleanMac.isNotBlank()) {
                                if (cleanMac.length == 12) {
                                    Text("✓ Valid 12-digit MAC format", color = VoltaGreen, fontSize = 11.sp)
                                } else if (cleanMac.length == 6 || cleanMac.length == 7) {
                                    Text("Suffix detected: Will resolve to 88D039$cleanMac", color = VoltaCyan, fontSize = 11.sp)
                                } else {
                                    Text("${cleanMac.length}/12 characters entered", fontSize = 11.sp)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_claim_mac_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val resolvedMac = when (cleanMac.length) {
                                12 -> cleanMac
                                6 -> "88D039$cleanMac"
                                7 -> if (cleanMac.startsWith("9")) "88D03$cleanMac" else "88D039${cleanMac.drop(1)}"
                                else -> cleanMac
                            }
                            viewModel.claimExistingStrip(
                                mac = resolvedMac,
                                userToken = settings.serverToken,
                                serverUrl = settings.serverUrl
                            )
                        },
                        enabled = !isClaiming && cleanMac.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_claim_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = VoltaCyan, contentColor = Color.Black)
                    ) {
                        if (isClaiming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Claiming Strip...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Claim Strip to Account", fontWeight = FontWeight.Bold)
                        }
                    }

                    claimStatus?.let { status ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = status,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (status.startsWith("Error")) MaterialTheme.colorScheme.error else VoltaGreen
                            )
                        }
                    }
                }
            }
        }
    }
}
