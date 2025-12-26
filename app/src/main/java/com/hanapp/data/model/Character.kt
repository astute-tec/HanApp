package com.hanapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "characters")
data class Character(
    @PrimaryKey val id: String, // 汉字本身
    val pinyin: String,
    val strokes: String, // 笔画顺序数据 (JSON 字符串)
    val grade: Int,     // 年级 (1, 2, 3)
    val exampleWords: List<String>, // 组词列表
    val meaning: String = ""
)
