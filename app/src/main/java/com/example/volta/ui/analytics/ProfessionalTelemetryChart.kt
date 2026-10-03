package com.example.volta.ui.analytics

import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.theme.VoltaBlue

@Composable
fun ProfessionalTelemetryChart(
    dataPoints: List<Double>,
    lineColor: Color = VoltaBlue,
    unit: String = "",
    title: String = "Telemetry",
    icon: ImageVector = Icons.Default.ShowChart,
    modifier: Modifier = Modifier
) {
    if (dataPoints.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = lineColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Awaiting Telemetry Readings",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Readings stream in every 30s as your power strip reports.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    // Memoize statistical calculations so they never re-compute on scrolling
    val minVal = remember(dataPoints) { dataPoints.minOrNull() ?: 0.0 }
    val maxVal = remember(dataPoints) { dataPoints.maxOrNull() ?: 1.0 }
    val avgVal = remember(dataPoints) { dataPoints.average() }
    val lastVal = remember(dataPoints) { dataPoints.lastOrNull() ?: 0.0 }
    val range = remember(minVal, maxVal) { if (maxVal - minVal > 0.01) maxVal - minVal else 1.0 }

    // Interactive scrubber touch state
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val inspectedValue = selectedIndex?.let { idx ->
        if (idx in dataPoints.indices) dataPoints[idx] else null
    }

    val density = LocalDensity.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title + Dynamic Interactive Inspected Pill or Live Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(lineColor.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = lineColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedIndex != null) "Scrubbing sample #${selectedIndex!! + 1}" else "Live telemetry trend",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = if (inspectedValue != null) lineColor else lineColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (inspectedValue != null) Color.White else lineColor, CircleShape)
                        )
                        Text(
                            text = "${String.format("%.1f", inspectedValue ?: lastVal)} $unit",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (inspectedValue != null) Color.White else lineColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-metrics KPI Badges (Min, Avg, Peak)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChartStatBadge(label = "Minimum", value = "${String.format("%.1f", minVal)} $unit", modifier = Modifier.weight(1f))
                ChartStatBadge(label = "Average", value = "${String.format("%.1f", avgVal)} $unit", modifier = Modifier.weight(1f))
                ChartStatBadge(label = "Peak", value = "${String.format("%.1f", maxVal)} $unit", modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // High-Performance Cached Canvas using drawWithCache
            // Zero memory allocation or path re-creation on scroll!
            val surfaceColor = MaterialTheme.colorScheme.surface
            val labelTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f).toArgb()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .pointerInput(dataPoints) {
                        detectTapGestures(
                            onPress = { offset ->
                                val stepX = size.width / (dataPoints.size - 1).coerceAtLeast(1)
                                val idx = (offset.x / stepX).toInt().coerceIn(0, dataPoints.size - 1)
                                selectedIndex = idx
                                tryAwaitRelease()
                                selectedIndex = null
                            }
                        )
                    }
                    .pointerInput(dataPoints) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val stepX = size.width / (dataPoints.size - 1).coerceAtLeast(1)
                                val idx = (offset.x / stepX).toInt().coerceIn(0, dataPoints.size - 1)
                                selectedIndex = idx
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val stepX = size.width / (dataPoints.size - 1).coerceAtLeast(1)
                                val idx = (change.position.x / stepX).toInt().coerceIn(0, dataPoints.size - 1)
                                selectedIndex = idx
                            },
                            onDragEnd = {
                                selectedIndex = null
                            },
                            onDragCancel = {
                                selectedIndex = null
                            }
                        )
                    }
                    .drawWithCache {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val paddingTop = 12f
                        val paddingBottom = 20f
                        val paddingRight = 44f // Space for y-axis labels
                        val chartWidth = canvasWidth - paddingRight
                        val effectiveHeight = canvasHeight - paddingTop - paddingBottom

                        val nPoints = dataPoints.size
                        val stepX = chartWidth / (nPoints - 1).coerceAtLeast(1)

                        // Cached Coordinates
                        val points = Array(nPoints) { i ->
                            val x = i * stepX
                            val normalized = ((dataPoints[i] - minVal) / range).toFloat().coerceIn(0f, 1f)
                            val y = paddingTop + effectiveHeight * (1f - normalized)
                            Offset(x, y)
                        }

                        // Build Smooth Monotone Cubic Spline Path
                        val strokePath = Path()
                        val fillPath = Path()

                        if (points.isNotEmpty()) {
                            strokePath.moveTo(points[0].x, points[0].y)
                            fillPath.moveTo(points[0].x, points[0].y)

                            for (i in 0 until points.size - 1) {
                                val p0 = if (i > 0) points[i - 1] else points[i]
                                val p1 = points[i]
                                val p2 = points[i + 1]
                                val p3 = if (i + 2 < points.size) points[i + 2] else p2

                                // Catmull-Rom to Cubic Bezier control points conversion
                                val c1x = p1.x + (p2.x - p0.x) / 6f
                                val c1y = p1.y + (p2.y - p0.y) / 6f
                                val c2x = p2.x - (p3.x - p1.x) / 6f
                                val c2y = p2.y - (p3.y - p1.y) / 6f

                                strokePath.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
                                fillPath.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
                            }

                            fillPath.lineTo(points.last().x, canvasHeight - paddingBottom)
                            fillPath.lineTo(points.first().x, canvasHeight - paddingBottom)
                            fillPath.close()
                        }

                        // Cached Gradient Brush
                        val gradientBrush = Brush.verticalGradient(
                            colors = listOf(
                                lineColor.copy(alpha = 0.35f),
                                lineColor.copy(alpha = 0.12f),
                                lineColor.copy(alpha = 0.02f),
                                Color.Transparent
                            ),
                            startY = paddingTop,
                            endY = canvasHeight - paddingBottom
                        )

                        // Grid paint
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        val textPaint = Paint().apply {
                            color = labelTextColor
                            textSize = with(density) { 10.sp.toPx() }
                            isAntiAlias = true
                            textAlign = Paint.Align.LEFT
                        }

                        onDrawBehind {
                            // 1. Draw 3 reference grid levels with real value labels on the right
                            val gridColor = Color.Gray.copy(alpha = 0.18f)
                            val yLevels = listOf(
                                Pair(paddingTop, String.format("%.0f", maxVal)),
                                Pair(paddingTop + effectiveHeight * 0.5f, String.format("%.0f", (maxVal + minVal) / 2.0)),
                                Pair(paddingTop + effectiveHeight, String.format("%.0f", minVal))
                            )

                            yLevels.forEach { (y, label) ->
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(chartWidth, y),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                                drawContext.canvas.nativeCanvas.drawText(
                                    label,
                                    chartWidth + 6f,
                                    y + 4f,
                                    textPaint
                                )
                            }

                            if (points.isNotEmpty()) {
                                // 2. Draw Cached Gradient Area
                                drawPath(path = fillPath, brush = gradientBrush)

                                // 3. Draw Cached Stroke Line
                                drawPath(
                                    path = strokePath,
                                    color = lineColor,
                                    style = Stroke(
                                        width = 2.5.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )

                                // 4. Interactive Scrubber Hairline or Default Latest Marker
                                val activeIdx = selectedIndex
                                if (activeIdx != null && activeIdx in points.indices) {
                                    val activePt = points[activeIdx]

                                    // Vertical hairline across chart
                                    drawLine(
                                        color = lineColor.copy(alpha = 0.8f),
                                        start = Offset(activePt.x, paddingTop),
                                        end = Offset(activePt.x, canvasHeight - paddingBottom),
                                        strokeWidth = 1.5.dp.toPx()
                                    )

                                    // Outer pulse halo
                                    drawCircle(
                                        color = lineColor.copy(alpha = 0.3f),
                                        radius = 10.dp.toPx(),
                                        center = activePt
                                    )
                                    // Solid circle
                                    drawCircle(
                                        color = lineColor,
                                        radius = 5.dp.toPx(),
                                        center = activePt
                                    )
                                    // White center pip
                                    drawCircle(
                                        color = Color.White,
                                        radius = 2.dp.toPx(),
                                        center = activePt
                                    )
                                } else {
                                    // Normal state: Highlight the latest point
                                    val lastPt = points.last()
                                    drawCircle(
                                        color = lineColor.copy(alpha = 0.25f),
                                        radius = 8.dp.toPx(),
                                        center = lastPt
                                    )
                                    drawCircle(
                                        color = lineColor,
                                        radius = 4.dp.toPx(),
                                        center = lastPt
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 1.5.dp.toPx(),
                                        center = lastPt
                                    )
                                }
                            }
                        }
                    }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Footer info: scrubbing guidance + sample resolution
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💡 Touch & drag to inspect",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = "${dataPoints.size} samples • 30s rate",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ChartStatBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
