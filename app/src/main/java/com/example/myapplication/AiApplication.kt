package com.example.myapplication

import android.app.Application
import com.example.myapplication.data.AppStartup
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Hilt 入口。依赖在进程内单例，会话库和密钥存储随应用存活。 */
@HiltAndroidApp
class AiApplication : Application() {
    @Inject
    lateinit var startup: AppStartup

    override fun onCreate() {
        super.onCreate()
        // 上一次进程可能停在流式生成中，先把残留状态收尾。
        startup.run()
    }
}
