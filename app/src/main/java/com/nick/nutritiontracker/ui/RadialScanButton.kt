package com.nick.nutritiontracker.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun RadialScanButton(
    onScan: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isLongPressing by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    
    val slots = listOf("Frühstück", "Mittag", "Abend", "Snack")
    // Sector layout: Top (Frühstück), Right (Mittag), Bottom (Abend), Left (Snack)
    
    var selectedSlotIndex by remember { mutableIntStateOf(-1) }
    val scale by animateFloatAsState(
        targetValue = if (isLongPressing) 1.2f else 1f,
        label = "fabScale"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        // Radial Menu
        AnimatedVisibility(
            visible = isLongPressing,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                slots.forEachIndexed { index, name ->
                    // 0: Top (-90 deg), 1: Right (0 deg), 2: Bottom (90 deg), 3: Left (180 deg)
                    val angle = index * 90f - 90f 
                    val rad = (angle * PI / 180.0).toFloat()
                    val dist = 85.dp
                    
                    val isSelected = selectedSlotIndex == index
                    
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (dist.toPx() * cos(rad)).roundToInt(),
                                    (dist.toPx() * sin(rad)).roundToInt()
                                )
                            }
                            .size(if (isSelected) 84.dp else 72.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name, 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { /* Handled by pointerInput to avoid conflicts with long press */ },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .scale(scale)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onScan("Snack") }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { 
                            isLongPressing = true
                            dragOffset = Offset.Zero
                        },
                        onDragEnd = {
                            if (isLongPressing) {
                                val finalSlot = if (selectedSlotIndex != -1) slots[selectedSlotIndex] else "Snack"
                                onScan(finalSlot)
                            }
                            isLongPressing = false
                            selectedSlotIndex = -1
                        },
                        onDragCancel = {
                            isLongPressing = false
                            selectedSlotIndex = -1
                        },
                        onDrag = { change, dragAmount ->
                            dragOffset += dragAmount
                            val dist = dragOffset.getDistance()
                            if (dist > 40f) {
                                // atan2 returns angle in radians from -PI to PI
                                val angleRad = atan2(dragOffset.y, dragOffset.x)
                                val angleDeg = (angleRad * 180f / PI.toFloat())
                                // Normalize to 0..360
                                val normalizedAngle = (angleDeg + 360) % 360
                                // 270 (Top) -> index 0, 0 (Right) -> index 1, 90 (Bottom) -> index 2, 180 (Left) -> index 3
                                // Rotate by 45 to align sectors
                                val sector = (((normalizedAngle + 45 + 90) % 360) / 90).toInt()
                                selectedSlotIndex = sector % 4
                            } else {
                                selectedSlotIndex = -1
                            }
                            change.consume()
                        }
                    )
                }
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan")
        }
    }
}
