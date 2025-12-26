package com.hanapp.data.repository

import com.hanapp.data.dao.UserDao
import com.hanapp.data.model.User
import com.hanapp.data.model.UserProgress
import kotlinx.coroutines.flow.Flow

class UserRepository(private val userDao: UserDao) {
    fun getAllUsersFlow(): Flow<List<User>> = userDao.getAllUsersFlow()
    
    suspend fun getAllUsers(): List<User> = userDao.getAllUsers()

    fun getRecentUsers(): Flow<List<User>> = userDao.getRecentUsers()

    suspend fun getUserById(userId: Long): User? = userDao.getUserById(userId)

    fun getUserFlow(userId: Long): Flow<User?> = userDao.getUserFlow(userId)

    suspend fun insertUser(user: User): Long = userDao.insertUser(user)

    suspend fun updateUser(user: User) = userDao.updateUser(user)

    suspend fun getProgress(userId: Long, characterId: String): UserProgress? = 
        userDao.getProgress(userId, characterId)

    suspend fun saveProgress(progress: UserProgress) = userDao.insertProgress(progress)

    fun getUserTotalProgressFlow(userId: Long): Flow<List<UserProgress>> = 
        userDao.getAllProgressFlow(userId)

    suspend fun getUserTotalProgress(userId: Long): List<UserProgress> = 
        userDao.getAllProgress(userId)
}
