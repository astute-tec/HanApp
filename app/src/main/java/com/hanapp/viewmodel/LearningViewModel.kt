package com.hanapp.viewmodel

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
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
    
    // 统计相关状态
    private val _totalTime = mutableLongStateOf(0L) // 总时长（秒）
    val totalTime: State<Long> = _totalTime
    
    private val _dailyTime = mutableLongStateOf(0L) // 今日时长（秒）
    val dailyTime: State<Long> = _dailyTime
    
    private val _dailyCharCount = mutableIntStateOf(0) // 今日学字量
    val dailyCharCount: State<Int> = _dailyCharCount
    
    private var sessionStartTime = System.currentTimeMillis()
    private var lastSaveTime = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            // 1. 确保数据导入完成
            repository.importDataFromAssets(application)
            
            // 2. 监听用户状态变化（核心：年级切换自动触发刷新）
            userRepository.getUserFlow(userId).collectLatest { user ->
                if (user != null) {
                    _totalTime.longValue = user.totalLearningTime
                    _score.value = user.totalPoints // 确保显示的是数据库中的总分
                    // 当用户信息变化时（如从登录页切回且由于 VM 复用导致没跑 init）
                    updateProgressLists()
                    // 如果当前没字，尝试加载一个
                    if (_currentCharacter.value == null) {
                        loadNextCharacter()
                    }
                }
            }
        }

        // 3. 统计定时器
        viewModelScope.launch {
            while (true) {
                delay(10000)
                val now = System.currentTimeMillis()
                val deltaSec = (now - sessionStartTime) / 1000
                _dailyTime.longValue = deltaSec
                
                if (now - lastSaveTime > 60000) {
                    saveTotalTime()
                    lastSaveTime = now
                }
            }
        }

        // 4. 波形监听
        viewModelScope.launch {
            audioManager.amplitudes.collectLatest {
                _amplitudes.value = it
            }
        }
    }
    
    private fun saveTotalTime() {
        viewModelScope.launch {
            userRepository.getUserById(userId)?.let { user ->
                val now = System.currentTimeMillis()
                val sessionSec = (now - sessionStartTime) / 1000
                // 我们不直接重写 sessionStartTime，而是累加并更新 lastSaveTime
                // 下面代码采用累加逻辑
                val updatedTotal = user.totalLearningTime + (now - lastSaveTime) / 1000
                userRepository.updateUser(user.copy(totalLearningTime = updatedTotal))
                _totalTime.longValue = updatedTotal
            }
        }
    }

    private suspend fun updateProgressLists() {
        val user = userRepository.getUserById(userId)
        val grade = user?.currentGrade ?: 1
        val allChars = characterDao.getAllCharactersByGrade(grade)
        val allProgress = userRepository.getUserTotalProgress(userId)
        val progressMap = allProgress.associateBy { it.characterId }
        
        // 计算今日字数 (lastModified 在今天零点之后且得分过关)
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        _dailyCharCount.intValue = allProgress.count { it.lastModified >= todayStart && it.lastScore >= 6 }

        _learnedCharacters.value = allChars.filter { char -> 
            (progressMap[char.id]?.lastScore ?: 0) >= 6 
        }.sortedByDescending { progressMap[it.id]?.lastModified ?: 0 }

        _pendingCharacters.value = allChars.filter { char -> 
            (progressMap[char.id]?.lastScore ?: 0) < 6 
        }.sortedBy { it.id }
    }

    fun loadNextCharacter() {
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
                val user = userRepository.getUserById(userId)
                val grade = user?.currentGrade ?: 1
                charToLoad = characterDao.getFirstCharacterByGrade(grade)
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
            
            val word = if (char.exampleWords.isNotEmpty()) "（${char.exampleWords[0]}）" else ""
            _feedbackMessage.value = if (isReview) "回味一下 '${char.id}' $word 怎么写吧！" else "快来写写这个 '${char.id}' $word 字吧！"
            if ((progress?.lastScore ?: 0) > 0 && !isReview) {
                _feedbackMessage.value = "上次拿了 ${progress?.lastScore} 分，尝试突破自己吗？"
            }
        }
        isHandlingSuccess = false
    }

    fun clearHistoryInk() {
        _currentCharacter.value?.let { char ->
            viewModelScope.launch {
                val progress = userRepository.getProgress(userId, char.id)
                val updatedProgress = (progress ?: UserProgress(userId, char.id)).copy(
                    lastScore = 0,
                    lastWritingInk = null,
                    lastModified = System.currentTimeMillis()
                )
                userRepository.saveProgress(updatedProgress)
                _currentHistoryInk.value = null
                updateProgressLists()
            }
        }
    }

    fun startDemo() { _isShowingDemo.value = true }
    fun onDemoComplete() { _isShowingDemo.value = false }

    // 语音交互方法
    fun pronounceCurrentChar() {
        _currentCharacter.value?.let { char ->
            viewModelScope.launch {
                _isSpeaking.value = true
                tts.speak(getPronunciationText(char))
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
                        val progress = userRepository.getProgress(userId, target)
                        val oldStars = progress?.pronunciationScore ?: 0
                        
                        if (stars > oldStars) {
                            val newBonus = (stars - oldStars) * 2
                            _score.value += newBonus
                            _feedbackMessage.value = "读得真好！获得了 $stars 颗星，由于新记录奖励 $newBonus 分！"
                            
                            // 更新用户进度和积分
                            userRepository.saveProgress(
                                (progress ?: UserProgress(userId, target)).copy(
                                    pronunciationScore = stars,
                                    lastModified = System.currentTimeMillis()
                                )
                            )
                            userRepository.getUserById(userId)?.let { user ->
                                userRepository.updateUser(user.copy(totalPoints = user.totalPoints + newBonus))
                            }
                        } else {
                            val reward = REWARD_PHRASES.random()
                            _feedbackMessage.value = "读得很好！（这次拿了 $stars 颗星，由于没破纪录，就不重复给分啦）"
                            tts.speak(reward)
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
                    val progress = userRepository.getProgress(userId, targetChar)
                    val oldHighScore = progress?.highScore ?: 0
                    
                    if (resultScore > oldHighScore) {
                        val newPoints = resultScore - oldHighScore
                        _score.value += newPoints
                        _feedbackMessage.value = when {
                            resultScore >= 10 -> "入木三分！破纪录奖励 $newPoints 分！"
                            else -> "更上一层楼！奖励 $newPoints 分！"
                        }
                        
                        userRepository.getUserById(userId)?.let { user ->
                            userRepository.updateUser(user.copy(totalPoints = user.totalPoints + newPoints))
                        }
                    } else {
                        _feedbackMessage.value = "写得不错！（这次得了 $resultScore 分，继续努力破纪录吧！）"
                    }

                    if (resultScore >= 8) _showFireworks.value = true
                } else {
                    _feedbackMessage.value = "最后一次写得真漂亮！"
                }
                
                if (result.isWrongOrder) {
                    tts.speak("笔顺不太对哦，下次要按顺序写呢。")
                    delay(1500)
                }
                
                val pronunciationText = getPronunciationText(current)
                _feedbackMessage.value = pronunciationText
                tts.speak(pronunciationText)
                // 增加延迟，确保特别是成语等长词能读完再进行下一步
                delay(4500) 
                if (!isReview) {
                    val reward = REWARD_PHRASES.random()
                    _feedbackMessage.value = reward
                    tts.speak(reward)
                    delay(1500)
                }
                
                val inkJson = serializeInk(ink)
                val currentProgress = userRepository.getProgress(userId, targetChar)
                val updatedProgress = (currentProgress ?: UserProgress(userId, targetChar)).copy(
                    lastWritingInk = inkJson,
                    lastScore = resultScore,
                    highScore = maxOf(currentProgress?.highScore ?: 0, resultScore),
                    lastModified = System.currentTimeMillis()
                )
                userRepository.saveProgress(updatedProgress)
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

    private fun getPronunciationText(character: Character): String {
        val word = if (character.exampleWords.isNotEmpty()) {
            character.exampleWords[0]
        } else {
            FALLBACK_WORDS[character.id] ?: character.id
        }
        
        return when {
            word == character.id -> character.id
            // 如果是诗句（通常较长且含有标点或空格，或者字数>4）
            word.length > 4 -> "${character.id}，“$word”的${character.id}"
            // 如果是成语或组词
            word.contains(character.id) -> "${character.id}，$word"
            else -> "${character.id}，比如$word"
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts.shutdown()
    }

    companion object {
        private val REWARD_PHRASES = listOf(
            "你真棒！",
            "太厉害了！",
            "写的真漂亮！",
            "简直是小书法家！",
            "进步真大，为你点赞！",
            "太完美了，继续加油！",
            "你真是一门心思学好汉字！",
            "你的字越来越有灵气了！",
            "卓越的表现，你是最棒的！",
            "看你的字真是一种享受！",
            "哇，写的太工整了！",
            "你真是个汉字小达人！"
        )

        private val FALLBACK_WORDS = mapOf(
            "一" to "一心一意", "二" to "二龙戏珠", "三" to "三省吾身", "十" to "十全十美",
            "上" to "蒸蒸日上", "下" to "下笔成章", "左" to "左右逢源", "右" to "无出其右",
            "中" to "中流砥柱", "大" to "大展宏图", "小" to "小巧玲珑", "人" to "人杰地灵",
            "天" to "海阔天空", "地" to "地大物博", "日" to "日新月异", "月" to "海上生明月",
            "山" to "山高水长", "水" to "上善若水", "火" to "星星之火", "木" to "木秀于林",
            "手" to "妙手回春", "口" to "口若悬河", "耳" to "耳濡目染", "目" to "目不暇接",
            "头" to "头角峥嵘", "米" to "谁知盘中餐，粒粒皆辛苦", "花" to "鸟语花香", "鸟" to "笨鸟先飞",
            "鱼" to "鱼跃龙门", "虫" to "雕虫小技", "云" to "云淡风轻", "雨" to "风调雨顺",
            "风" to "春风化雨", "雪" to "雪中送炭", "春" to "春华秋实", "夏" to "夏炉冬扇",
            "秋" to "一叶知秋", "冬" to "冬日夏云", "爸" to "父爱如山", "妈" to "母爱如水",
            "我" to "忘我奋斗", "你" to "你好", "他" to "他山之石", "好" to "好学不倦",
            "见" to "见微知著", "开" to "开卷有益", "关" to "关怀备至", "来" to "继往开来",
            "去" to "去伪存真", "坐" to "坐怀不乱", "走" to "走马观花", "听" to "兼听则明",
            "说" to "说一不二", "读" to "读万卷书", "写" to "妙笔生花", "看" to "看破红尘",
            "爱" to "爱屋及乌", "家" to "诗礼传家", "校" to "学校", "书" to "书山有路",
            "万" to "万紫千红", "紫" to "万紫千红", "红" to "万紫千红", "千" to "万紫千红",
            "举" to "举一反三", "反" to "举一反三", "什" to "什么", "么" to "什么",
            "学" to "学而不厌", "温" to "温故知新", "故" to "温故知新", "知" to "温故知新",
            "新" to "温故知新", "先" to "笨鸟先飞", "勤" to "勤能补拙", "拙" to "勤能补拙",
            "专" to "专心致志", "致" to "专心致志", "志" to "专心致志", "全" to "全神贯注",
            "神" to "全神贯注", "贯" to "全神贯注", "注" to "全神贯注", "融" to "融会贯通",
            "通" to "融会贯通", "止" to "学无止境", "诚" to "诚实守信", "信" to "诚实守信",
            "助" to "助人为乐", "勇" to "勇往直前", "正" to "正直无私", "宽" to "宽宏大量",
            "忠" to "忠心耿耿", "智" to "智勇双全", "足" to "足智多谋", "谋" to "足智多谋",
            "深" to "深谋远虑", "虑" to "深谋远虑", "明" to "明察秋毫", "察" to "明察秋毫",
            "分" to "争分夺秒", "秒" to "争分夺秒", "精" to "精益求精", "益" to "精益求精",
            "恒" to "持之以恒", "星" to "月明星稀", "稀" to "月明星稀", "箭" to "光阴似箭",
            "合" to "志同道合", "同" to "形影不离", "形" to "形影不离", "影" to "形影不离",
            "戒" to "戒骄戒躁", "躁" to "戒骄戒躁", "威" to "威风凛凛", "凛" to "威风凛凛",
            "盛" to "繁荣昌盛", "繁" to "繁荣昌盛", "荣" to "繁荣昌盛", "昌" to "繁荣昌盛",
            "盈" to "热泪盈眶", "眶" to "热泪盈眶", "澄" to "波光粼粼", "粼" to "波光粼粼",
            "滴" to "水滴石穿", "艘" to "一艘帆船", "帆" to "一艘帆船", "航" to "航向大海",
            "海" to "航向大海", "艺" to "多才多艺", "良" to "良师益友", "艰" to "艰苦奋斗",
            "奋" to "艰苦奋斗", "斗" to "艰苦奋斗", "凤" to "龙飞凤舞", "舞" to "龙飞凤舞",
            "凡" to "不同凡响", "处" to "处变不惊", "惊" to "处变不惊", "够" to "足够多",
            "美" to "美不胜收", "胜" to "美不胜收", "收" to "美不胜收", "画" to "如诗如画",
            "诗" to "如诗如画", "金" to "金榜题名", "榜" to "金榜题名", "题" to "金榜题名",
            "名" to "金榜题名"
        )
    }
}
