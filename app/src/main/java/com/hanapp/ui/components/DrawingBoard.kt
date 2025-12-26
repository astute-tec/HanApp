package com.hanapp.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.digitalink.Ink
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.max

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingBoard(
    modifier: Modifier = Modifier,
    char: String? = null,
    historyInk: Ink? = null,
    strokeColor: Color = Color.Black,
    strokeWidth: Float = 40f,
    clearTrigger: Int = 0,
    enabled: Boolean = true,
    onInkChanged: (Ink) -> Unit = {}
) {
    val paths = remember { mutableStateListOf<Path>() }
    
    val inkBuilder = remember { mutableStateOf(Ink.builder()) }
    var currentStrokePoints = remember { mutableStateListOf<Ink.Point>() }
    // 用于强制触发重刷的计数器
    var renderTrigger by remember { mutableIntStateOf(0) }

    // 处理清除操作和历史回显
    LaunchedEffect(clearTrigger, char, historyInk) {
        paths.clear()
        inkBuilder.value = Ink.builder()
        
        // 如果有历史数据，将其转换为 Path 并添加到列表
        historyInk?.let { ink ->
            ink.strokes.forEach { stroke ->
                val path = Path()
                stroke.points.forEachIndexed { index, point ->
                    if (index == 0) path.moveTo(point.x, point.y)
                    else {
                        // 简单历史回显不考虑压感，使用固定粗细
                        path.lineTo(point.x, point.y)
                    }
                }
                paths.add(path)
                inkBuilder.value.addStroke(stroke)
            }
        }
        
        // 只有在主动清除（clearTrigger 改变）时才通知。初始化（clearTrigger 为 0）时不应触发识别
        if (clearTrigger > 0) {
             onInkChanged(inkBuilder.value.build())
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val canvasSize = if (width < height) width else height
        val s = canvasSize / 1024f
        val offsetX = (width - canvasSize) / 2f
        val offsetY = (height - canvasSize) / 2f

        // 背景字帖 (水印) - 移除 padding 以便与田字格对齐
        if (char != null) {
            key(char) {
                StrokeAnimationView(
                    modifier = Modifier.fillMaxSize(),
                    char = char,
                    isPlaying = false,
                    alpha = 0.8f
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    if (!enabled) return@pointerInteropFilter false

                    // 将屏幕坐标映射到 1024 坐标系
                    val normalizedX = (event.x - offsetX) / s
                    val normalizedY = (event.y - offsetY) / s // 移除 Y 轴反转，使用标准坐标系
                    
                    val t = System.currentTimeMillis()

                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            currentStrokePoints.clear()
                            currentStrokePoints.add(com.google.mlkit.vision.digitalink.Ink.Point.create(normalizedX, normalizedY, t))
                            renderTrigger++
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            currentStrokePoints.add(com.google.mlkit.vision.digitalink.Ink.Point.create(normalizedX, normalizedY, t))
                            renderTrigger++
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            val strokeBuilder = com.google.mlkit.vision.digitalink.Ink.Stroke.builder()
                            currentStrokePoints.forEach { strokeBuilder.addPoint(it) }
                            inkBuilder.value.addStroke(strokeBuilder.build())
                            currentStrokePoints.clear()
                            renderTrigger++
                            onInkChanged(inkBuilder.value.build())
                            true
                        }
                        else -> false
                    }
                }
        ) {
            // 通过访问 renderTrigger 强制 Compose 在 ACTION_MOVE 周期重绘 Canvas
            val _t = renderTrigger

            // 绘制时应用相同的变换：平移、缩放
            // 笔迹数据已经是屏幕正向 (Y向下)，直接应用正向变换
            withTransform({
                translate(left = offsetX, top = offsetY)
                scale(scaleX = s, scaleY = s, pivot = Offset.Zero)
            }) {
                // 1. 绘制已完成的笔迹
                inkBuilder.value.build().strokes.forEach { stroke ->
                    drawStrokeBrush(stroke, strokeColor, strokeWidth, s)
                }

                // 2. 绘制当前正在书写的笔迹（实时预览）
                if (currentStrokePoints.isNotEmpty()) {
                    val tempStrokeBuilder = com.google.mlkit.vision.digitalink.Ink.Stroke.builder()
                    currentStrokePoints.forEach { tempStrokeBuilder.addPoint(it) }
                    drawStrokeBrush(tempStrokeBuilder.build(), strokeColor, strokeWidth, s)
                }
            }
        }
    }
}

/**
 * 封装毛笔效果绘制逻辑
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokeBrush(
    stroke: com.google.mlkit.vision.digitalink.Ink.Stroke,
    strokeColor: Color,
    strokeWidth: Float,
    s: Float
) {
    var lastPoint: com.google.mlkit.vision.digitalink.Ink.Point? = null
    stroke.points.forEach { point ->
        if (lastPoint != null) {
            // 钢笔效果：保持等宽绘制，移除压力感应
            drawLine(
                color = strokeColor,
                start = Offset(lastPoint!!.x, lastPoint!!.y),
                end = Offset(point.x, point.y),
                strokeWidth = strokeWidth / s, // 在 1024 坐标系应用转换后的粗细
                cap = StrokeCap.Round
            )
        }
        lastPoint = point
    }
}
