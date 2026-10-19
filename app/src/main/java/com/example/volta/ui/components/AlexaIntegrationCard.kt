package com.example.volta.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaYellow

private val AlexaCyan = Color(0xFF00CAFF)
private val AlexaDark = Color(0xFF0F172A)

@Composable
fun AlexaIntegrationCard(
    serverUrl: String,
    serverToken: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showCommandsHelp by remember { mutableStateOf(false) }
    var showDirectivesInfo by remember { mutableStateOf(false) }

    val cleanBaseUrl = serverUrl.trim().trimEnd('/')
    val hasToken = serverToken.trim().isNotBlank()
    val alexaLinkUrl = if (cleanBaseUrl.isNotBlank() && hasToken) {
        "$cleanBaseUrl/api/alexa/link?token=${Uri.encode(serverToken.trim())}"
    } else {
        "https://volta.03092017.xyz/api/alexa/link"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, AlexaCyan.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Alexa Icon, Title, Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .background(AlexaCyan.copy(alpha = 0.16f), CircleShape)
                            .border(1.5.dp, AlexaCyan, CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_alexa),
                            contentDescription = "Amazon Alexa",
                            tint = AlexaCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Amazon Alexa Smart Home",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Voice Control & Directive Parity",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TagBadge(
                    text = if (hasToken) "READY TO LINK" else "TOKEN REQUIRED",
                    color = if (hasToken) VoltaGreen else Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Connect Volta to Amazon Alexa for native voice commands across all 4 outlets per strip, including real-time temperature, voltage, and watt telemetry.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Account Linking Button
            Button(
                onClick = {
                    if (!hasToken) {
                        Toast.makeText(context, "Please sign in or configure your Volta token first.", Toast.LENGTH_SHORT).show()
                    } else {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, alexaLinkUrl.toUri())
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlexaCyan,
                    contentColor = AlexaDark
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("connect_amazon_alexa_button")
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_alexa),
                    contentDescription = null,
                    tint = AlexaDark,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Connect to Amazon Alexa",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = AlexaDark,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Linking Helper Steps
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "3-Step Setup Guide:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("1. Tap 'Connect to Amazon Alexa' to launch skill authorization.", fontSize = 11.sp)
                    Text("2. Approve OAuth linking with your Volta credentials.", fontSize = 11.sp)
                    Text("3. In Alexa, discover devices — all 4 outlets per strip are added automatically.", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Expandable: Voice Directives & Parity
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDirectivesInfo = !showDirectivesInfo }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Sensors, contentDescription = null, tint = AlexaCyan, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Supported Alexa Directives (Live Backend)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (showDirectivesInfo) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (showDirectivesInfo) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DirectiveRow("Alexa.PowerController", "Switch outlets ON/OFF individually or all at once")
                    DirectiveRow("Alexa.TemperatureSensor", "Query real-time internal thermistor temperature in °C")
                    DirectiveRow("Alexa.RangeController (Voltage)", "Query live AC grid voltage in Volts")
                    DirectiveRow("Alexa.RangeController (Power)", "Query live active wattage consumption in Watts")
                    DirectiveRow("Alexa.EndpointHealth", "Real-time strip connectivity and dial-out socket status")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Expandable: Sample Voice Commands
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCommandsHelp = !showCommandsHelp }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = VoltaYellow, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Sample Voice Commands",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (showCommandsHelp) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (showCommandsHelp) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    VoiceCommandChip("🗣️ \"Alexa, turn on Living Room TV\"")
                    VoiceCommandChip("🗣️ \"Alexa, turn off Soundbar\"")
                    VoiceCommandChip("🗣️ \"Alexa, what is the temperature of Bedroom TV?\"")
                    VoiceCommandChip("🗣️ \"Alexa, what is the voltage on Living Room Strip?\"")
                    VoiceCommandChip("🗣️ \"Alexa, how many watts is TV consuming?\"")
                }
            }
        }
    }
}

@Composable
private fun DirectiveRow(title: String, desc: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = AlexaCyan
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VoiceCommandChip(cmd: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = cmd,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
