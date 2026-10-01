package com.example.volta.ui.setup

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaYellow
import com.example.volta.viewmodel.VoltaViewModel

@Composable
fun SetupScreen(
    viewModel: VoltaViewModel,
    modifier: Modifier = Modifier
) {
    val isProvisioning by viewModel.isProvisioning.collectAsState()
    val provisioningStatus by viewModel.provisioningStatus.collectAsState()

    var apIp by remember { mutableStateOf("192.168.1.1") }
    var apPortStr by remember { mutableStateOf("30300") }
    var controllerIp by remember { mutableStateOf("192.168.1.100") }
    var targetSsid by remember { mutableStateOf("MyHomeNetwork") }
    var targetPass by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Steps Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "MTTL-W01 Pairing Wizard",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Korean MTTL-W01 smart power strips dial out to your Volta controller over TCP port 10086. Use this tool to flash Wi-Fi credentials to new strips.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SetupStepRow(number = 1, title = "Activate AP Mode", desc = "Hold physical power button on the strip for 5 seconds until LED blinks.")
                    SetupStepRow(number = 2, title = "Connect Phone Wi-Fi", desc = "On your phone/tablet, join the 'MTTL-W01-xxxx' Wi-Fi network.")
                    SetupStepRow(number = 3, title = "Send Configuration", desc = "Enter your home Wi-Fi and Volta controller server IP below and execute.")
                }
            }
        }

        // Configuration Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Strip Handshake Credentials",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = apIp,
                            onValueChange = { apIp = it },
                            label = { Text("Strip AP IP") },
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = apPortStr,
                            onValueChange = { apPortStr = it },
                            label = { Text("AP Port") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = controllerIp,
                        onValueChange = { controllerIp = it },
                        label = { Text("Volta Server Dial-In IP (Port 10086)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = targetSsid,
                        onValueChange = { targetSsid = it },
                        label = { Text("Home Wi-Fi Network (SSID)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = targetPass,
                        onValueChange = { targetPass = it },
                        label = { Text("Home Wi-Fi Password") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val port = apPortStr.toIntOrNull() ?: 30300
                            viewModel.runProvisioning(apIp, port, controllerIp, targetSsid, targetPass)
                        },
                        enabled = !isProvisioning && targetSsid.isNotBlank() && controllerIp.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("send_provisioning_button")
                    ) {
                        if (isProvisioning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Communicating with strip…")
                        } else {
                            Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Execute Pairing Handshake")
                        }
                    }

                    // Result Status
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

        // Protocol reference card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Technical Reference",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• MTTL-W01 runs Realtek RTL8711AF SoC with 4 relays + 2 USB ports.\n" +
                               "• AP socket operates at 192.168.1.1:30300.\n" +
                               "• Command 1: 'up:ip:<controller_ip>\\r\\n' -> 'up:ip:ip_ok'\n" +
                               "• Command 2: 'up:connect:<ssid>:<pass>\\r\\n' -> 'up:connect:connect_ok'\n" +
                               "• Once paired, strip initiates TCP connection to controller port 10086.",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupStepRow(number: Int, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(VoltaBlue, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.White
            )
        }
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
