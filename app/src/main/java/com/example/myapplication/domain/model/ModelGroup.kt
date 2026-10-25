package com.example.myapplication.domain.model

import androidx.annotation.StringRes
import com.example.myapplication.R

/** 稳定分组键，避免把 R.string 资源号写入磁盘缓存。 */
enum class ModelGroup(val key: String, @StringRes val labelRes: Int) {
    OFFICIAL("official", R.string.group_official),
    SEED("seed", R.string.group_seed),
    SEEDREAM("seedream", R.string.group_seedream),
    ARK_DEEPSEEK("ark_deepseek", R.string.group_ark_deepseek),
    ARK_GLM("ark_glm", R.string.group_ark_glm),
    ;

    companion object {
        fun fromKey(key: String): ModelGroup =
            entries.firstOrNull { it.key == key } ?: OFFICIAL

        fun fromLabelRes(@StringRes res: Int): ModelGroup =
            entries.firstOrNull { it.labelRes == res } ?: OFFICIAL
    }
}
