package com.hanapp.data.model

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "user_progress",
    primaryKeys = ["userId", "characterId"],
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class UserProgress(
    val userId: Long,
    val characterId: String,
    val practiceCount: Int = 0,
    val highScore: Int = 0,
    var lastWritingInk: String? = null,
    var lastScore: Int = 0,
    var pronunciationScore: Int = 0, // 记录读音已获得的最高分数/星级
    var lastModified: Long = 0
)
