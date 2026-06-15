package com.musicplayer.service

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener

/**
 * DataSource that decrypts XOR-encoded audio on the fly.
 *
 * Auto-detect mode (startOffset == AUTO_DETECT):
 *   1. Read first chunk as-is.
 *   2. Check if it looks like a valid MP3/ID3 header.
 *   3. If not, try XOR-decoding to see if THAT looks valid.
 *   4. Decide once; apply consistently for the rest of the stream.
 */
internal class BmeDecryptDataSource(
    private val upstream: DataSource,
    private val key: Int,
    private val startOffset: Long = AUTO_DETECT
) : DataSource {

    private var resolvedOffset: Long = startOffset
    private var decryptEnabled: Boolean = (startOffset != AUTO_DETECT)
    private var bytesRead: Long = 0L
    private var firstRead = true

    override fun open(dataSpec: DataSpec): Long {
        bytesRead = 0L
        firstRead = true
        resolvedOffset = startOffset
        decryptEnabled = (startOffset != AUTO_DETECT)
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val n = upstream.read(buffer, offset, length)
        if (n <= 0) return n

        if (firstRead) {
            firstRead = false
            if (startOffset == AUTO_DETECT) {
                // Try to detect: plain first, then encrypted
                val headerBytes = buffer.copyOfRange(offset, offset + minOf(n, 10))
                if (looksLikePlainAudio(headerBytes)) {
                    // Play as-is ג€” regular MP3
                    decryptEnabled = false
                    resolvedOffset = Long.MAX_VALUE // never decrypt
                } else {
                    // Try XOR ג€” check if decoded header is valid
                    val decoded = headerBytes.map { (it.toInt() xor key).toByte() }.toByteArray()
                    if (looksLikePlainAudio(decoded)) {
                        decryptEnabled = true
                        resolvedOffset = findMp3Offset(decoded, 0, decoded.size)
                    } else {
                        // Unknown format ג€” play plain anyway
                        decryptEnabled = false
                        resolvedOffset = Long.MAX_VALUE
                    }
                }
            }
        }

        if (decryptEnabled) {
            val pos = bytesRead
            for (i in 0 until n) {
                if (pos + i >= resolvedOffset) {
                    buffer[offset + i] = (buffer[offset + i].toInt() xor key).toByte()
                }
            }
        }

        bytesRead += n
        return n
    }

    // ג”€ג”€ Heuristics ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

    /** Returns true if the first bytes match MP3 sync, ID3, fLaC, OGG, RIFF */
    private fun looksLikePlainAudio(buf: ByteArray): Boolean {
        if (buf.size < 4) return false
        val b0 = buf[0].toInt() and 0xFF
        val b1 = buf[1].toInt() and 0xFF
        val b2 = buf[2].toInt() and 0xFF
        val b3 = buf[3].toInt() and 0xFF

        // ID3v2
        if (b0 == 0x49 && b1 == 0x44 && b2 == 0x33) return true
        // MP3 sync (0xFFEx or 0xFFFx)
        if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) return true
        // fLaC
        if (b0 == 0x66 && b1 == 0x4C && b2 == 0x61 && b3 == 0x43) return true
        // OggS
        if (b0 == 0x4F && b1 == 0x67 && b2 == 0x67 && b3 == 0x53) return true
        // RIFF (WAV)
        if (b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46) return true

        return false
    }

    private fun findMp3Offset(buf: ByteArray, off: Int, len: Int): Long {
        if (len < 10) return 0L
        val b0 = buf[off].toInt() and 0xFF
        val b1 = buf[off + 1].toInt() and 0xFF
        val b2 = buf[off + 2].toInt() and 0xFF
        // ID3v2
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
    override fun close() { upstream.close(); bytesRead = 0L; resolvedOffset = startOffset; firstRead = true }
    override fun addTransferListener(t: TransferListener) = upstream.addTransferListener(t)

    companion object {
        const val AUTO_DETECT = -1L
        const val FROM_START  =  0L
        const val WAV_OFFSET  = 44L
    }
}
