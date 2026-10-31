package com.yuguang.lumo.domain

import com.yuguang.lumo.domain.model.ModelChoice
import com.yuguang.lumo.domain.model.ProviderId
import kotlinx.coroutines.flow.Flow

interface ModelCatalogRepository {
    fun observe(provider: ProviderId): Flow<List<ModelChoice>>

    fun supportsVision(provider: ProviderId, modelId: String): Boolean

    fun preferredImageGenModel(): String

    suspend fun refresh(provider: ProviderId): String?

    /** 缓存未过期则跳过网络请求。 */
    suspend fun refreshIfStale(provider: ProviderId, force: Boolean = false): String?

    suspend fun refreshAll(): String?
}
