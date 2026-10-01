package com.example.volta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.model.VoltaSettings
import com.example.volta.theme.VoltaGreen
import com.example.volta.viewmodel.VoltaViewModel

@Composable
fun SettingsScreen(
    viewModel: VoltaViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()

    var isStandalone by remember(settings) { mutableStateOf(settings.isStandalone) }
    var serverUrl by remember(settings) { mutableStateOf(settings.serverUrl) }
    var serverToken by remember(settings) { mutableStateOf(settings.serverToken) }
    var costPerKwh by remember(settings) { mutableStateOf(settings.costPerKwh.toString()) }
    var currency by remember(settings) { mutableStateOf(settings.currency) }
    var ntfyTopic by remember(settings) { mutableStateOf(settings.ntfyTopic) }
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

    var saveNotice by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = serverToken,
                            onValueChange = { serverToken = it },
                            label = { Text("Auth Token (Bearer)") },
                            modifier = Modifier.fillMaxWidth()
                        )
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

        // Notifications & Alerts
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Push Alerts (ntfy.sh)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Receive notifications on strip dropouts, high temperature, or voltage spikes.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = ntfyTopic,
                        onValueChange = { ntfyTopic = it },
                        label = { Text("ntfy Topic Name (e.g. my-volta-alerts)") },
                        modifier = Modifier.fillMaxWidth()
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

        // Location & Solar Times
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Location (Solar Calculations)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Used for Sunrise and Sunset schedule triggers.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

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

        // Save Button
        item {
            Button(
                onClick = {
                    val updated = VoltaSettings(
                        serverUrl = serverUrl,
                        serverToken = serverToken,
                        isStandalone = isStandalone,
                        costPerKwh = costPerKwh.toDoubleOrNull() ?: 1.2,
                        currency = currency.ifBlank { "EGP" },
                        ntfyTopic = ntfyTopic,
                        alertOfflineMin = alertOfflineMin.toIntOrNull() ?: 10,
                        notifyOffline = notifyOffline,
                        notifySwitch = notifySwitch,
                        tempAlertC = tempAlertC.toIntOrNull() ?: 60,
                        notifyTemp = notifyTemp,
                        voltageMin = voltageMin.toDoubleOrNull() ?: 200.0,
                        voltageMax = voltageMax.toDoubleOrNull() ?: 250.0,
                        notifyVoltage = notifyVoltage,
                        latitude = latitude.toDoubleOrNull() ?: 30.0444,
                        longitude = longitude.toDoubleOrNull() ?: 31.2357
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
