package com.example.volta.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaCyan
import com.example.volta.theme.VoltaRed
import com.example.volta.theme.VoltaSlateBorder
import com.example.volta.theme.VoltaSlateCard
import com.example.volta.theme.VoltaSlateSubCard

@Composable
fun CycleConfirmationDialog(
    stripName: String,
    outletName: String,
    onConfirm: (durationSeconds: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDuration by remember { mutableIntStateOf(20) }
    val presets = listOf(10, 20, 30, 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VoltaSlateCard,
        shape = RoundedCornerShape(16.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.RestartAlt,
                contentDescription = null,
                tint = VoltaCyan,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Power Cycle (Pulse Restart)",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Power to $outletName ($stripName) will be cut immediately and automatically restored after the set duration.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Warning callout
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = VoltaRed.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, VoltaRed.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = VoltaRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Connected routers or modems will temporarily disconnect from the internet.",
                            fontSize = 11.5.sp,
                            color = VoltaRed,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Preset Duration Chips
                Text(
                    text = "Power-Off Duration:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { sec ->
                        val isSelected = selectedDuration == sec
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) VoltaBlue else VoltaSlateSubCard,
                            border = BorderStroke(1.dp, if (isSelected) VoltaCyan else VoltaSlateBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedDuration = sec }
                        ) {
                            Text(
                                text = "${sec}s",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedDuration) },
                colors = ButtonDefaults.buttonColors(containerColor = VoltaBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Restart Now (${selectedDuration}s)", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
