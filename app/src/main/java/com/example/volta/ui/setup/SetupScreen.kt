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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material.icons.filled.WifiTethering
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
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaNavy
import com.example.volta.theme.VoltaRed
import com.example.volta.ui.components.TagBadge
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

    // Streamlined Form fields: ONLY Home Wi-Fi SSID and Password!
    var targetSsid by remember { mutableStateOf("MyHomeNetwork") }
    var targetPass by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // Direct connect input field for manual Suffix or SSID
    var directInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                                text = "Only SSID and password needed. Auto-claim will assign the strip.",
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
                        onValueChange = { targetSsid = it },
                        label = { Text("Home Wi-Fi Network (SSID)") },
                        placeholder = { Text("e.g. MyHomeNetwork_2.4G") },
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
                        onValueChange = { targetPass = it },
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

                    Spacer(modifier = Modifier.height(14.dp))

                    // Automated Details Pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "• Strip AP Address: 192.168.1.1:30300 (Automated)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "• Backend Controller: Connected (dynamic zero-config dial-in)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "• Zero-Touch Auto-Claim: Strip automatically binds to your account",
                                fontSize = 11.sp,
                                color = VoltaGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.runProvisioning(
                                ssid = targetSsid,
                                pass = targetPass
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
                        Spacer(modifier = Modifier.height(12.dp))
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
}
