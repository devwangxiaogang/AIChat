package com.example.myapplication.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatMediaStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val root: File
        get() = File(context.filesDir, "chat_media").also { it.mkdirs() }

    suspend fun importUserImage(uri: Uri): ImportedImage? = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        val mime = context.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val compressed = compress(bytes, mime) ?: return@withContext null
        val ext = when (compressed.mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val file = File(root, "u_${UUID.randomUUID()}.$ext")
        file.writeBytes(compressed.bytes)
        ImportedImage(file.absolutePath, compressed.mime)
    }

    suspend fun saveGenerated(bytes: ByteArray, mime: String = "image/jpeg"): String = withContext(Dispatchers.IO) {
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            else -> "jpg"
        }
        val file = File(root, "g_${UUID.randomUUID()}.$ext")
        file.writeBytes(bytes)
        file.absolutePath
    }

    suspend fun toDataUrl(localPath: String, mime: String): String? = withContext(Dispatchers.IO) {
        val file = File(localPath)
        if (!file.isFile) return@withContext null
        val bytes = file.readBytes()
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        "data:$mime;base64,$encoded"
    }

    /** base64 后的大致字符数，用来在读文件之前判断请求体预算。 */
    fun encodedLength(localPath: String): Long {
        val file = File(localPath)
        if (!file.isFile) return 0
        return file.length() / 3 * 4
    }

    fun deletePaths(paths: List<String>) {
        paths.forEach { path ->
            runCatching { File(path).takeIf { it.isFile }?.delete() }
        }
    }

    private fun compress(source: ByteArray, mime: String): Compressed? {
        if (source.size <= MAX_BYTES && source.size <= MAX_RAW_KEEP) {
            return Compressed(source, mime.ifBlank { "image/jpeg" })
        }
        val bitmap = BitmapFactory.decodeByteArray(source, 0, source.size) ?: return null
        val scaled = scaleDown(bitmap)
        val out = ByteArrayOutputStream()
        var quality = 88
        do {
            out.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            quality -= 8
        } while (out.size() > MAX_BYTES && quality > 40)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return Compressed(out.toByteArray(), "image/jpeg")
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_SIDE) return bitmap
        val scale = MAX_SIDE.toFloat() / maxSide
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    data class ImportedImage(val path: String, val mime: String)

    private data class Compressed(val bytes: ByteArray, val mime: String)

    companion object {
        const val MAX_ATTACHMENTS = 4
        private const val MAX_BYTES = 2 * 1024 * 1024
        private const val MAX_RAW_KEEP = 2 * 1024 * 1024
        private const val MAX_SIDE = 2048
    }
}
