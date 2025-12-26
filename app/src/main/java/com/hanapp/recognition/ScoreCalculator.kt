package com.hanapp.recognition

class ScoreCalculator {
    /**
     * 计算评分 (0-10分)
     * @param expectedChar 期望的汉字
     * @param recognizedChars 识别出的汉字列表
     * @param userInk 用户书写的实际墨迹
     * @return 分数
     */
    data class ScoreResult(val score: Int, val isWrongOrder: Boolean)

    fun calculateScoreWithOrder(
        expectedChar: String, 
        recognizedChars: List<String>, 
        userInk: com.google.mlkit.vision.digitalink.Ink,
        medians: List<List<List<Int>>>? = null
    ): ScoreResult {
        if (recognizedChars.isEmpty()) return ScoreResult(0, false)
        
        // 1. 基础识别校验
        val isRecognized = recognizedChars.contains(expectedChar)
        if (!isRecognized) return ScoreResult((0..3).random(), false)

        val userStrokes = userInk.strokes
        val expectedStrokeCount = medians?.size ?: 0
        
        // 2. 笔画数硬校验：如果笔画数差大于等于 2，直接低分
        if (expectedStrokeCount > 0 && Math.abs(userStrokes.size - expectedStrokeCount) >= 2) {
            return ScoreResult(4, false)
        }

        // 3. 几何匹配度采样评分 (关键优化)
        var totalDeviation = 0f
        var sampledPoints = 0
        var wrongOrder = false

        if (medians != null && userStrokes.size == medians.size) {
            for (i in userStrokes.indices) {
                val userStroke = userStrokes[i]
                val expectedMedian = medians[i]
                
                // 采样点：起点、中点、终点
                val sampleIndices = if (userStroke.points.size >= 3) {
                    listOf(0, userStroke.points.size / 2, userStroke.points.size - 1)
                } else {
                    listOf(0, userStroke.points.size - 1)
                }

                // 标准笔画的参考点 (medians 数据是 [x, y] 格式)
                val expectedPoints = if (expectedMedian.size >= 3) {
                    listOf(expectedMedian[0], expectedMedian[expectedMedian.size / 2], expectedMedian.last())
                } else {
                    listOf(expectedMedian[0], expectedMedian.last())
                }

                for (idx in sampleIndices.indices) {
                    val uPoint = userStroke.points[sampleIndices[idx]]
                    val eRaw = expectedPoints[idx]
                    
                    // 坐标转换：中文字体库坐标是 Y 轴向上，1024 空间
                    // 修正：应用 120f 垂直补偿（向上移，所以减去 120）
                    val ex = eRaw[0].toFloat()
                    val ey = (1024 - eRaw[1]).toFloat() - 120f

                    val dx = uPoint.x - ex
                    val dy = uPoint.y - ey
                    val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    
                    totalDeviation += dist
                    sampledPoints++

                    // 笔顺/位置偏差判定：首点偏差过大判为笔顺有误
                    if (idx == 0 && dist > 300f) {
                        wrongOrder = true
                    }
                }
            }
        }

        // 4. 最终分值计算
        // 基础分 6 分 (只要识别对及笔画数接近)
        var score = 6 
        
        // 质量分 (基于平均偏移量，0-3 分)
        if (sampledPoints > 0) {
            val avgDist = totalDeviation / sampledPoints
            val qualityBonus = when {
                avgDist < 80f -> 3   // 写的非常准
                avgDist < 150f -> 2  // 写的还不错
                avgDist < 250f -> 1  // 略微有些歪
                else -> 0
            }
            score += qualityBonus
        }

        // 候选词权重 (+1 分)
        if (recognizedChars[0] == expectedChar && score < 10) {
            score += 1
        }
        
        // 关键逻辑：如果笔画数还没写够，即使识别出了汉字，也不给予及格分 (最高5分)
        // 这能防止系统在用户还没写完最后一笔前就过早触发成功逻辑
        if (expectedStrokeCount > 0 && userStrokes.size < expectedStrokeCount) {
            score = score.coerceAtMost(5)
        }

        // 笔顺扣分 (-1 分)
        if (wrongOrder) {
            score = (score - 1).coerceAtLeast(0) // 如果已经因为笔画数少被压到5分，再扣就4分
        }

        return ScoreResult(score.coerceIn(0, 10), wrongOrder)
    }

    // 保持向下兼容
    fun calculateScore(expectedChar: String, recognizedChars: List<String>, userInk: com.google.mlkit.vision.digitalink.Ink): Int {
        return calculateScoreWithOrder(expectedChar, recognizedChars, userInk).score
    }

    fun isPass(score: Int): Boolean = score >= 6
}
