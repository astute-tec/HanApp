package com.hanapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp

@Composable
fun TianZiGe(
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFFE91E63).copy(alpha = 0.6f),
    strokeWidth: Float = 3f
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        // 绘制外框
        drawRect(
            color = lineColor,
            topLeft = Offset(0f, 0f),
            size = size,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth * 2)
        )
        
        // 绘制水平中线 (虚线)
        drawLine(
            color = lineColor,
            start = Offset(0f, height / 2),
            end = Offset(width, height / 2),
            strokeWidth = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )
        
        // 绘制垂直中线 (虚线)
        drawLine(
            color = lineColor,
            start = Offset(width / 2, 0f),
            end = Offset(width / 2, height),
            strokeWidth = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )
        
        // 绘制斜线 (\)
        drawLine(
            color = lineColor,
            start = Offset(0f, 0f),
            end = Offset(width, height),
            strokeWidth = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )
        
        // 绘制斜线 (/)
        drawLine(
            color = lineColor,
            start = Offset(width, 0f),
            end = Offset(0f, height),
            strokeWidth = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )
    }
}
