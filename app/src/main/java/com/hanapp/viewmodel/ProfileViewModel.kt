package com.hanapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hanapp.data.model.User
import com.hanapp.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ProfileViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val _recentUsers = MutableStateFlow<List<User>>(emptyList())
    val recentUsers: StateFlow<List<User>> = _recentUsers.asStateFlow()

    private val _selectedAvatar = MutableStateFlow("avatar_twilight")
    val selectedAvatar: StateFlow<String> = _selectedAvatar.asStateFlow()

    private val _selectedGrade = MutableStateFlow(1)
    val selectedGrade: StateFlow<Int> = _selectedGrade.asStateFlow()

    init {
        loadRecentUsers()
    }

    private fun loadRecentUsers() {
        viewModelScope.launch {
            userRepository.getRecentUsers().collect {
                _recentUsers.value = it
            }
        }
    }

    fun selectAvatar(avatar: String) {
        _selectedAvatar.value = avatar
    }

    fun selectGrade(grade: Int) {
        _selectedGrade.value = grade
    }

    suspend fun loginOrRegister(name: String): User {
        // 全局查找用户，而不仅仅是在最近缓存中查找
        val allUsers = userRepository.getAllUsers()
        var user = allUsers.find { it.name == name }
        
        if (user == null) {
            user = User(
                name = name, 
                avatar = _selectedAvatar.value, 
                currentGrade = _selectedGrade.value,
                lastLoginTime = System.currentTimeMillis()
            )
            val id = userRepository.insertUser(user)
            user = user.copy(id = id)
        } else {
            // 更新登录时间，同时支持换头像和年级功能
            user = user.copy(
                lastLoginTime = System.currentTimeMillis(),
                avatar = _selectedAvatar.value,
                currentGrade = _selectedGrade.value
            )
            userRepository.updateUser(user)
        }
        return user
    }

    suspend fun loginRecentUser(user: User): User {
        val updatedUser = user.copy(
            lastLoginTime = System.currentTimeMillis(),
            avatar = _selectedAvatar.value, // 应用当前选中的头像
            currentGrade = _selectedGrade.value // 应用当前选中的年级
        )
        userRepository.updateUser(updatedUser)
        return updatedUser
    }
}
