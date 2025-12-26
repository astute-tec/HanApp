package com.hanapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun VoiceActionButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    // 呼吸动画效果 (当处于活动状态时)
    val infiniteTransition = rememberInfiniteTransition(label = "voice_active")
    val activeScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .scale(if (isActive) activeScale else 1f)
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.9f),
                        color.copy(alpha = 0.7f)
                    )
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // 毛玻璃装饰感层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
        )
        
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(32.dp),
            tint = Color.White
        )
    }
}
