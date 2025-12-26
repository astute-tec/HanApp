package com.hanapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hanapp.data.model.User
import com.hanapp.data.model.UserProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun getAllUsersFlow(): Flow<List<User>>

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<User>

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Long): User?

    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUserFlow(userId: Long): Flow<User?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    @Query("SELECT * FROM user_progress WHERE userId = :userId AND characterId = :characterId")
    suspend fun getProgress(userId: Long, characterId: String): UserProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: UserProgress)

    @Query("SELECT * FROM user_progress WHERE userId = :userId")
    fun getAllProgressFlow(userId: Long): Flow<List<UserProgress>>

    @Query("SELECT * FROM user_progress WHERE userId = :userId")
    suspend fun getAllProgress(userId: Long): List<UserProgress>

    @Query("SELECT * FROM users ORDER BY lastLoginTime DESC LIMIT 3")
    fun getRecentUsers(): Flow<List<User>>
}
