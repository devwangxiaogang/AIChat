package com.example.myapplication.di

import com.example.myapplication.domain.model.ProviderId
import dagger.MapKey

@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class ProviderKey(val value: ProviderId)
