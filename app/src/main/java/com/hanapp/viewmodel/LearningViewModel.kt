package com.hanapp.viewmodel

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanapp.data.AppDatabase
import com.hanapp.data.model.Character
import com.hanapp.data.model.UserProgress
import com.hanapp.data.repository.CharacterRepository
import com.hanapp.data.repository.UserRepository
import com.hanapp.recognition.HandwritingRecognizer
import com.hanapp.recognition.ScoreCalculator
import com.google.mlkit.vision.digitalink.Ink
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.collectLatest
import com.hanapp.utils.AudioManager
import com.hanapp.utils.AudioScorer
import com.hanapp.ui.components.HanziDataHelper
import com.hanapp.ui.components.StrokeAnimationView
import com.hanapp.utils.TTSHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class LearningViewModel(application: Application, private val userId: Long) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val characterDao = db.characterDao()
    private val userDao = db.userDao()
    private val repository = CharacterRepository(characterDao)
    private val userRepository = UserRepository(userDao)
    
    private val recognizer = HandwritingRecognizer()
    private val scorer = ScoreCalculator()
    private val tts = TTSHelper(application)
    private val audioManager = AudioManager()
    private val audioScorer = AudioScorer()
    private val gson = Gson()
    private var evalJob: kotlinx.coroutines.Job? = null
    
    // 语音相关状态
    private val _isRecording = mutableStateOf(false)
    val isRecording: State<Boolean> = _isRecording

    private val _amplitudes = mutableStateOf<List<Float>>(emptyList())
    val amplitudes: State<List<Float>> = _amplitudes

    private val _lastPcmData = mutableStateOf<ByteArray?>(null)
    val lastPcmData: State<ByteArray?> = _lastPcmData

    private val _pronunciationStars = mutableStateOf(0)
    val pronunciationStars: State<Int> = _pronunciationStars

    private val _isSpeaking = mutableStateOf(false)
    val isSpeaking: State<Boolean> = _isSpeaking
    
    private val _isPlayingRecording = mutableStateOf(false)
    val isPlayingRecording: State<Boolean> = _isPlayingRecording
    
    private var isHandlingSuccess = false

    private val _currentCharacter = mutableStateOf<Character?>(null)
    val currentCharacter: State<Character?> = _currentCharacter

    private val _isShowingDemo = mutableStateOf(false)
    val isShowingDemo: State<Boolean> = _isShowingDemo

    private val _score = mutableStateOf(0)
    val score: State<Int> = _score

    private val _feedbackMessage = mutableStateOf("正在准备好玩的汉字...")
    val feedbackMessage: State<String> = _feedbackMessage

    private val _learnedCharacters = mutableStateOf<List<Character>>(emptyList())
    val learnedCharacters: State<List<Character>> = _learnedCharacters

    private val _pendingCharacters = mutableStateOf<List<Character>>(emptyList())
    val pendingCharacters: State<List<Character>> = _pendingCharacters

    private val _showFireworks = mutableStateOf(false)
    val showFireworks: State<Boolean> = _showFireworks

    private val _currentHistoryInk = mutableStateOf<Ink?>(null)
    val currentHistoryInk: State<Ink?> = _currentHistoryInk

    private val _isReviewMode = mutableStateOf(false)
    val isReviewMode: State<Boolean> = _isReviewMode

    init {
        viewModelScope.launch {
            repository.importDataFromAssets(application)
            updateProgressLists()
            loadNextCharacter()
            
            // 订阅波形数据
            launch {
                audioManager.amplitudes.collectLatest {
                    _amplitudes.value = it
                }
            }
        }
    }

    private suspend fun updateProgressLists() {
        val allChars = characterDao.getAllCharactersByGrade(1)
        val allProgress = userRepository.getUserTotalProgress(userId)
        val progressMap = allProgress.associateBy { it.characterId }

        _learnedCharacters.value = allChars.filter { char -> 
            (progressMap[char.id]?.lastScore ?: 0) >= 6 
        }.sortedByDescending { progressMap[it.id]?.lastModified ?: 0 }

        _pendingCharacters.value = allChars.filter { char -> 
            (progressMap[char.id]?.lastScore ?: 0) < 6 
        }.sortedBy { it.id }
    }

    private fun loadNextCharacter() {
        viewModelScope.launch {
            // 确保拿到最新的进度列表
            updateProgressLists()
            
            val currentId = _currentCharacter.value?.id
            // 从待学习列表中找到第一个不是当前字的
            var charToLoad = _pendingCharacters.value.find { it.id != currentId }
            
            if (charToLoad == null) {
                // 如果待学习列表空了或者只有当前这个，尝试按顺序找下一个
                charToLoad = _currentCharacter.value?.let { current ->
                    characterDao.getNextCharacter(current.id, current.grade)
                } 
            }
            
            // 如果还是没有（说明是最后一关了），则循环回第一个
            if (charToLoad == null) {
                charToLoad = characterDao.getFirstCharacterByGrade(1)
            }

            charToLoad?.let {
                selectCharacter(it, isReview = false)
            } ?: run {
                _feedbackMessage.value = "太棒了！你已经完成了所有关卡！"
            }
        }
    }

    fun selectCharacter(char: Character, isReview: Boolean = false) {
        _currentCharacter.value = char
        _isReviewMode.value = isReview
        _isShowingDemo.value = false
        
        // 清除上一个字符的录音数据
        _lastPcmData.value = null
        _pronunciationStars.value = 0
        
        viewModelScope.launch {
            val progress = userRepository.getProgress(userId, char.id)
            _currentHistoryInk.value = progress?.lastWritingInk?.let { deserializeInk(it) }
            
            _feedbackMessage.value = if (isReview) "回味一下 '${char.id}' 怎么写吧！" else "快来写写这个 '${char.id}' 字吧！"
            if ((progress?.lastScore ?: 0) > 0 && !isReview) {
                _feedbackMessage.value = "上次拿了 ${progress?.lastScore} 分，尝试突破自己吗？"
            }
        }
        isHandlingSuccess = false
    }

    fun clearHistoryInk() {
        _currentCharacter.value?.let { char ->
            viewModelScope.launch {
                val progress = UserProgress(userId, char.id, lastScore = 0, lastModified = System.currentTimeMillis())
                userRepository.saveProgress(progress)
                _currentHistoryInk.value = null
                updateProgressLists()
            }
        }
    }

    fun startDemo() { _isShowingDemo.value = true }
    fun onDemoComplete() { _isShowingDemo.value = false }

    // 语音交互方法
    fun pronounceCurrentChar() {
        _currentCharacter.value?.id?.let {
            viewModelScope.launch {
                _isSpeaking.value = true
                tts.speak(it)
                delay(1000)
                _isSpeaking.value = false
            }
        }
    }

    fun startRecording() {
        _isRecording.value = true
        _pronunciationStars.value = 0
        _feedbackMessage.value = "正在录音..."
        
        // 启动AudioManager录音
        audioManager.startRecording()
    }

    fun stopRecording() {
        if (_isRecording.value) {
            _isRecording.value = false
            
            // 获取录音数据并保存
            val pcm = audioManager.stopRecording()
            _lastPcmData.value = pcm
            
            // 使用音频特征评分
            _currentCharacter.value?.id?.let { target ->
                viewModelScope.launch {
                    _feedbackMessage.value = "正在分析你的发音..."
                    val stars = audioScorer.assessAudio(pcm, target)
                    _pronunciationStars.value = stars
                    
                    if (stars > 0) {
                        val bonusPoints = stars * 2
                        _score.value += bonusPoints
                        _feedbackMessage.value = "读得真好！获得了 $stars 颗星，奖励 $bonusPoints 分！"
                        
                        // 同步更新数据库总积分
                        userRepository.getUserById(userId)?.let { user ->
                            userRepository.updateUser(user.copy(totalPoints = user.totalPoints + bonusPoints))
                        }
                    } else {
                        _feedbackMessage.value = "声音太小了，大声一点试试？"
                    }
                }
            }
        }
    }

    fun playChildRecording() {
        _lastPcmData.value?.let { pcm ->
            viewModelScope.launch {
                _isPlayingRecording.value = true
                audioManager.playPcm(pcm)
                // 等待播放完成（估算时长：PCM数据大小 / 采样率 / 字节数）
                val durationMs = (pcm.size / 2 / 16).toLong() // 16kHz, 16bit
                delay(durationMs)
                _isPlayingRecording.value = false
            }
        }
    }


    fun submitWriting(ink: com.google.mlkit.vision.digitalink.Ink) {
        val current = _currentCharacter.value ?: return
        val targetChar = current.id
        val isReview = _isReviewMode.value
        
        if (ink.strokes.isEmpty()) {
            evalJob?.cancel()
            return
        }
        
        evalJob?.cancel()
        evalJob = viewModelScope.launch {
            delay(1500)
            
            val candidates = recognizeInk(ink)
            val hanziData = com.hanapp.ui.components.HanziDataHelper.loadHanziXmlData(getApplication(), targetChar)
            val result = scorer.calculateScoreWithOrder(targetChar, candidates, ink, hanziData?.medians)
            val resultScore = result.score
            val isPass = scorer.isPass(resultScore)
            
            if (isPass) {
                if (isHandlingSuccess) return@launch
                isHandlingSuccess = true

                if (!isReview) {
                    _score.value += resultScore
                    if (resultScore >= 8) _showFireworks.value = true
                    
                    userRepository.getUserById(userId)?.let { user ->
                        userRepository.updateUser(user.copy(totalPoints = user.totalPoints + resultScore))
                    }
                }
                
                _feedbackMessage.value = when {
                    isReview -> "最后一次写得真漂亮！"
                    result.isWrongOrder -> "字写对了，但笔顺不对哦（扣1分）"
                    resultScore >= 10 -> "入木三分，极品作！"
                    else -> "很有灵气，得了 $resultScore 分！"
                }
                
                if (result.isWrongOrder) {
                    tts.speak("笔顺不太对哦，下次要按顺序写呢。")
                    delay(1500)
                }
                
                tts.speak(targetChar)
                delay(1200)
                tts.speak(targetChar)
                delay(2000)
                if (!isReview) tts.speak("你真棒！")
                
                val inkJson = serializeInk(ink)
                val progress = UserProgress(
                    userId = userId,
                    characterId = targetChar,
                    lastWritingInk = inkJson,
                    lastScore = resultScore,
                    lastModified = System.currentTimeMillis()
                )
                userRepository.saveProgress(progress)
                updateProgressLists()
                
                if (!isReview) {
                    delay(1000) 
                    _showFireworks.value = false
                    loadNextCharacter()
                } else {
                    delay(1500)
                    _showFireworks.value = false
                    _feedbackMessage.value = "这个字写得真棒！还要再练习一下吗？"
                }
            } else {
                _feedbackMessage.value = "这个字写得不太对哦，加油再试一次！"
                tts.speak("要细心一点哦，加把劲！")
            }
        }
    }

    private suspend fun recognizeInk(ink: Ink): List<String> = 
        suspendCancellableCoroutine { continuation ->
            recognizer.recognize(ink) { candidates ->
                if (continuation.isActive) {
                    continuation.resume(candidates)
                }
            }
        }

    private fun serializeInk(ink: Ink): String {
        val data = ink.strokes.map { stroke ->
            stroke.points.map { point -> mapOf("x" to point.x, "y" to point.y) }
        }
        return gson.toJson(data)
    }

    private fun deserializeInk(json: String): Ink? {
        return try {
            val type = object : TypeToken<List<List<Map<String, Float>>>>() {}.type
            val data: List<List<Map<String, Float>>> = gson.fromJson(json, type)
            val builder = Ink.builder()
            data.forEach { strokeData ->
                val strokeBuilder = Ink.Stroke.builder()
                strokeData.forEach { p ->
                    strokeBuilder.addPoint(Ink.Point.create(p["x"]!!, p["y"]!!, System.currentTimeMillis()))
                }
                builder.addStroke(strokeBuilder.build())
            }
            builder.build()
        } catch (e: Exception) { null }
    }

    override fun onCleared() {
        super.onCleared()
        tts.shutdown()
    }
}
