package com.example.myapplication.di

import javax.inject.Qualifier

/** 比界面更长的协程范围，用于离开对话页后仍能收完正在生成的回复。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope
