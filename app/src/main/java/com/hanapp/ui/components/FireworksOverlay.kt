package com.hanapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.random.Random

data class Particle(
    val x: Float,
    val y: Float,
    val color: Color,
    val velocityX: Float,
    val velocityY: Float,
    var alpha: Float = 1f
)

@Composable
fun FireworksOverlay(isVisible: Boolean) {
    if (!isVisible) return

    val progress = remember { Animatable(0f) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(1500, easing = LinearOutSlowInEasing)
            )
        }
    }

    val particles = remember(isVisible) {
        List(50) {
            Particle(
                x = 0.5f,
                y = 0.5f,
                color = Color(
                    Random.nextInt(200, 256),
                    Random.nextInt(100, 200),
                    Random.nextInt(150, 256)
                ),
                velocityX = (Random.nextFloat() - 0.5f) * 0.05f,
                velocityY = (Random.nextFloat() - 0.5f) * 0.05f
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = progress.value

        particles.forEach { p ->
            val currentX = (p.x + p.velocityX * t * 50) * w
            val currentY = (p.y + p.velocityY * t * 50) * h
            
            drawCircle(
                color = p.color.copy(alpha = (1f - t).coerceIn(0f, 1f)),
                radius = 10f,
                center = Offset(currentX, currentY)
            )
        }
    }
}
