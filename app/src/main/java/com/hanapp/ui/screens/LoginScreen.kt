package com.hanapp.ui.screens

import kotlinx.coroutines.launch

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanapp.R
import com.hanapp.data.model.User
import com.hanapp.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: ProfileViewModel,
    onLoginSuccess: (User) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val recentUsers by viewModel.recentUsers.collectAsState()
    val selectedAvatar by viewModel.selectedAvatar.collectAsState()
    
    val avatars = listOf(
        "avatar_twilight", "avatar_rainbow", "avatar_pinkie", "avatar_fluttershy", "avatar_rarity"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFFDEFF9), Color(0xFFECF0F1))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(min = 450.dp, max = 600.dp)
                .fillMaxWidth(0.9f)
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "小马宝莉快乐园",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE91E63)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 最近用户
                if (recentUsers.isNotEmpty()) {
                    val scope = rememberCoroutineScope()
                    Text("最近小伙伴:", fontSize = 14.sp, color = Color.Gray)
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        recentUsers.forEach { user ->
                            RecentUserItem(user) { 
                                scope.launch {
                                    val updatedUser = viewModel.loginRecentUser(user)
                                    onLoginSuccess(updatedUser)
                                }
                            }
                        }
                    }
                    Divider(modifier = Modifier.padding(vertical = 16.dp))
                }

                Text("选择新头像:", fontSize = 14.sp, color = Color.Gray)
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    avatars.forEach { avatar ->
                        AvatarItem(
                            resName = avatar,
                            isSelected = selectedAvatar == avatar,
                            onClick = { viewModel.selectAvatar(avatar) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("你的大名") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                Text("选择年级:", fontSize = 14.sp, color = Color.Gray)
                val selectedGrade by viewModel.selectedGrade.collectAsState()
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (1.. 5).forEach { grade ->
                        val isSelected = selectedGrade == grade
                        Button(
                            onClick = { viewModel.selectGrade(grade) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Color(0xFFE91E63) else Color.White,
                                contentColor = if (isSelected) Color.White else Color(0xFFE91E63)
                            ),
                            modifier = Modifier.weight(1f).height(40.dp).border(1.dp, Color(0xFFE91E63), RoundedCornerShape(10.dp)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("${grade}年级", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                val scope = rememberCoroutineScope()
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            scope.launch {
                                val user = viewModel.loginOrRegister(name)
                                onLoginSuccess(user)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                ) {
                    Text("开始魔法学习", fontSize = 18.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun AvatarItem(resName: String, isSelected: Boolean, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
    
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) Color(0xFFE91E63) else Color.LightGray,
                shape = CircleShape
            )
            .clickable { onClick() }
    ) {
        Image(
            painter = painterResource(id = if (resId != 0) resId else R.drawable.pony_mascot),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun RecentUserItem(user: User, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resId = context.resources.getIdentifier(user.avatar, "drawable", context.packageName)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Image(
                painter = painterResource(id = if (resId != 0) resId else R.drawable.pony_mascot),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(user.name, fontSize = 12.sp, color = Color.DarkGray)
    }
}
