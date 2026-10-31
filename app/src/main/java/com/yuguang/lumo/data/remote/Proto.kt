package com.yuguang.lumo.data.remote

import java.io.ByteArrayOutputStream

/** 只编码同声传译请求里用到的 proto3 字段，缺省值不写出。 */
internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun string(field: Int, value: String) {
        if (value.isEmpty()) return
        bytes(field, value.toByteArray(Charsets.UTF_8))
    }

    fun int32(field: Int, value: Int) {
        if (value == 0) return
        tag(field, 0)
        varint(value.toLong() and 0xffffffffL)
    }

    fun bool(field: Int, value: Boolean) {
        if (!value) return
        int32(field, 1)
    }

    fun bytes(field: Int, value: ByteArray) {
        if (value.isEmpty()) return
        tag(field, 2)
        varint(value.size.toLong())
        out.write(value)
    }

    fun message(field: Int, block: ProtoWriter.() -> Unit) {
        val nested = ProtoWriter().apply(block).toByteArray()
        if (nested.isEmpty()) return
        bytes(field, nested)
    }

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wire: Int) {
        varint(((field shl 3) or wire).toLong())
    }

    private fun varint(value: Long) {
        var rest = value
        while (rest and 0x7f.inv().toLong() != 0L) {
            out.write((rest and 0x7f or 0x80).toInt())
            rest = rest ushr 7
        }
        out.write(rest.toInt())
    }
}

internal class ProtoReader(private val data: ByteArray) {
    private var offset = 0

    fun fields(): List<ProtoField> {
        val found = mutableListOf<ProtoField>()
        while (offset < data.size) {
            val key = varint().toInt()
            val field = key ushr 3
            when (key and 0x7) {
                0 -> found += ProtoField(field, varint = varint())
                2 -> {
                    val length = varint().toInt()
                    val end = (offset + length).coerceAtMost(data.size)
                    found += ProtoField(field, bytes = data.copyOfRange(offset, end))
                    offset = end
                }
                5 -> offset += 4
                1 -> offset += 8
                else -> break
            }
        }
        return found
    }

    private fun varint(): Long {
        var result = 0L
        var shift = 0
        while (offset < data.size && shift < 64) {
            val byte = data[offset++].toInt() and 0xff
            result = result or ((byte and 0x7f).toLong() shl shift)
            if (byte and 0x80 == 0) break
            shift += 7
        }
        return result
    }
}

internal data class ProtoField(
    val number: Int,
    val varint: Long = 0,
    val bytes: ByteArray? = null,
) {
    fun text(): String = bytes?.toString(Charsets.UTF_8).orEmpty()
}
