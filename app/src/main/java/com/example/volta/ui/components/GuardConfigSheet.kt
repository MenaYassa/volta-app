package com.example.volta.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.model.PowerStrip
import com.example.volta.model.VoltageGuard
import com.example.volta.theme.VoltaAmber
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardConfigSheet(
    strip: PowerStrip,
    onDismiss: () -> Unit,
    onSave: (enabled: Boolean, minV: Double, maxV: Double, safeDelayMin: Int, tempEnabled: Boolean, maxTempC: Double) -> Unit,
    onReset: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentGuard = strip.voltageGuard ?: VoltageGuard()

    var enabled by remember(currentGuard) { mutableStateOf(currentGuard.enabled) }
    var minV by remember(currentGuard) { mutableStateOf(currentGuard.minV.toString()) }
    var maxV by remember(currentGuard) { mutableStateOf(currentGuard.maxV.toString()) }
    var safeDelayMin by remember(currentGuard) { mutableStateOf(currentGuard.safeDelayMin.toString()) }

    var tempEnabled by remember(currentGuard) { mutableStateOf(currentGuard.tempEnabled) }
    var maxTempC by remember(currentGuard) { mutableFloatStateOf(currentGuard.maxTempC.toFloat()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Safety Guard",
                        tint = VoltaGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    val maxOutletTemp = strip.outlets.maxOfOrNull { it.tempC } ?: 25
                    Column {
                        Text(
                            text = "Hardware Safety Guard",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${strip.displayName} • ${strip.voltageV}V • ${maxOutletTemp}°C",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tripped Alerts if active
            if (currentGuard.tempTripped) {
                Surface(
                    color = VoltaRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, VoltaRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = VoltaRed, modifier = Modifier.size(20.dp))
                            Text(
                                text = "🔥 OVERHEAT TRIPPED (${currentGuard.tripTempC ?: 65.0}°C)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = VoltaRed
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Emergency shut down due to overheating. For fire safety, power does NOT turn back on automatically. Check connected appliances.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onReset,
                            colors = ButtonDefaults.buttonColors(containerColor = VoltaRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Thermal Trip", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (currentGuard.tripped) {
                Surface(
                    color = VoltaAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, VoltaAmber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = VoltaAmber, modifier = Modifier.size(20.dp))
                            Text(
                                text = "🚨 VOLTAGE TRIPPED (${currentGuard.tripVoltage ?: 250.0}V)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = VoltaAmber
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Power shut off to protect appliances. Will auto-recover once grid voltage stabilizes for ${currentGuard.safeDelayMin} minutes continuously.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onReset,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Manual Trip Reset", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Card 1: Temperature Overheat Guard
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.DeviceThermostat, contentDescription = null, tint = VoltaRed, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Temperature Guard",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = tempEnabled,
                            onCheckedChange = { tempEnabled = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Emergency cutoff if any outlet exceeds safety threshold. For fire safety, power does NOT turn back on automatically.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Max Temp Cutoff:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        TagBadge(
                            text = "${maxTempC.toInt()}°C",
                            color = if (maxTempC > 70) VoltaRed else VoltaAmber
                        )
                    }

                    Slider(
                        value = maxTempC,
                        onValueChange = { maxTempC = it },
                        valueRange = 40f..80f,
                        steps = 7,
                        enabled = tempEnabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Card 2: Voltage Surge & Brownout Guard
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = VoltaAmber, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Voltage Guard",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Protects compressors, refrigerators, and electronics against dangerous brownouts and high voltage spikes. Automatically restores active outlets after stable delay.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = minV,
                            onValueChange = { minV = it },
                            label = { Text("Min Voltage (V)") },
                            placeholder = { Text("195.0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxV,
                            onValueChange = { maxV = it },
                            label = { Text("Max Voltage (V)") },
                            placeholder = { Text("250.0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = safeDelayMin,
                        onValueChange = { safeDelayMin = it },
                        label = { Text("Safe Delay (Minutes)") },
                        placeholder = { Text("3") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "When voltage normalizes, Volta restores only the outlets that were originally on.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val parsedMin = minV.toDoubleOrNull() ?: 195.0
                        val parsedMax = maxV.toDoubleOrNull() ?: 250.0
                        val parsedDelay = safeDelayMin.toIntOrNull() ?: 3
                        onSave(enabled, parsedMin, parsedMax, parsedDelay, tempEnabled, maxTempC.toDouble())
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_guard_button")
                ) {
                    Text("Save Guard Settings", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
