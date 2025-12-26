package com.hanapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun WaveformView(
    amplitudes: List<Float>, // 0.0 to 1.0
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF00BCD4)
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val barCount = amplitudes.size
        if (barCount == 0) return@Canvas
        
        val spacing = 4.dp.toPx()
        val barWidth = (width - (barCount - 1) * spacing) / barCount
        
        amplitudes.forEachIndexed { index, amplitude ->
            val barHeight = height * amplitude.coerceIn(0.1f, 1f)
            val x = index * (barWidth + spacing)
            val y = (height - barHeight) / 2
            
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.8f),
                        color.copy(alpha = 1.0f),
                        color.copy(alpha = 0.8f)
                    )
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}
