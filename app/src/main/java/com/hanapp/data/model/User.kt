package com.hanapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatar: String = "avatar_twilight", // 默认头像
    val totalPoints: Int = 0,
    val currentGrade: Int = 1,
    val lastCharacterId: String = "",
    val lastLoginTime: Long = System.currentTimeMillis(),
    val totalLearningTime: Long = 0 // 总学习时长（秒）
)
