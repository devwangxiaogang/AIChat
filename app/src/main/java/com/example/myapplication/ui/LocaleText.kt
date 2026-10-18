package com.example.myapplication.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.SpeechLanguage
import com.example.myapplication.domain.model.VoiceChoice
import com.example.myapplication.domain.model.knownModelLabel
import com.example.myapplication.domain.model.translationModels

@Composable
fun languageLabel(code: String, languages: List<SpeechLanguage>): String =
    if (code.isEmpty()) {
        stringResource(R.string.lang_auto)
    } else {
        languages.firstOrNull { it.code == code }?.let { stringResource(it.labelRes) } ?: code
    }

@Composable
fun shortLanguageLabel(code: String): String {
    val res = shortLanguageRes(code)
    val text = stringResource(res ?: R.string.lang_auto_short)
    return if (res == null && code.isNotEmpty()) code else text
}

@Composable
fun translationModelLabel(id: String): String =
    translationModels.firstOrNull { it.id == id }?.let { stringResource(it.labelRes) }
        ?: stringResource(R.string.model_custom)

@Composable
fun ProviderId.localizedModel(id: String): String =
    knownModelLabel(id) ?: stringResource(R.string.model_custom)

@Composable
fun ProviderId.localizedSummary(id: String): String =
    stringResource(R.string.provider_model, stringResource(nameRes), localizedModel(id))

@Composable
fun storedVoiceLabel(option: String): String {
    val res = voiceLabelRes(option) ?: return option
    return stringResource(res)
}

@StringRes
private fun shortLanguageRes(code: String): Int? = when (code) {
    "" -> R.string.lang_auto_short
    "zh" -> R.string.short_zh
    "zh-Hant" -> R.string.short_zh_hant
    "en" -> R.string.short_en
    "ja" -> R.string.short_ja
    "ko" -> R.string.short_ko
    "de" -> R.string.short_de
    "fr" -> R.string.short_fr
    "es" -> R.string.short_es
    "it" -> R.string.short_it
    "pt" -> R.string.short_pt
    "ru" -> R.string.short_ru
    "th" -> R.string.short_th
    "vi" -> R.string.short_vi
    "ar" -> R.string.short_ar
    "id" -> R.string.short_id
    "ms" -> R.string.short_ms
    "nl" -> R.string.short_nl
    "pl" -> R.string.short_pl
    "cs" -> R.string.short_cs
    "tr" -> R.string.short_tr
    "uk" -> R.string.short_uk
    "ro" -> R.string.short_ro
    "yue-CN" -> R.string.short_yue
    "sh-CN" -> R.string.short_sh
    else -> null
}

@StringRes
private fun voiceLabelRes(option: String): Int? = when (option) {
    VoiceChoice.FEMALE.name, "女声" -> R.string.voice_female
    VoiceChoice.MALE.name, "男声" -> R.string.voice_male
    VoiceChoice.CLONE.name, "复刻原声" -> R.string.voice_clone
    else -> null
}

fun isDefaultConversationTitle(title: String): Boolean = title == "新对话" || title == "New chat"
