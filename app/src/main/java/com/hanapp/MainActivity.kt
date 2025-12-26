package com.hanapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.airbnb.lottie.compose.*
import com.google.mlkit.vision.digitalink.Ink
import com.hanapp.R
import com.hanapp.data.AppDatabase
import com.hanapp.ui.components.*
import com.hanapp.ui.theme.HanAppTheme
import com.hanapp.viewmodel.LearningViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 请求录音权限
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        
        setContent {
            HanAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hanApp = context.applicationContext as HanApp
    val userRepository = hanApp.userRepository
    
    val profileViewModel: com.hanapp.viewmodel.ProfileViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return com.hanapp.viewmodel.ProfileViewModel(userRepository) as T
            }
        }
    )
    
    var currentUser by remember { mutableStateOf<com.hanapp.data.model.User?>(null) }

    if (currentUser == null) {
        com.hanapp.ui.screens.LoginScreen(profileViewModel) { user ->
            currentUser = user
        }
    } else {
        LearningScreen(
            userId = currentUser!!.id,
            userName = currentUser!!.name,
            userAvatar = currentUser!!.avatar,
            onBackToLogin = { currentUser = null }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LearningScreen(
    userId: Long,
    userName: String,
    userAvatar: String,
    onBackToLogin: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: LearningViewModel = viewModel(
        key = "user_$userId",
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return LearningViewModel(context.applicationContext as android.app.Application, userId) as T
            }
        }
    )

    var clearTrigger by remember { mutableIntStateOf(0) }

    val character = viewModel.currentCharacter.value
    val isShowingDemo = viewModel.isShowingDemo.value
    val feedback = viewModel.feedbackMessage.value
    val score = viewModel.score.value

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth > 720.dp
        
        // 背景图片
        Image(
            painter = painterResource(id = R.drawable.pony_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.5f
        )

        Box(modifier = Modifier.fillMaxSize().padding(if (isWideScreen) 20.dp else 10.dp)) {
            if (isWideScreen) {
                // 平板模式
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.weight(3f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        UserHeader(
                            userName, 
                            userAvatar, 
                            feedback, 
                            score, 
                            viewModel.totalTime.value,
                            viewModel.dailyTime.value,
                            viewModel.dailyCharCount.value,
                            true, 
                            onBackToLogin
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            LearningCard(viewModel, character, isShowingDemo, clearTrigger, { clearTrigger++ }, true)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            AppButton("笔顺", MaterialTheme.colorScheme.secondary) { 
                                clearTrigger++
                                viewModel.startDemo() 
                            }
                            Spacer(modifier = Modifier.width(32.dp))
                             AppButton("重写", MaterialTheme.colorScheme.tertiary) { 
                                 clearTrigger++ 
                                 viewModel.clearHistoryInk()
                             }
                             Spacer(modifier = Modifier.width(32.dp))
                             AppButton("继续", MaterialTheme.colorScheme.primary) { 
                                 clearTrigger++
                                 viewModel.loadNextCharacter()
                             }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(24.dp))
                    ProgressSidebar(viewModel, userId, true, modifier = Modifier.weight(1.5f).fillMaxHeight())
                }
            } else {
                // 手机模式
                Column(modifier = Modifier.fillMaxSize()) {
                    UserHeader(
                        userName, 
                        userAvatar, 
                        feedback, 
                        score, 
                        viewModel.totalTime.value,
                        viewModel.dailyTime.value,
                        viewModel.dailyCharCount.value,
                        false, 
                        onBackToLogin
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        LearningCard(viewModel, character, isShowingDemo, clearTrigger, { clearTrigger++ }, false)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ProgressSidebar(viewModel, userId, false, modifier = Modifier.fillMaxWidth().height(150.dp))
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), 
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppButton(
                            "笔顺", 
                            MaterialTheme.colorScheme.secondary, 
                            modifier = Modifier.weight(1f)
                        ) { 
                            clearTrigger++
                            viewModel.startDemo() 
                        }
                        AppButton(
                            "重写", 
                            MaterialTheme.colorScheme.tertiary, 
                            modifier = Modifier.weight(1f)
                        ) { 
                             clearTrigger++ 
                             viewModel.clearHistoryInk()
                        }
                        AppButton(
                            "继续", 
                            MaterialTheme.colorScheme.primary, 
                            modifier = Modifier.weight(1f)
                        ) { 
                             clearTrigger++ 
                             viewModel.loadNextCharacter()
                        }
                    }
                }
            }
        }
        
        com.hanapp.ui.components.FireworksOverlay(isVisible = viewModel.showFireworks.value)
    }
}

@Composable
fun UserHeader(
    name: String, 
    avatar: String, 
    feedback: String, 
    score: Int, 
    totalSeconds: Long,
    dailySeconds: Long,
    dailyChars: Int,
    isWideScreen: Boolean, 
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val avatarId = context.resources.getIdentifier(avatar, "drawable", context.packageName)

    fun formatTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h}时${m}分" else "${m}分"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.8f), shape = RoundedCornerShape(20.dp))
            .padding(if (isWideScreen) 12.dp else 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = if(avatarId != 0) avatarId else R.drawable.pony_mascot),
                contentDescription = null,
                modifier = Modifier.size(if (isWideScreen) 80.dp else 60.dp).clip(CircleShape).clickable { onBack() }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "$name 的魔法屋", fontSize = 14.sp, color = Color.Gray)
                Text(text = feedback, fontSize = if (isWideScreen) 22.sp else 18.sp, color = Color(0xFF880E4F), fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "总积分", fontSize = 12.sp, color = Color.Gray)
                Text(text = "$score", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Divider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatItem("总时长", formatTime(totalSeconds + dailySeconds), isWideScreen)
            StatItem("今日时长", formatTime(dailySeconds), isWideScreen)
            StatItem("今日学字", "${dailyChars}个", isWideScreen)
        }
    }
}

@Composable
fun StatItem(label: String, value: String, isWideScreen: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "$label: ", fontSize = if(isWideScreen) 14.sp else 12.sp, color = Color.Gray)
        Text(text = value, fontSize = if(isWideScreen) 16.sp else 14.sp, color = Color(0xFFE91E63), fontWeight = FontWeight.Medium)
    }
}

@Composable
fun LearningCard(
    viewModel: LearningViewModel,
    character: com.hanapp.data.model.Character?,
    isShowingDemo: Boolean,
    clearTrigger: Int,
    onClear: () -> Unit,
    isWideScreen: Boolean
) {
    val isRecording = viewModel.isRecording.value
    val amplitudes = viewModel.amplitudes.value
    val stars = viewModel.pronunciationStars.value
    val isSpeaking = viewModel.isSpeaking.value
    val isPlayingRecording = viewModel.isPlayingRecording.value

    Row(
        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(if (isWideScreen) 20.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 田字格区域
        Card(
            modifier = if (isWideScreen) {
                Modifier
                    .fillMaxHeight(0.85f) // 限制高度占比，防止遮挡上下元素
                    .aspectRatio(1f)
            } else {
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
            },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TianZiGe(lineColor = Color(0xFFE91E63).copy(alpha = 0.6f), strokeWidth = 3f)
                
                DrawingBoard(
                    modifier = Modifier.fillMaxSize(),
                    char = character?.id,
                    historyInk = if (isShowingDemo) null else viewModel.currentHistoryInk.value,
                    clearTrigger = clearTrigger,
                    onInkChanged = { viewModel.submitWriting(it) },
                    strokeWidth = 40f, 
                    strokeColor = Color(0xFF212121)
                )

                if (isShowingDemo && character != null) {
                    StrokeAnimationView(
                        modifier = Modifier.fillMaxSize(),
                        char = character.id,
                        isPlaying = true,
                        alpha = 0.8f,
                        onAnimationComplete = { viewModel.onDemoComplete() }
                    )
                }
                
                // 录音时的波形图遮罩（可选，也可以放在按钮旁）
                if (isRecording) {
                    WaveformView(
                        amplitudes = amplitudes,
                        modifier = Modifier.fillMaxWidth(0.8f).height(60.dp).align(Alignment.BottomCenter).padding(bottom = 20.dp),
                        color = Color(0xFF00BCD4)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(if (isWideScreen) 20.dp else 12.dp))

        // 右侧语音操作按钮区
        Column(
            modifier = Modifier.width(if (isWideScreen) 80.dp else 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 发音播放
            VoiceActionButton(
                icon = Icons.Default.VolumeUp,
                contentDescription = "朗读",
                isActive = isSpeaking,
                color = Color(0xFFFF9800)
            ) {
                viewModel.pronounceCurrentChar()
            }

            // 语音录制
            VoiceActionButton(
                icon = Icons.Default.Mic,
                contentDescription = "录音",
                isActive = isRecording,
                color = if (isRecording) Color(0xFFF44336) else Color(0xFF2196F3)
            ) {
                if (isRecording) viewModel.stopRecording() else viewModel.startRecording()
            }
            
            // 播放录音
            if (viewModel.lastPcmData.value != null && !isRecording) {
                VoiceActionButton(
                    icon = if (isPlayingRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlayingRecording) "停止" else "回放",
                    isActive = isPlayingRecording,
                    color = Color(0xFF4CAF50)
                ) {
                    viewModel.playChildRecording()
                }
            }
            
            // 评分星级反馈 (Lottie)
            if (stars > 0 && !isRecording) {
                PronunciationStars(stars)
            }
        }
    }
}

@Composable
fun PronunciationStars(count: Int) {
    Row(horizontalArrangement = Arrangement.Center) {
        repeat(count) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFFD600),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ProgressSidebar(viewModel: LearningViewModel, userId: Long, isWideScreen: Boolean, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hanApp = context.applicationContext as HanApp
    val userRepository = hanApp.userRepository
    
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 已掌握, 1: 待学习

    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.6f), shape = RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        if (isWideScreen) {
            Text("我的进度", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2185B))
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                TabItem("已掌握 (${viewModel.learnedCharacters.value.size})", selectedTab == 0) { selectedTab = 0 }
                TabItem("待学习 (${viewModel.pendingCharacters.value.size})", selectedTab == 1) { selectedTab = 1 }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        if (isWideScreen || selectedTab == 0) {
            if (isWideScreen) Text("已掌握 (${viewModel.learnedCharacters.value.size})", fontSize = 14.sp, color = Color(0xFF43A047))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isWideScreen) 80.dp else 64.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 8.dp)
            ) {
                val list = viewModel.learnedCharacters.value
                items(list) { char ->
                    ProgressItemGrid(
                        userId = userId,
                        character = char, 
                        isLearned = true,
                        userRepository = userRepository,
                        onClick = { viewModel.selectCharacter(char, isReview = true) }
                    )
                }
            }
        }
        
        if (isWideScreen) Divider(modifier = Modifier.padding(vertical = 12.dp))
        
        if (isWideScreen || selectedTab == 1) {
            if (isWideScreen) Text("待学习 (${viewModel.pendingCharacters.value.size})", fontSize = 14.sp, color = Color.Gray)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isWideScreen) 80.dp else 64.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 8.dp)
            ) {
                val list = viewModel.pendingCharacters.value
                items(list) { char ->
                    ProgressItemGrid(
                        userId = userId,
                        character = char, 
                        isLearned = false,
                        userRepository = userRepository,
                        onClick = { viewModel.selectCharacter(char, isReview = false) }
                    )
                }
            }
        }
    }
}

@Composable
fun TabItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFFC2185B) else Color.Gray
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(3.dp)
                    .background(Color(0xFFC2185B), shape = RoundedCornerShape(2.dp))
                    .padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun ProgressItemGrid(
    userId: Long,
    character: com.hanapp.data.model.Character, 
    isLearned: Boolean, 
    userRepository: com.hanapp.data.repository.UserRepository,
    onClick: () -> Unit
) {
    var inkPreview by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(userId, character.id) {
        if (isLearned) {
            inkPreview = userRepository.getProgress(userId, character.id)?.lastWritingInk
        }
    }

    Card(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isLearned) Color(0xFFE8F5E9) else Color(0xFFEEEEEE).copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLearned) 4.dp else 0.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (isLearned && !inkPreview.isNullOrEmpty()) {
                val gson = remember { com.google.gson.Gson() }
                val type = object : com.google.gson.reflect.TypeToken<List<List<Map<String, Float>>>>() {}.type
                val inkData: List<List<Map<String, Float>>> = gson.fromJson(inkPreview, type)
                val inkBuilder = Ink.builder()
                inkData.forEach { strokeData ->
                    val strokeBuilder = Ink.Stroke.builder()
                    strokeData.forEach { p ->
                        strokeBuilder.addPoint(Ink.Point.create(p["x"]!!, p["y"]!!, 0L))
                    }
                    inkBuilder.addStroke(strokeBuilder.build())
                }
                
                DrawingBoard(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    historyInk = inkBuilder.build(),
                    strokeColor = Color(0xFF2E7D32),
                    strokeWidth = 12f,
                    enabled = false
                )
            } else {
                Text(
                    text = character.id, 
                    fontSize = 24.sp, 
                    fontWeight = FontWeight.Bold,
                    color = if (isLearned) Color(0xFF2E7D32) else Color.LightGray.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        label = "buttonScale"
    )

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .height(64.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .pointerInteropFilter {
                when (it.action) {
                    MotionEvent.ACTION_DOWN -> isPressed = true
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isPressed = false
                }
                false
            },
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Text(text = text, fontSize = if(text.length > 4) 16.sp else 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
