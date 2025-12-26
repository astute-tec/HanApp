package com.hanapp.utils

import android.annotation.SuppressLint
import android.media.*
import android.os.Process
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

class AudioManager {
    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var recordingThread: Thread? = null

    private val _amplitudes = MutableStateFlow<List<Float>>(emptyList())
    val amplitudes: StateFlow<List<Float>> = _amplitudes

    private val recordedData = mutableListOf<Byte>()

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording.get()) return
        
        recordedData.clear()
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            BUFFER_SIZE
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("AudioManager", "AudioRecord initialization failed")
            return
        }

        isRecording.set(true)
        audioRecord?.startRecording()

        recordingThread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buffer = ShortArray(BUFFER_SIZE / 2)
            while (isRecording.get()) {
                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (readSize > 0) {
                    // 计算 RMS 音量用于波形显示
                    var sum = 0.0
                    for (i in 0 until readSize) {
                        sum += (buffer[i] * buffer[i]).toDouble()
                        // 转换为字节存入 recordedData
                        val sample = buffer[i]
                        recordedData.add((sample.toInt() and 0xFF).toByte())
                        recordedData.add(((sample.toInt() shr 8) and 0xFF).toByte())
                    }
                    val rms = sqrt(sum / readSize)
                    val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                    
                    // 更新波形数据流（保留最近 30 个点）
                    val currentList = _amplitudes.value.toMutableList()
                    currentList.add(normalized)
                    if (currentList.size > 30) currentList.removeAt(0)
                    _amplitudes.value = currentList
                }
            }
        }.apply { start() }
    }

    fun stopRecording(): ByteArray {
        isRecording.set(false)
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recordingThread = null
        return recordedData.toByteArray()
    }

    fun playPcm(data: ByteArray) {
        if (data.isEmpty()) return
        
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AUDIO_FORMAT
        )
        
        val audioTrack = AudioTrack(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AUDIO_FORMAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            data.size.coerceAtLeast(minBufferSize),
            AudioTrack.MODE_STATIC,
            android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        
        audioTrack.write(data, 0, data.size)
        audioTrack.play()
        
        // 播放完后自动释放（静态模式下需要注意生命周期）
        // 这里为了简单，仅示例逻辑。实际开发建议使用 AudioTrack 监听或协程等待
    }
}
