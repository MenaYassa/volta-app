package com.example.volta.ui.schedules

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.model.OutletTimer
import com.example.volta.model.PowerStrip
import com.example.volta.model.TimerMode
import com.example.volta.theme.VoltaAmber
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaRed

@Composable
fun TimerItemCard(
    timer: OutletTimer,
    strip: PowerStrip?,
    onTogglePause: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetOutletName = if (timer.outlet == 0) {
        "All Outlets"
    } else {
        strip?.outlets?.find { it.n == timer.outlet }?.name ?: "Outlet ${timer.outlet}"
    }

    val actionColor by animateColorAsState(
        when {
            timer.isPaused -> VoltaAmber
            timer.mode == TimerMode.COUNTDOWN && timer.targetActionOn -> VoltaGreen
            timer.mode == TimerMode.COUNTDOWN && !timer.targetActionOn -> VoltaRed
            timer.mode == TimerMode.CYCLIC && timer.currentPhaseOn -> VoltaGreen
            else -> VoltaBlue
        },
        label = "timerColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("timer_card_${timer.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Badges and Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Outlet badge
                    Surface(
                        color = actionColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (timer.outlet == 0) "⚡ Master" else "#${timer.outlet}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = actionColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Mode pill
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (timer.mode == TimerMode.COUNTDOWN) "Countdown" else "Cyclic Loop",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (timer.isPaused) {
                        Surface(
                            color = VoltaAmber.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "PAUSED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = VoltaAmber,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("delete_timer_${timer.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Timer",
                        tint = VoltaRed.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Display: Timer Countdown + Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = timer.label.ifBlank { "${strip?.displayName ?: "Strip"} • $targetOutletName" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    val countdownDisplay = when (timer.mode) {
                        TimerMode.COUNTDOWN -> {
                            val actionName = if (timer.targetActionOn) "ON" else "OFF"
                            "⏱ ${timer.formattedRemaining} left until $actionName"
                        }
                        TimerMode.CYCLIC -> {
                            val nextAction = if (timer.currentPhaseOn) "OFF" else "ON"
                            "⏱ ${timer.formattedRemaining} left until $nextAction"
                        }
                    }

                    Text(
                        text = countdownDisplay,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = actionColor,
                        letterSpacing = 0.5.sp
                    )

                    val cycleProgress = when {
                        timer.isForever -> "cycle ${timer.currentCycle + 1}/∞"
                        timer.repeatCount > 1 -> "cycle ${timer.currentCycle + 1}/${timer.repeatCount}"
                        else -> "1-shot"
                    }

                    val phaseLabel = when (timer.mode) {
                        TimerMode.COUNTDOWN -> "phase: countdown"
                        TimerMode.CYCLIC -> if (timer.currentPhaseOn) "phase: ON" else "phase: OFF"
                    }

                    Text(
                        text = "$phaseLabel • $cycleProgress",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Control Buttons (Pause/Resume + Reset + Delete)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onReset,
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .testTag("reset_timer_${timer.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Timer",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = onTogglePause,
                        modifier = Modifier
                            .size(40.dp)
                            .background(if (timer.isPaused) VoltaGreen else actionColor, CircleShape)
                            .testTag("toggle_timer_${timer.id}")
                    ) {
                        Icon(
                            imageVector = if (timer.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (timer.isPaused) "Resume" else "Pause",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { timer.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = actionColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status Details Footer
            val statusSummary = when (timer.mode) {
                TimerMode.COUNTDOWN -> {
                    val act = if (timer.targetActionOn) "Turn ON" else "Turn OFF"
                    val rep = when {
                        timer.isForever -> "Repeat forever ∞"
                        timer.repeatCount == 1 -> "Run once"
                        else -> "Cycle ${timer.currentCycle + 1} of ${timer.repeatCount}"
                    }
                    "Will $act $targetOutletName • $rep"
                }
                TimerMode.CYCLIC -> {
                    val phase = if (timer.currentPhaseOn) "Active: ON" else "Active: OFF"
                    val rep = when {
                        timer.isForever -> "Looping forever ∞"
                        else -> "Cycle ${timer.currentCycle + 1} of ${timer.repeatCount}"
                    }
                    "$phase • $rep"
                }
            }

            Text(
                text = statusSummary,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
