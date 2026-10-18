package com.example.myapplication

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Hilt 入口。依赖在进程内单例，会话库和密钥存储随应用存活。 */
@HiltAndroidApp
class AiApplication : Application()
