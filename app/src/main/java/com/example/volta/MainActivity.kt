package com.example.volta

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaTheme
import com.example.volta.ui.MainScreen
import com.example.volta.viewmodel.VoltaViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: VoltaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            var showPermissionDialog by remember { mutableStateOf(false) }

            // Runtime Permissions Request Launcher
            val multiplePermissionsLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
                onResult = { results ->
                    if (results[Manifest.permission.POST_NOTIFICATIONS] == true) {
                        viewModel.sendTestNotification()
                    }
                }
            )

            LaunchedEffect(Unit) {
                val missingPermissions = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                        missingPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    }
                }
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }

                if (missingPermissions.isNotEmpty()) {
                    showPermissionDialog = true
                }
            }

            // Handle HTTP 403 Forbidden events gracefully with user-facing Toast without logging out
            LaunchedEffect(Unit) {
                viewModel.adminForbiddenEvent.collect { msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                }
            }

            VoltaTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(viewModel = viewModel)
                }

                if (showPermissionDialog) {
                    AlertDialog(
                        onDismissRequest = { showPermissionDialog = false },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Permissions",
                                tint = VoltaBlue
                            )
                        },
                        title = {
                            Text(text = "App Permissions Required", fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Column {
                                Text(
                                    text = "To discover, control, and protect your MTTL power strips, Volta requests the following system permissions:",
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "• 🔔 Notifications: Instant mobile push alerts for power dropouts, high temp, and voltage surges.",
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "• 📶 Nearby Devices: Wi-Fi scanning to discover, connect, and pair new MTTL power strips.",
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "• 📍 Location (GPS): Required by Android for Wi-Fi access point SSID detection and solar dawn/dusk schedules.",
                                    fontSize = 12.sp
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showPermissionDialog = false
                                    val perms = mutableListOf<String>()
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                        perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                                    }
                                    perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
                                    perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    multiplePermissionsLauncher.launch(perms.toTypedArray())
                                },
                                modifier = Modifier.testTag("grant_initial_permissions_button")
                            ) {
                                Text("Allow Permissions")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showPermissionDialog = false },
                                modifier = Modifier.testTag("dismiss_permissions_button")
                            ) {
                                Text("Later")
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setAppForegrounded(true)
    }

    override fun onStop() {
        super.onStop()
        viewModel.setAppForegrounded(false)
    }
}
