package com.example.simpleytdownloader

import android.app.Application
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "SimpleYTDownloader"
        var isInitialized = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        initializeLibraries()
    }

    private fun initializeLibraries() {
        applicationScope.launch {
            try {
                // 初始化 YoutubeDL
                YoutubeDL.getInstance().init(this@App)
                Log.d(TAG, "YoutubeDL initialized successfully")

                // 初始化 FFmpeg
                FFmpeg.getInstance().init(this@App)
                Log.d(TAG, "FFmpeg initialized successfully")

                // 初始化 Aria2c
                Aria2c.getInstance().init(this@App)
                Log.d(TAG, "Aria2c initialized successfully")

                isInitialized = true
                Log.d(TAG, "All libraries initialized successfully")
            } catch (e: YoutubeDLException) {
                Log.e(TAG, "Failed to initialize libraries", e)
            }
        }
    }
}
