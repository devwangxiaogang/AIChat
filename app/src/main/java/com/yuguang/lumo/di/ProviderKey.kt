package com.yuguang.lumo.di

import com.yuguang.lumo.domain.model.ProviderId
import dagger.MapKey

@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class ProviderKey(val value: ProviderId)
