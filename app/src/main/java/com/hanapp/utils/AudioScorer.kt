package com.hanapp.utils

import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 基于音频特征的发音评分器
 * 用于评估儿童发音质量（当无法使用语音识别时的备选方案）
 */
class AudioScorer {
    
    /**
     * 分析PCM音频数据并给出评分
     * @param pcmData PCM 16-bit音频数据
     * @param targetChar 目标汉字（暂不影响评分，预留扩展）
     * @return 星级 0-3
     */
    fun assessAudio(pcmData: ByteArray, targetChar: String): Int {
        if (pcmData.isEmpty()) {
            Log.w("AudioScorer", "音频数据为空")
            return 0
        }
        
        // 转换为样本值
        val samples = FloatArray(pcmData.size / 2)
        for (i in samples.indices) {
            val low = pcmData[i * 2].toInt() and 0xFF
            val high = pcmData[i * 2 + 1].toInt()
            val sample = ((high shl 8) or low).toShort()
            samples[i] = sample / 32768.0f
        }
        
        // 计算音频特征
        val duration = samples.size / 16000.0f // 16kHz采样率，单位：秒
        val avgEnergy = calculateAverageEnergy(samples)
        val maxEnergy = calculateMaxEnergy(samples)
        val silenceRatio = calculateSilenceRatio(samples)
        
        Log.d("AudioScorer", "时长: ${duration}s, 平均能量: $avgEnergy, 最大能量: $maxEnergy, 静音比: $silenceRatio")
        
        return calculateScore(duration, avgEnergy, maxEnergy, silenceRatio)
    }
    
    /**
     * 计算平均能量
     */
    private fun calculateAverageEnergy(samples: FloatArray): Float {
        var totalEnergy = 0.0
        for (sample in samples) {
            totalEnergy += abs(sample.toDouble())
        }
        return (totalEnergy / samples.size).toFloat()
    }
    
    /**
     * 计算最大能量
     */
    private fun calculateMaxEnergy(samples: FloatArray): Float {
        var maxEnergy = 0.0f
        for (sample in samples) {
            val energy = abs(sample)
            if (energy > maxEnergy) {
                maxEnergy = energy
            }
        }
        return maxEnergy
    }
    
    /**
     * 计算静音比例
     */
    private fun calculateSilenceRatio(samples: FloatArray): Float {
        val threshold = 0.02f // 静音阈值
        var silenceCount = 0
        
        for (sample in samples) {
            if (abs(sample) < threshold) {
                silenceCount++
            }
        }
        
        return silenceCount.toFloat() / samples.size
    }
    
    /**
     * 根据音频特征计算评分
     */
    private fun calculateScore(
        duration: Float,
        avgEnergy: Float,
        maxEnergy: Float,
        silenceRatio: Float
    ): Int {
        var score = 0
        
        // 1. 检查是否有效发声（平均能量）
        when {
            avgEnergy < 0.01f -> {
                // 几乎没有声音
                Log.d("AudioScorer", "声音太小")
                return 0
            }
            avgEnergy >= 0.05f -> {
                // 声音洪亮
                score += 2
                Log.d("AudioScorer", "声音洪亮 +2")
            }
            avgEnergy >= 0.03f -> {
                // 声音适中
                score += 1
                Log.d("AudioScorer", "声音适中 +1")
            }
        }
        
        // 2. 检查录音时长（单个汉字应该0.3-3秒）
        when {
            duration < 0.3f -> {
                Log.d("AudioScorer", "时长太短，可能没说完整")
                score -= 1
            }
            duration > 4.0f -> {
                Log.d("AudioScorer", "时长太长")
                score -= 1
            }
            duration in 0.5f..2.0f -> {
                // 时长合适
                score += 1
                Log.d("AudioScorer", "时长合适 +1")
            }
        }
        
        // 3. 检查静音比例（不应该太多停顿）
        when {
            silenceRatio > 0.7f -> {
                Log.d("AudioScorer", "静音太多")
                score -= 1
            }
            silenceRatio < 0.3f -> {
                // 连贯发音
                Log.d("AudioScorer", "发音连贯")
            }
        }
        
        // 4. 检查峰值能量（是否有清晰的发音高峰）
        if (maxEnergy > 0.3f) {
            score += 1
            Log.d("AudioScorer", "有清晰发音峰值 +1")
        }
        
        // 限制在0-3星范围
        val finalScore = score.coerceIn(0, 3)
        
        Log.i("AudioScorer", "最终评分: $finalScore 星")
        return finalScore
    }
}
