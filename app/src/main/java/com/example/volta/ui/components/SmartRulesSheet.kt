package com.example.volta.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.model.SmartRule
import com.example.volta.theme.VoltaAmber
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaCyan
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaSlateBorder
import com.example.volta.theme.VoltaSlateCard
import com.example.volta.theme.VoltaSlateSubCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartRulesSheet(
    stripName: String,
    outletNumber: Int,
    initialRule: SmartRule?,
    onSave: (SmartRule) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var enabled by remember { mutableStateOf(initialRule?.enabled ?: true) }
    var chargeArmW by remember { mutableDoubleStateOf(initialRule?.chargeArmW ?: 15.0) }
    var chargeCompW by remember { mutableDoubleStateOf(initialRule?.chargeCompW ?: 3.0) }
    var chargeHoldSec by remember { mutableIntStateOf(initialRule?.chargeHoldSec ?: 60) }
    var idleW by remember { mutableDoubleStateOf(initialRule?.idleW ?: 5.0) }
    var idleHoldSec by remember { mutableIntStateOf(initialRule?.idleHoldSec ?: 120) }
    var maxW by remember { mutableDoubleStateOf(initialRule?.maxW ?: 2000.0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VoltaSlateCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = VoltaCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Smart Automation Rules",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Outlet $outletNumber • $stripName",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VoltaGreen)
                )
            }

            HorizontalDivider(color = VoltaSlateBorder)

            // Rule 1: Charge-Complete Cutoff
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = VoltaSlateSubCard),
                border = BorderStroke(1.dp, VoltaSlateBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = VoltaGreen, modifier = Modifier.size(18.dp))
                        Text("Battery Charge-Complete Protection", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    }
                    Text(
                        "Arms when wattage reaches ${chargeArmW.toInt()}W. Automatically turns outlet OFF after consumption drops below ${chargeCompW.toInt()}W for ${chargeHoldSec}s.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = chargeArmW.toString(),
                            onValueChange = { chargeArmW = it.toDoubleOrNull() ?: chargeArmW },
                            label = { Text("Arm Watt (W)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = chargeCompW.toString(),
                            onValueChange = { chargeCompW = it.toDoubleOrNull() ?: chargeCompW },
                            label = { Text("Cutoff Watt (W)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Rule 2: Idle Standby Cutoff
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = VoltaSlateSubCard),
                border = BorderStroke(1.dp, VoltaSlateBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Bedtime, contentDescription = null, tint = VoltaAmber, modifier = Modifier.size(18.dp))
                        Text("Vampire Power / Idle Standby Cutoff", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    }
                    Text(
                        "Cuts off power if appliance remains in standby mode below ${idleW.toInt()}W for ${idleHoldSec}s.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = idleW.toString(),
                        onValueChange = { idleW = it.toDoubleOrNull() ?: idleW },
                        label = { Text("Standby Threshold (W)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Rule 3: Max Wattage Limit
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = VoltaSlateSubCard),
                border = BorderStroke(1.dp, VoltaSlateBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = VoltaCyan, modifier = Modifier.size(18.dp))
                        Text("Overload Ceiling Cutoff", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    }
                    Text(
                        "Instant emergency cutoff if outlet power exceeds ${maxW.toInt()}W.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = maxW.toString(),
                        onValueChange = { maxW = it.toDoubleOrNull() ?: maxW },
                        label = { Text("Max Power Ceiling (W)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Save Button
            Button(
                onClick = {
                    onSave(
                        SmartRule(
                            outlet = outletNumber,
                            enabled = enabled,
                            chargeArmW = chargeArmW,
                            chargeCompW = chargeCompW,
                            chargeHoldSec = chargeHoldSec,
                            idleW = idleW,
                            idleHoldSec = idleHoldSec,
                            maxW = maxW
                        )
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = VoltaBlue),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Save Smart Rules", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
