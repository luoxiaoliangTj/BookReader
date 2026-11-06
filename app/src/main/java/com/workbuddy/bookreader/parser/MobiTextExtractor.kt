package com.workbuddy.bookreader.parser

import java.io.ByteArrayInputStream

/**
 * 简易 MOBI/PalmDOC 文本提取器（无 DRM）。
 * PDB 头 -> MOBI 头 -> PalmDOC LZ77 解压第 1 条记录起的文本记录。
 */
object MobiTextExtractor {

    fun extract(bytes: ByteArray): String {
        require(bytes.size > 132)
        val recCount = readU16(bytes, 76)
        // 找到 PDB record info list
        val recOffsets = IntArray(recCount)
        for (i in 0 until recCount) {
            recOffsets[i] = readU32(bytes, 78 + i * 8)
        }
        val rec0 = recOffsets[0]
        val rec0End = recOffsets.getOrElse(1) { bytes.size }
        val compression = readU16(bytes, rec0)
        val textRecCount = readU16(bytes, rec0 + 8)
        val recSize = readU16(bytes, rec0 + 10)   // 一般 4096
        val mobiHeaderLen = readU32(bytes, rec0 + 20)
        val extraFlags = if (mobiHeaderLen >= 0xE4 && rec0 + 0xF2 + 2 <= rec0End)
            readU16(bytes, rec0 + 0xF2) else 0

        val out = StringBuilder(textRecCount * 1024)
        for (i in 1 until textRecCount) {
            val s = recOffsets[i]
            val e = recOffsets.getOrElse(i + 1) { minOf(bytes.size, s + recSize) }
            val data = trimTrailingEntries(bytes.copyOfRange(s, e), extraFlags)
            val plain = when (compression) {
                1 -> data                                   // 无压缩
                2 -> palmDocDecompress(data)                // PalmDOC LZ77
                17480 -> huffCdDecompress(data, bytes, recOffsets) // HUFF/CDIC（简化：回退）
                else -> data
            }
            out.append(String(plain, Charsets.UTF_8))
        }
        return out.toString()
            .replace(Regex("\\u2029|\\r"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
    }

    /** 去掉记录尾部的 multibyte/entry-size 尾标 */
    private fun trimTrailingEntries(rec: ByteArray, extraFlags: Int): ByteArray {
        var size = rec.size
        if (extraFlags and 1 != 0) { // multibyte overlap
            val a = rec[size - 1].toInt() and 0x03
            size -= a + 1
        }
        size -= 1 // entry size byte（简化处理：假设无额外 entry table）
        return if (size > 0) rec.copyOf(size) else rec
    }

    private fun palmDocDecompress(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(data.size * 4)
        var i = 0
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            when {
                b == 0 -> out.write(byteArrayOf(0))
                b <= 8 -> { for (j in 0 until b) out.write(byteArrayOf(data[i + 1 + j])); i += b }
                b <= 0x7F -> out.write(byteArrayOf(b.toByte()))
                b <= 0xBF -> {
                    val b2 = data[i + 1].toInt() and 0xFF
                    val dist = ((b shl 8) or b2) shr 3
                    val len = (b2 and 0x07) + 3
                    val hist = out.toByteArray()
                    for (j in 0 until len) out.write(byteArrayOf(hist[hist.size - dist]))
                    i += 1
                }
                else -> { out.write(byteArrayOf(' '.code.toByte())); out.write(byteArrayOf((b and 0x7F).toByte())) }
            }
            i += 1
        }
        return out.toByteArray()
    }

    private fun huffCdDecompress(data: ByteArray, all: ByteArray, offs: IntArray): ByteArray {
        // HUFF/CDIC 解压较复杂，这里回退为原样输出，文本可读性可能受损
        return data
    }

    private fun readU16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
    private fun readU32(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
        ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
}
