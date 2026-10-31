package com.example.myapplication

import com.example.myapplication.domain.model.ModelCapabilities
import com.example.myapplication.domain.model.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 模型目录筛选与能力推断；这些规则直接决定选择器里出现哪些模型。 */
class ModelCapabilitiesTest {
    @Test
    fun arkKeepOnlyListedFamilies() {
        assertTrue(ModelCapabilities.arkKeep("doubao-seed-2-1-lite-260915"))
        assertTrue(ModelCapabilities.arkKeep("doubao-seedream-5-0-flash-260915"))
        assertTrue(ModelCapabilities.arkKeep("deepseek-v4-pro-ga-260813"))
        assertTrue(ModelCapabilities.arkKeep("glm-5-2-260617"))
        assertFalse(ModelCapabilities.arkKeep("gpt-4o"))
        assertFalse(ModelCapabilities.arkKeep("doubao-pro-32k"))
    }

    @Test
    fun versionSuffixIsComparable() {
        assertEquals(260428L, ModelCapabilities.versionKey("doubao-seed-2-0-mini-260428"))
        assertEquals(-1L, ModelCapabilities.versionKey("doubao-seed-evolving"))
        assertEquals("doubao-seed-2-0-mini", ModelCapabilities.familyKey("doubao-seed-2-0-mini-260428"))
        assertEquals("glm-5-2", ModelCapabilities.familyKey("glm-5-2"))
    }

    @Test
    fun visionIsInferredFromFamily() {
        assertTrue(ModelCapabilities.inferVision(ProviderId.DOUBAO, "doubao-seed-2-1-lite-260915"))
        assertTrue(ModelCapabilities.inferVision(ProviderId.DEEPSEEK, "deepseek-flash"))
        assertFalse(ModelCapabilities.inferVision(ProviderId.DEEPSEEK, "deepseek-v4-pro"))
        assertFalse(ModelCapabilities.inferVision(ProviderId.DOUBAO, "doubao-seedream-5-0-flash-260915"))
        assertFalse(ModelCapabilities.inferVision(ProviderId.DOUBAO, "glm-5-2-260617"))
    }

    @Test
    fun imageGenerationOnlySeedream() {
        assertTrue(ModelCapabilities.inferImageGen("doubao-seedream-5-0-flash-260915"))
        assertFalse(ModelCapabilities.inferImageGen("doubao-seed-2-1-lite-260915"))
    }
}
