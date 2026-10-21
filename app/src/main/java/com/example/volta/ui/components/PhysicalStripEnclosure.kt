package com.example.volta.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.model.PowerStrip
import androidx.compose.material.icons.filled.RestartAlt
import com.example.volta.theme.VoltaCyan
import com.example.volta.theme.VoltaAmber
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaGreenBright
import com.example.volta.theme.VoltaRed
import java.util.Locale

/**
 * Realistic Hardware Power Strip Enclosure matching the Volta Web App (.strip-physical).
 * Features:
 * - Top Rail: Strip Usage (Watts) & On/Off Outlets Indicator (X of Y ON + LED matrix)
 *   placed OVER the buttons to let the outlet buttons spread out across the full width,
 *   preventing accidental wrong taps.
 * - Horizontal Buttons Strip:
 *   - 1 Illuminated Main Rocker Switch (ALL ON / PARTIAL / ALL OFF master toggle)
 *   - Generously spread out tactile socket buttons with glowing bolt icons, lock badges, and live watts.
 */
@Composable
fun PhysicalStripEnclosure(
    strip: PowerStrip,
    onToggleOutlet: (Int, Boolean) -> Unit,
    onToggleMaster: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onLockedOutletClick: (Int) -> Unit = {},
    onLongClickEnclosure: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val outlets = strip.outlets
    val unlockedOutlets = outlets.filter { !it.locked }
    val activeUnlockedCount = unlockedOutlets.count { it.on }
    val allUnlockedAreOn = strip.online && unlockedOutlets.isNotEmpty() && activeUnlockedCount == unlockedOutlets.size
    val activeCount = outlets.count { it.on }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(strip.mac) {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClickEnclosure?.invoke()
                    }
                )
            },
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0C1322),
        border = BorderStroke(1.2.dp, Color(0xFF22304E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // =========================================================================
            // 1. TOP RAIL (OVER THE BUTTONS): STRIP USAGE & ON/OFF OUTLETS INDICATOR
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Strip Usage Indicator (Left)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color(0xFF1E293B), CircleShape)
                            .border(
                                1.dp,
                                if (strip.totalPowerW > 0) VoltaGreenBright.copy(alpha = 0.6f) else Color(0xFF334155),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Strip Usage",
                            tint = if (strip.totalPowerW > 0) VoltaGreenBright else Color(0xFF64748B),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format(Locale.US, "%.1f", strip.totalPowerW),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = " W",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = VoltaGreenBright
                        )
                        Text(
                            text = "usage",
                            fontSize = 9.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(start = 3.dp, bottom = 1.dp)
                        )
                    }
                }

                // On/Off Outlets Indicator (Right)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Channel LED status indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (outlet in outlets) {
                            val dotColor = when {
                                outlet.locked -> VoltaAmber
                                outlet.on -> VoltaGreenBright
                                else -> Color(0xFF334155)
                            }
                            Box(
                                modifier = Modifier
                                    .size(6.5.dp)
                                    .shadow(if (outlet.on) 3.dp else 0.dp, CircleShape)
                                    .background(dotColor, CircleShape)
                            )
                        }
                    }

                    // On/Off Count Pill Badge
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = if (activeCount > 0) VoltaGreen.copy(alpha = 0.15f) else Color(0xFF1E293B),
                        border = BorderStroke(
                            1.dp,
                            if (activeCount > 0) VoltaGreen.copy(alpha = 0.45f) else Color(0xFF334155)
                        )
                    ) {
                        Text(
                            text = "$activeCount of ${outlets.size} ON",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeCount > 0) VoltaGreenBright else Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Sleek Horizontal Hardware Rail Accent Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .clip(RoundedCornerShape(0.5.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF1E293B).copy(alpha = 0.2f),
                                Color(0xFF38BDF8).copy(alpha = 0.35f),
                                Color(0xFF22C55E).copy(alpha = 0.45f),
                                Color(0xFF1E293B).copy(alpha = 0.2f)
                            )
                        )
                    )
            )

            Spacer(modifier = Modifier.height(9.dp))

            // =========================================================================
            // 2. HORIZONTAL STRIP OF BUTTONS (SPREAD OUT TO PREVENT ACCIDENTAL WRONG PRESS)
            // =========================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // MAIN POWER ROCKER SWITCH ("Main")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 10.dp)
                ) {
                    val masterBg = when {
                        allUnlockedAreOn -> Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF15803D)))
                        activeUnlockedCount > 0 -> Brush.linearGradient(listOf(Color(0xFFFBBF24), Color(0xFFD97706)))
                        else -> Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0B1120)))
                    }
                    val masterBorder = when {
                        allUnlockedAreOn -> Color(0xFF22C55E)
                        activeUnlockedCount > 0 -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444).copy(alpha = 0.6f)
                    }
                    val masterIconColor = when {
                        allUnlockedAreOn || activeUnlockedCount > 0 -> Color.White
                        else -> Color(0xFFEF4444)
                    }
                    val masterSublabel = when {
                        allUnlockedAreOn -> "ALL ON"
                        activeUnlockedCount > 0 -> "PARTIAL"
                        else -> "ALL OFF"
                    }
                    val masterSublabelColor = when {
                        allUnlockedAreOn -> VoltaGreenBright
                        activeUnlockedCount > 0 -> Color(0xFFFBBF24)
                        else -> Color(0xFF94A3B8)
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .shadow(elevation = if (allUnlockedAreOn) 6.dp else 2.dp, shape = CircleShape)
                            .background(masterBg, CircleShape)
                            .border(1.8.dp, masterBorder, CircleShape)
                            .clickable {
                                val turnOn = activeUnlockedCount == 0
                                onToggleMaster(turnOn)
                            }
                            .testTag("master_power_btn_${strip.mac}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Master Switch",
                            tint = masterIconColor,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Main",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = masterSublabel,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = masterSublabelColor,
                        maxLines = 1
                    )
                }

                // Vertical Hardware Divider
                Box(
                    modifier = Modifier
                        .height(52.dp)
                        .width(1.5.dp)
                        .background(Color(0xFF24314F))
                )

                // SPREADED HORIZONTAL OUTLET SOCKET BUTTONS
                // (Now occupying the entire remaining width with generous spacing)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (outlet in outlets) {
                        val isOn = outlet.on
                        val isLocked = outlet.locked

                        val socketBg = when {
                            isOn -> Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF16A34A)))
                            else -> Brush.linearGradient(listOf(Color(0xFF090D16), Color(0xFF1A2333)))
                        }
                        val socketBorder = when {
                            isLocked -> VoltaAmber
                            isOn -> Color(0xFF22C55E)
                            else -> Color(0xFF334155)
                        }
                        val boltColor = when {
                            isOn -> Color.White
                            else -> Color(0xFF64748B)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                val isCycling = outlet.activeCycle != null
                                val cycleRemaining = outlet.activeCycle?.remaining ?: 0

                                // Circular Grounded Socket Receptacle with generous touch target
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .shadow(elevation = if (isOn || isCycling) 5.dp else 1.dp, shape = CircleShape)
                                        .then(
                                            if (isCycling) Modifier.background(Color(0x3338BDF8), CircleShape)
                                            else Modifier.background(socketBg, CircleShape)
                                        )
                                        .border(1.8.dp, if (isCycling) VoltaCyan else socketBorder, CircleShape)
                                        .clickable {
                                            if (isCycling) {
                                                // Cycle in progress
                                            } else if (isLocked) {
                                                onLockedOutletClick(outlet.n)
                                            } else {
                                                onToggleOutlet(outlet.n, !isOn)
                                            }
                                        }
                                        .testTag("socket_btn_${strip.mac}_${outlet.n}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isCycling) Icons.Default.RestartAlt else Icons.Default.Bolt,
                                        contentDescription = "Outlet ${outlet.n}",
                                        tint = if (isCycling) VoltaCyan else boltColor,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                // Lock overlay badge (distinct golden padlock)
                                if (isLocked) {
                                    Box(
                                        modifier = Modifier
                                            .size(15.dp)
                                            .offset(x = 3.dp, y = (-3).dp)
                                            .background(VoltaAmber, CircleShape)
                                            .border(1.dp, Color(0xFF0B1120), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Locked",
                                            tint = Color.Black,
                                            modifier = Modifier.size(8.5.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            val isCycling = outlet.activeCycle != null
                            val cycleRemaining = outlet.activeCycle?.remaining ?: 0
                            val displayName = outlet.name.ifBlank { "Outlet ${outlet.n}" }
                            Text(
                                text = if (isLocked) "$displayName 🔒" else displayName,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOn || isCycling) Color.White else Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )

                            // Live Power or Status
                            Text(
                                text = when {
                                    isCycling -> "CYCLE (${cycleRemaining}s)"
                                    isOn -> "${String.format(Locale.US, "%.0f", outlet.powerW)}W"
                                    else -> "OFF"
                                },
                                fontSize = 9.sp,
                                fontWeight = if (isOn || isCycling) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isCycling -> VoltaCyan
                                    isOn -> VoltaGreenBright
                                    else -> Color(0xFF64748B)
                                },
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
