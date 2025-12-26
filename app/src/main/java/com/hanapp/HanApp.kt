package com.hanapp

import android.app.Application
import com.hanapp.data.AppDatabase
import com.hanapp.data.repository.UserRepository

class HanApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val userRepository by lazy { UserRepository(database.userDao()) }

    override fun onCreate() {
        super.onCreate()
        // 可以在这里预初始化或进行性能追踪
    }
}
