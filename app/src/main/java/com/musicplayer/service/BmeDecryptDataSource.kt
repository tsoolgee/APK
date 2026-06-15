package com.musicplayer.service

import android.net.Uri
import android.util.Log
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * XOR-decrypting DataSource wrapper.
 *
 * AUTO_DETECT mode:
 *  - Reads first chunk as-is
 *  - If header looks like valid audio → play plain
 *  - Else XOR-decode header → if valid → play encrypted
 *  - Else → play plain anyway (graceful fallback)
 */
internal class BmeDecryptDataSource(
    private val upstream: DataSource,
    private val key: Int,
    private val startOffset: Long = AUTO_DETECT
) : DataSource {

    private var resolvedOffset: Long  = startOffset
    private var decryptEnabled: Boolean = (startOffset != AUTO_DETECT)
    private var bytesRead: Long = 0L
    private var decided   = false
    private val TAG = "BmeDecrypt"

    override fun open(dataSpec: DataSpec): Long {
        bytesRead       = 0L
        decided         = false
        resolvedOffset  = startOffset
        decryptEnabled  = (startOffset != AUTO_DETECT)
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val n = upstream.read(buffer, offset, length)
        if (n <= 0) return n

        if (!decided && startOffset == AUTO_DETECT) {
            decided = true
            val header = buffer.sliceArray(offset until (offset + minOf(n, 16)))
            when {
                looksLikeAudio(header) -> {
                    // Plain audio
                    decryptEnabled = false
                    resolvedOffset = Long.MAX_VALUE
                    Log.d(TAG, "Auto-detect: PLAIN")
                }
                looksLikeAudio(xorBytes(header, key)) -> {
                    // XOR-encrypted
                    decryptEnabled = true
                    resolvedOffset = findAudioStart(xorBytes(header, key))
                    Log.d(TAG, "Auto-detect: ENCRYPTED, offset=$resolvedOffset")
                }
                else -> {
                    // Unknown — try plain
                    decryptEnabled = false
                    resolvedOffset = Long.MAX_VALUE
                    Log.w(TAG, "Auto-detect: UNKNOWN, playing plain")
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

    private fun xorBytes(buf: ByteArray, k: Int) = ByteArray(buf.size) { (buf[it].toInt() xor k).toByte() }

    private fun looksLikeAudio(b: ByteArray): Boolean {
        if (b.size < 4) return false
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF
        val b2 = b[2].toInt() and 0xFF
        val b3 = b[3].toInt() and 0xFF
        // ID3v2
        if (b0 == 0x49 && b1 == 0x44 && b2 == 0x33) return true
        // MP3 sync
        if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) return true
        // fLaC
        if (b0 == 0x66 && b1 == 0x4C && b2 == 0x61 && b3 == 0x43) return true
        // OggS
        if (b0 == 0x4F && b1 == 0x67 && b2 == 0x67 && b3 == 0x53) return true
        // RIFF/WAV
        if (b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46) return true
        return false
    }

    private fun findAudioStart(b: ByteArray): Long {
        if (b.size < 10) return 0L
        // ID3v2 — skip tag
        if ((b[0].toInt() and 0xFF) == 0x49 &&
            (b[1].toInt() and 0xFF) == 0x44 &&
            (b[2].toInt() and 0xFF) == 0x33) {
            val size = ((b[6].toInt() and 0x7F) shl 21) or
                       ((b[7].toInt() and 0x7F) shl 14) or
                       ((b[8].toInt() and 0x7F) shl  7) or
                        (b[9].toInt() and 0x7F)
            return (size + 10).toLong()
        }
        // Find MP3 sync
        for (i in 0 until b.size - 1) {
            if ((b[i].toInt() and 0xFF) == 0xFF &&
                (b[i + 1].toInt() and 0xE0) == 0xE0) return i.toLong()
        }
        return 0L
    }

    override fun getUri(): Uri? = upstream.uri
    override fun close() {
        upstream.close()
        bytesRead = 0L
        decided   = false
        resolvedOffset = startOffset
    }
    override fun addTransferListener(t: TransferListener) = upstream.addTransferListener(t)

    companion object {
        const val AUTO_DETECT = -1L
        const val FROM_START  =  0L
        const val WAV_OFFSET  = 44L
    }
}
