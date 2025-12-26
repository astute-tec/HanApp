package com.hanapp.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 基于Android系统SpeechRecognizer的轻量级语音识别工具
 * 用于评估儿童对汉字的发音准确度
 */
class SimpleSpeechRecognizer(private val context: Context) {
    
    private var speechRecognizer: SpeechRecognizer? = null
    
    /**
     * 识别录音中的中文文字
     * @param audioData PCM音频数据（暂不使用，改用实时麦克风录音）
     * @param targetChar 目标汉字
     * @return 星级评分 0-3
     */
    suspend fun recognizeAndAssess(targetChar: String): Int = suspendCancellableCoroutine { continuation ->
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e("SimpleSpeechRecognizer", "语音识别不可用")
            if (continuation.isActive) continuation.resume(0)
            return@suspendCancellableCoroutine
        }
        
        // 先释放旧的识别器
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            // 等待一小段时间确保资源释放
            Thread.sleep(100)
        } catch (e: Exception) {
            Log.w("SimpleSpeechRecognizer", "清理旧识别器失败: ${e.message}")
        }
        
        // 创建新的识别器
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        
        val recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
        }
        
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SimpleSpeechRecognizer", "准备接收语音")
            }
            
            override fun onBeginningOfSpeech() {
                Log.d("SimpleSpeechRecognizer", "开始说话")
            }
            
            override fun onRmsChanged(rmsdB: Float) {}
            
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {
                Log.d("SimpleSpeechRecognizer", "停止说话")
            }
            
            override fun onError(error: Int) {
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "音频错误"
                    SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                    SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                    SpeechRecognizer.ERROR_NO_MATCH -> "没有匹配"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
                    SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时"
                    else -> "未知错误"
                }
                Log.e("SimpleSpeechRecognizer", "识别错误: $errorMsg ($error)")
                if (continuation.isActive) continuation.resume(0)
            }
            
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                Log.d("SimpleSpeechRecognizer", "识别结果: $matches, 目标: $targetChar")
                
                val stars = assessPronunciation(matches, targetChar)
                if (continuation.isActive) continuation.resume(stars)
            }
            
            override fun onPartialResults(partialResults: Bundle?) {}
            
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        
        continuation.invokeOnCancellation {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
        
        // 添加超时保护，使用Handler替代协程
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (continuation.isActive) {
                Log.w("SimpleSpeechRecognizer", "识别超时")
                speechRecognizer?.cancel()
                continuation.resume(0)
            }
        }
        handler.postDelayed(timeoutRunnable, 8000) // 8秒超时
        
        try {
            speechRecognizer?.startListening(recognitionIntent)
        } catch (e: Exception) {
            handler.removeCallbacks(timeoutRunnable)
            Log.e("SimpleSpeechRecognizer", "启动识别失败: ${e.message}")
            if (continuation.isActive) continuation.resume(0)
        }
    }
    
    /**
     * 评估发音准确度
     */
    private fun assessPronunciation(matches: List<String>?, targetChar: String): Int {
        if (matches.isNullOrEmpty()) return 0
        
        // 检查第一个匹配结果
        val firstMatch = matches[0]
        
        return when {
            // 完全匹配，3星
            firstMatch == targetChar -> {
                Log.i("SimpleSpeechRecognizer", "完美匹配！")
                3
            }
            // 包含目标字，2星
            firstMatch.contains(targetChar) -> {
                Log.i("SimpleSpeechRecognizer", "部分匹配")
                2
            }
            // 在前3个结果中找到目标字，1星
            matches.take(3).any { it.contains(targetChar) } -> {
                Log.i("SimpleSpeechRecognizer", "识别到目标字，但不是第一匹配")
                1
            }
            // 完全不匹配，0星
            else -> {
                Log.i("SimpleSpeechRecognizer", "未匹配")
                0
            }
        }
    }
    
    /**
     * 停止识别
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
    }
    
    /**
     * 取消识别
     */
    fun cancel() {
        speechRecognizer?.cancel()
    }
    
    /**
     * 释放资源
     */
    fun release() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
