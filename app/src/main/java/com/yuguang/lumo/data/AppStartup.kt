package com.yuguang.lumo.data

import android.content.Context
import com.yuguang.lumo.R
import com.yuguang.lumo.data.local.MessageDao
import com.yuguang.lumo.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 进程启动时的一次性修复。
 *
 * 流式生成只活在内存里的协程中，进程被杀后消息会停在 STREAMING：
 * 气泡一直显示「生成中」，也没有可点的重试。启动时统一改成 FAILED 即可恢复。
 */
@Singleton
class AppStartup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageDao: MessageDao,
    @AppScope private val scope: CoroutineScope,
) {
    fun run() {
        scope.launch {
            runCatching { messageDao.failStreaming(context.getString(R.string.generation_interrupted)) }
        }
    }
}
