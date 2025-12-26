package com.hanapp.ui.components

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import com.google.gson.Gson


@Composable
fun StrokeAnimationView(
    modifier: Modifier = Modifier,
    char: String,
    isPlaying: Boolean,
    alpha: Float = 1f,
    onAnimationComplete: () -> Unit = {}
) {
    key(char) {
        val context = LocalContext.current
        val hanziData = remember(char) { HanziDataHelper.loadHanziXmlData(context, char) }
        
        if (hanziData == null) {
            Box(modifier = modifier)
        } else {
            var currentStrokeIndex by remember { mutableIntStateOf(-1) }
            val strokePaths = remember(hanziData) {
                hanziData.strokes.map { strokeStr ->
                    PathParser().parsePathString(strokeStr).toPath()
                }
            }

            LaunchedEffect(isPlaying, char) {
                if (isPlaying) {
                    for (i in 0 until strokePaths.size) {
                        currentStrokeIndex = i
                        kotlinx.coroutines.delay(800)
                    }
                    onAnimationComplete()
                } else {
                    currentStrokeIndex = -1
                }
            }

            BoxWithConstraints(modifier = modifier) {
                val density = LocalDensity.current
                val width = with(density) { maxWidth.toPx() }
                val height = with(density) { maxHeight.toPx() }
                val canvasSize = if (width < height) width else height
                val s = canvasSize / 1024f
                val offsetX = (width - canvasSize) / 2f
                val offsetY = (height - canvasSize) / 2f

                Canvas(modifier = Modifier.fillMaxSize()) {
                    withTransform({
                        // 还原变换：数据是 Y 轴向上坐标系。
                        // 修正：引入 120f 的垂直位移补偿，将汉字向上抬升，实实现田字格内居中
                        val centeringVOffset = 120f * s
                        translate(left = offsetX, top = offsetY + canvasSize - centeringVOffset)
                        scale(scaleX = s, scaleY = -s, pivot = Offset.Zero)
                    }) {
                        // 画出背景轮廓（淡红色底图）
                        val baseColor = Color(0xFFEF5350)
                        strokePaths.forEach { path ->
                            drawPath(
                                path = path,
                                color = baseColor.copy(alpha = 0.4f * alpha),
                                style = androidx.compose.ui.graphics.drawscope.Fill
                            )
                        }
             
                        // 画出当前及之前的笔画（红色）
                        if (currentStrokeIndex >= 0) {
                            for (i in 0..currentStrokeIndex) {
                                if (i < strokePaths.size) {
                                    drawPath(
                                        path = strokePaths[i],
                                        color = Color(0xFFD32F2F).copy(alpha = alpha),
                                        style = androidx.compose.ui.graphics.drawscope.Fill
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

