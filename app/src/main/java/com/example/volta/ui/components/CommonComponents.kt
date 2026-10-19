package com.example.volta.ui.components

import java.util.Locale
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.model.VoltageGuard
import com.example.volta.theme.VoltaAmber
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaCyan
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaGreenBright
import com.example.volta.theme.VoltaRed
import com.example.volta.theme.VoltaYellow

@Composable
fun StatusDot(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    isConnecting: Boolean = false
) {
    if (isConnecting) {
        CircularProgressIndicator(
            modifier = modifier.size(9.dp),
            color = VoltaCyan,
            strokeWidth = 1.5.dp
        )
    } else {
        Box(
            modifier = modifier
                .size(9.dp)
                .background(
                    color = if (isOnline) VoltaGreenBright else VoltaRed,
                    shape = CircleShape
                )
        )
    }
}

@Composable
fun StatBox(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    highlightColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = highlightColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TagBadge(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    bgColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    modifier: Modifier = Modifier
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(99.dp),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun InteractiveToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val trackBg by animateColorAsState(
        targetValue = if (!enabled) Color(0xFF64748B).copy(alpha = 0.35f)
        else if (checked) VoltaGreen
        else Color(0xFF475569).copy(alpha = 0.45f),
        label = "trackBg"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 0.dp,
        label = "thumbOffset"
    )

    Box(
        modifier = modifier
            .width(50.dp)
            .height(28.dp)
            .background(trackBg, RoundedCornerShape(99.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset)
                .size(22.dp)
                .background(Color.White, CircleShape)
        )
    }
}

@Composable
fun RenameDialog(
    title: String,
    initialValue: String,
    label: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) onConfirm(text.trim())
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun TelemetryLineChart(
    dataPoints: List<Double>,
    lineColor: Color = VoltaBlue,
    unit: String = "",
    modifier: Modifier = Modifier
) {
    if (dataPoints.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("No telemetry data for this interval", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val minVal = dataPoints.minOrNull() ?: 0.0
    val maxVal = dataPoints.maxOrNull() ?: 1.0
    val range = if (maxVal - minVal > 0.01) maxVal - minVal else 1.0
    val avgVal = dataPoints.average()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Current: ${String.format(Locale.US, "%.1f", dataPoints.last())} $unit",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = lineColor
                )
                Text(
                    text = "Avg: ${String.format(Locale.US, "%.1f", avgVal)} $unit | Peak: ${String.format(Locale.US, "%.1f", maxVal)} $unit",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val width = size.width
                val height = size.height
                val stepX = width / (dataPoints.size - 1).coerceAtLeast(1)

                val points = dataPoints.mapIndexed { index, value ->
                    val x = index * stepX
                    val normalized = ((value - minVal) / range).toFloat().coerceIn(0f, 1f)
                    val y = height - (normalized * (height - 24f)) - 12f
                    Offset(x, y)
                }

                // Fill gradient under curve
                val fillPath = Path()
                if (points.isNotEmpty()) {
                    fillPath.moveTo(points.first().x, height)
                    points.forEach { fillPath.lineTo(it.x, it.y) }
                    fillPath.lineTo(points.last().x, height)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                lineColor.copy(alpha = 0.35f),
                                lineColor.copy(alpha = 0.0f)
                            )
                        )
                    )
                }

                // Line path
                val strokePath = Path()
                if (points.isNotEmpty()) {
                    strokePath.moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val pPrev = points[i - 1]
                        val pCurr = points[i]
                        val controlPoint1 = Offset(pPrev.x + (pCurr.x - pPrev.x) / 2, pPrev.y)
                        val controlPoint2 = Offset(pPrev.x + (pCurr.x - pPrev.x) / 2, pCurr.y)
                        strokePath.cubicTo(
                            controlPoint1.x, controlPoint1.y,
                            controlPoint2.x, controlPoint2.y,
                            pCurr.x, pCurr.y
                        )
                    }
                }

                drawPath(
                    path = strokePath,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // High point dot
                val maxPoint = points.maxByOrNull { it.y }
                if (maxPoint != null) {
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = maxPoint
                    )
                    drawCircle(
                        color = lineColor,
                        radius = 2.dp.toPx(),
                        center = maxPoint
                    )
                }
            }
        }
    }
}

/**
 * Priority State Machine Presentation for Dual Hardware Safety Guard:
 * Priority 1 (Highest): temp_tripped -> Red pulsing banner / badge (OVERHEAT TRIPPED)
 * Priority 2: tripped && safe_since == null -> Red banner / badge (VOLTAGE TRIPPED)
 * Priority 3: tripped && safe_since != null -> Amber banner / progress (Stabilizing elapsed/delay)
 * Priority 4: enabled || temp_enabled -> Green pill (Guard Armed)
 * Priority 5: !enabled && !temp_enabled -> Muted pill (Guard Off)
 */
@Composable
fun SafetyGuardStatusBadge(
    guard: VoltageGuard?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (guard == null) return
    val elapsed = guard.safeSince?.let { ((System.currentTimeMillis() - it) / 1000).coerceAtLeast(0) } ?: 0L
    val totalDelaySec = guard.safeDelayMin * 60

    when {
        guard.tempTripped -> {
            Surface(
                color = VoltaRed.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, VoltaRed),
                modifier = modifier.clickable { onClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🔥 OVERHEAT TRIPPED (${guard.tripTempC ?: 65.0}°C)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoltaRed
                    )
                }
            }
        }
        guard.tripped && guard.safeSince == null -> {
            Surface(
                color = VoltaRed.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, VoltaRed),
                modifier = modifier.clickable { onClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🚨 VOLTAGE TRIPPED (${guard.tripVoltage ?: 250.0}V)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoltaRed
                    )
                }
            }
        }
        guard.tripped && guard.safeSince != null -> {
            Surface(
                color = VoltaAmber.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, VoltaAmber),
                modifier = modifier.clickable { onClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "⏳ Stabilizing (${elapsed}s / ${totalDelaySec}s)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoltaAmber
                    )
                }
            }
        }
        guard.enabled || guard.tempEnabled -> {
            Surface(
                color = VoltaGreen.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, VoltaGreen.copy(alpha = 0.6f)),
                modifier = modifier.clickable { onClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🛡️ Guard Armed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoltaGreen
                    )
                }
            }
        }
        else -> {
            Surface(
                color = Color(0xFF64748B).copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF64748B).copy(alpha = 0.4f)),
                modifier = modifier.clickable { onClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🛡️ Guard Off",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
fun SafetyGuardAlertBanner(
    guard: VoltageGuard?,
    onInspect: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (guard == null || (!guard.tempTripped && !guard.tripped)) return
    val isOverheat = guard.tempTripped
    val bannerColor = if (isOverheat || guard.safeSince == null) VoltaRed else VoltaAmber
    val title = when {
        isOverheat -> "🔥 Overheat Emergency Shutdown (${guard.tripTempC ?: 65.0}°C)"
        guard.safeSince != null -> {
            val elapsed = guard.safeSince?.let { ((System.currentTimeMillis() - it) / 1000).coerceAtLeast(0) } ?: 0L
            val totalDelay = guard.safeDelayMin * 60
            "⏳ Grid Voltage Stabilizing (${elapsed}s / ${totalDelay}s)"
        }
        else -> "🚨 Grid Voltage Safety Trip (${guard.tripVoltage ?: 250.0}V)"
    }
    val description = when {
        isOverheat -> "Outlets cut OFF for fire safety. Does not auto-resume. Tap to inspect & reset."
        guard.safeSince != null -> "Voltage in normal range. Power will restore automatically when stabilization timer completes."
        else -> "Voltage is out of safe range (${guard.minV}V - ${guard.maxV}V). Outlets safely disconnected."
    }

    Surface(
        color = bannerColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, bannerColor),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onInspect() }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = bannerColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun PillBadge(
    text: String,
    color: Color,
    dotColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(dotColor, CircleShape)
                )
            }
            Text(
                text = text,
                color = color,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun MiniMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF131C31),
        border = BorderStroke(1.dp, Color(0xFF24314F))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}



