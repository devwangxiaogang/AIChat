package com.example.myapplication.domain.model

import androidx.annotation.StringRes
import com.example.myapplication.R

/** 朗读引擎。对话、文字翻译和同传定稿句共用这一项。 */
enum class TtsEngine(@StringRes val labelRes: Int) {
    DOUBAO(R.string.engine_doubao),
    LOCAL(R.string.engine_local),
}

/** 豆包播报音色。复刻原声只用于同声翻译，不用于朗读文字。 */
enum class VoiceChoice(
    @StringRes val labelRes: Int,
    val speakerId: String?,
    val ttsSpeakerId: String,
) {
    FEMALE(R.string.voice_female, "zh_female_vv_uranus_bigtts", "zh_female_vv_uranus_bigtts"),
    MALE(R.string.voice_male, "zh_male_jingqiangkanye_emo_mars_bigtts", "zh_male_m191_uranus_bigtts"),
    CLONE(R.string.voice_clone, null, "zh_female_vv_uranus_bigtts"),
}

data class SpeechLanguage(val code: String, @StringRes val labelRes: Int)

data class SpeechPreferences(
    val engine: TtsEngine = TtsEngine.DOUBAO,
    val speaker: VoiceChoice = VoiceChoice.FEMALE,
    val translationModel: String = DEFAULT_TRANSLATION_MODEL,
    val textSource: String = "zh",
    val textTarget: String = "en",
    val liveSource: String = "zh",
    val liveTarget: String = "en",
    val liveVoice: VoiceChoice = VoiceChoice.FEMALE,
)

data class TranslationModelOption(val id: String, @StringRes val labelRes: Int)

const val DEFAULT_TRANSLATION_MODEL = "doubao-seed-translation-250915"

val translationModels = listOf(
    TranslationModelOption(DEFAULT_TRANSLATION_MODEL, R.string.model_seed_translation),
)

/** 文字翻译语种。空 code 表示让服务自动检测源语言。 */
val textLanguages = listOf(
    SpeechLanguage("zh", R.string.lang_zh_hans),
    SpeechLanguage("zh-Hant", R.string.lang_zh_hant),
    SpeechLanguage("en", R.string.lang_en),
    SpeechLanguage("ja", R.string.lang_ja),
    SpeechLanguage("ko", R.string.lang_ko),
    SpeechLanguage("de", R.string.lang_de),
    SpeechLanguage("fr", R.string.lang_fr),
    SpeechLanguage("es", R.string.lang_es),
    SpeechLanguage("it", R.string.lang_it),
    SpeechLanguage("pt", R.string.lang_pt),
    SpeechLanguage("ru", R.string.lang_ru),
    SpeechLanguage("th", R.string.lang_th),
    SpeechLanguage("vi", R.string.lang_vi),
    SpeechLanguage("ar", R.string.lang_ar),
    SpeechLanguage("id", R.string.lang_id),
    SpeechLanguage("ms", R.string.lang_ms),
    SpeechLanguage("nl", R.string.lang_nl),
    SpeechLanguage("pl", R.string.lang_pl),
    SpeechLanguage("cs", R.string.lang_cs),
    SpeechLanguage("tr", R.string.lang_tr),
    SpeechLanguage("uk", R.string.lang_uk),
)

private val liveEight = setOf("zh", "en", "de", "fr", "es", "id", "ja", "pt")

val liveLanguages = listOf(
    SpeechLanguage("zh", R.string.lang_zh),
    SpeechLanguage("en", R.string.lang_en),
    SpeechLanguage("de", R.string.lang_de),
    SpeechLanguage("fr", R.string.lang_fr),
    SpeechLanguage("es", R.string.lang_es),
    SpeechLanguage("id", R.string.lang_id),
    SpeechLanguage("ja", R.string.lang_ja),
    SpeechLanguage("pt", R.string.lang_pt),
    SpeechLanguage("ko", R.string.lang_ko),
    SpeechLanguage("ru", R.string.lang_ru),
    SpeechLanguage("it", R.string.lang_it),
    SpeechLanguage("ar", R.string.lang_ar),
    SpeechLanguage("tr", R.string.lang_tr),
    SpeechLanguage("ms", R.string.lang_ms),
    SpeechLanguage("vi", R.string.lang_vi),
    SpeechLanguage("th", R.string.lang_th),
    SpeechLanguage("nl", R.string.lang_nl),
    SpeechLanguage("ro", R.string.lang_ro),
    SpeechLanguage("pl", R.string.lang_pl),
    SpeechLanguage("cs", R.string.lang_cs),
    SpeechLanguage("yue-CN", R.string.lang_yue),
    SpeechLanguage("sh-CN", R.string.lang_sh),
)

/**
 * 同声传译 2.0 的语种限制：一边必须是中文或英文。
 * 指定女声或男声时目标语言只能是中文或英文；复刻原声两边都限制在八种语言。
 */
fun livePairError(source: String, target: String, voice: VoiceChoice): Int? {
    if (source == target) return R.string.pair_same
    if (source == "yue-CN" || source == "sh-CN") {
        if (target != "zh" && target != "en") return R.string.pair_dialect
    }
    val zhEn = source == "zh" || source == "en" || target == "zh" || target == "en"
    if (!zhEn) return R.string.pair_zh_en
    if (voice != VoiceChoice.CLONE && target != "zh" && target != "en") {
        return R.string.pair_named_voice
    }
    if (voice == VoiceChoice.CLONE && (source !in liveEight || target !in liveEight)) {
        return R.string.pair_clone
    }
    return null
}
