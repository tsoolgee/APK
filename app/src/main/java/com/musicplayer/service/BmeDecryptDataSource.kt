package com.musicplayer.service

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener

/**
 * DataSource פנימי — מפענח קבצי BME תוך כדי streaming.
 * שם "BME" חשוף בממשק; הלוגיקה הפנימית נסתרת.
 */
internal class BmeDecryptDataSource(
    private val upstream: DataSource,
    private val key: Int,
    private val startOffset: Long = AUTO_DETECT
) : DataSource {

    private var resolvedOffset: Long = startOffset
    private var bytesRead: Long = 0L
    private var firstRead = true

    override fun open(dataSpec: DataSpec): Long {
        bytesRead = 0L
        firstRead = true
        resolvedOffset = startOffset
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val n = upstream.read(buffer, offset, length)
        if (n <= 0) return n

        if (firstRead && resolvedOffset == AUTO_DETECT) {
            resolvedOffset = detectOffset(buffer, offset, n)
            firstRead = false
        }

        val pos = bytesRead
        for (i in 0 until n) {
            if (pos + i >= resolvedOffset) {
                buffer[offset + i] = (buffer[offset + i].toInt() xor key).toByte()
            }
        }
        bytesRead += n
        return n
    }

    private fun detectOffset(buf: ByteArray, off: Int, len: Int): Long {
        if (len < 10) return 0L
        val b0 = buf[off].toInt() and 0xFF
        val b1 = buf[off + 1].toInt() and 0xFF
        val b2 = buf[off + 2].toInt() and 0xFF
        // ID3v2 tag
        if (b0 == 0x49 && b1 == 0x44 && b2 == 0x33) {
            val size =
                ((buf[off + 6].toInt() and 0x7F) shl 21) or
                ((buf[off + 7].toInt() and 0x7F) shl 14) or
                ((buf[off + 8].toInt() and 0x7F) shl  7) or
                 (buf[off + 9].toInt() and 0x7F)
            return (size + 10).toLong()
        }
        // MP3 frame sync
        for (i in 0 until len - 1) {
            val x0 = buf[off + i    ].toInt() and 0xFF
            val x1 = buf[off + i + 1].toInt() and 0xFF
            if (x0 == 0xFF && (x1 and 0xE0) == 0xE0) return i.toLong()
        }
        return 0L
    }

    override fun getUri(): Uri? = upstream.uri
    override fun close() { upstream.close(); bytesRead = 0L; resolvedOffset = startOffset }
    override fun addTransferListener(t: TransferListener) = upstream.addTransferListener(t)

    companion object {
        const val AUTO_DETECT = -1L
        const val FROM_START  =  0L
        const val WAV_OFFSET  = 44L
    }
}
