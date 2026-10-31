package com.example.myapplication

import com.example.myapplication.domain.model.VoiceChoice
import com.example.myapplication.domain.model.livePairError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 同声传译的语种/音色组合限制，对应 README 里列出的规则。 */
class LanguageRulesTest {
    @Test
    fun chineseToEnglishIsAllowed() {
        assertNull(livePairError("zh", "en", VoiceChoice.FEMALE))
        assertNull(livePairError("en", "zh", VoiceChoice.CLONE))
    }

    @Test
    fun sameLanguageIsRejected() {
        assertEquals(R.string.pair_same, livePairError("zh", "zh", VoiceChoice.FEMALE))
    }

    @Test
    fun oneSideMustBeChineseOrEnglish() {
        assertEquals(R.string.pair_zh_en, livePairError("ja", "ko", VoiceChoice.FEMALE))
    }

    @Test
    fun namedVoiceCanOnlyReachChineseOrEnglish() {
        assertEquals(R.string.pair_named_voice, livePairError("zh", "ja", VoiceChoice.FEMALE))
        assertNull(livePairError("zh", "ja", VoiceChoice.CLONE))
    }

    @Test
    fun dialectsOnlyTranslateIntoChineseOrEnglish() {
        assertEquals(R.string.pair_dialect, livePairError("yue-CN", "ja", VoiceChoice.FEMALE))
        assertNull(livePairError("yue-CN", "en", VoiceChoice.FEMALE))
    }
}
