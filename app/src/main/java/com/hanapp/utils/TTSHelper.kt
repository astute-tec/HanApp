package com.hanapp.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TTSHelper(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var retryCount = 0

    init {
        initializeTTS()
    }

    private fun initializeTTS() {
        tts?.shutdown()
        
        android.util.Log.i("TTSHelper", "Initializing TTS with Sherpa-ONNX engine...")
        
        try {
            // 强制指定 Sherpa-ONNX 引擎包名，确保不被系统默认设置干扰
            val sherpaEngine = "com.k2fsa.sherpa.onnx.tts.engine"
            tts = TextToSpeech(context, this, sherpaEngine)
        } catch (e: Exception) {
            android.util.Log.e("TTSHelper", "Critical: Failed to create TextToSpeech with Sherpa: ${e.message}")
            // 降级使用默认引擎
            tts = TextToSpeech(context, this)
        }
    }

    override fun onInit(status: Int) {
        val currentEngine = tts?.defaultEngine ?: "unknown"
        android.util.Log.i("TTSHelper", "onInit status: $status, Current Engine: $currentEngine")
        
        if (status == TextToSpeech.SUCCESS) {
            setupLanguage()
        } else {
            android.util.Log.e("TTSHelper", "TTS Initialization failed with status: $status")
        }
    }

    private fun setupLanguage() {
        val locale = Locale.CHINA
        val result = tts?.setLanguage(locale)
        android.util.Log.d("TTSHelper", "Setting language to Locale.CHINA, result code: $result")
        
        if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
            isReady = true
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)
            tts?.setSpeechRate(0.85f)
            android.util.Log.d("TTSHelper", "TTS Ready for Locale.CHINA")
        } else {
            val resultAlt = tts?.setLanguage(Locale.CHINESE)
            android.util.Log.d("TTSHelper", "Fallback to Locale.CHINESE, result code: $resultAlt")
            
            if (resultAlt != TextToSpeech.LANG_MISSING_DATA && resultAlt != TextToSpeech.LANG_NOT_SUPPORTED) {
                isReady = true
                android.util.Log.d("TTSHelper", "TTS Ready for Locale.CHINESE")
            } else if (retryCount < 2) {
                retryCount++
                android.util.Log.w("TTSHelper", "Language set failed ($result). Retrying in 3s... (attempt $retryCount)")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    setupLanguage()
                }, 3000)
            } else {
                android.util.Log.e("TTSHelper", "All Chinese locales failed. The engine may not have voice data downloaded.")
            }
        }
    }

    fun speak(text: String) {
        if (isReady && !text.isNullOrBlank()) {
            val params = android.os.Bundle()
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "HanAppTTS_${System.currentTimeMillis()}")
            android.util.Log.d("TTSHelper", "Speaking: $text")
        } else {
            android.util.Log.e("TTSHelper", "TTS not ready or text empty. isReady=$isReady")
            if (!isReady && retryCount < 2) {
                // 如果还没准备好，尝试重新触发一下初始化
                initializeTTS()
            }
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
