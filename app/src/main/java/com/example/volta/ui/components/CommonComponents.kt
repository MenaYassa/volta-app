package com.example.volta.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaGreenBright
import com.example.volta.theme.VoltaRed
import com.example.volta.theme.VoltaYellow

@Composable
fun StatusDot(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(9.dp)
            .background(
                color = if (isOnline) VoltaGreenBright else VoltaRed,
                shape = CircleShape
            )
    )
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
    bgColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(99.dp),
        border = CardDefaults.outlinedCardBorder()
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
                    text = "Current: ${String.format("%.1f", dataPoints.last())} $unit",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = lineColor
                )
                Text(
                    text = "Avg: ${String.format("%.1f", avgVal)} $unit | Peak: ${String.format("%.1f", maxVal)} $unit",
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
