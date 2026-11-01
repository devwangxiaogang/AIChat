package com.yuguang.lumo

import com.yuguang.lumo.data.remote.DoubaoSpeechHeaders
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeAuthHeadersTest {
    private fun realtime(apiKey: String, appId: String, accessToken: String): Request {
        val builder = Request.Builder().url("wss://openspeech.bytedance.com/api/v3/duplex/realtime/dialogue")
        assertTrue(DoubaoSpeechHeaders.applyRealtimeAuth(builder, apiKey, appId, accessToken))
        return builder.build()
    }

    @Test
    fun apiKeyWinsWhenBothSchemesConfigured() {
        val request = realtime("key-1", "123456789", "token-1")
        assertEquals("key-1", request.header("X-Api-Key"))
        assertNull(request.header("X-Api-App-ID"))
        assertNull(request.header("X-Api-Access-Key"))
    }

    @Test
    fun legacyUsesDocumentedDialogHeaders() {
        val request = realtime("", "123456789", "token-1")
        assertEquals("123456789", request.header("X-Api-App-ID"))
        assertEquals("token-1", request.header("X-Api-Access-Key"))
        assertEquals("volc.speech.dialog", request.header("X-Api-Resource-Id"))
        assertEquals("PlgvMymc7f3tQnJ6", request.header("X-Api-App-Key"))
        assertNull(request.header("X-Api-Key"))
    }

    @Test
    fun blanksAreRejected() {
        val builder = Request.Builder().url("wss://openspeech.bytedance.com/api/v3/duplex/realtime/dialogue")
        assertFalse(DoubaoSpeechHeaders.applyRealtimeAuth(builder, " ", "", "token-1"))
        assertFalse(DoubaoSpeechHeaders.applyRealtimeAuth(builder, "", "123456789", ""))
    }

    @Test
    fun asrAuthKeepsLegacyPriority() {
        val builder = Request.Builder().url("https://openspeech.bytedance.com/api/v3/sauc/bigmodel_nostream")
        assertTrue(DoubaoSpeechHeaders.applyAuth(builder, "key-1", "123456789", "token-1"))
        val request = builder.build()
        assertEquals("123456789", request.header("X-Api-App-Key"))
        assertEquals("token-1", request.header("X-Api-Access-Key"))
        assertNull(request.header("X-Api-Key"))
    }
}