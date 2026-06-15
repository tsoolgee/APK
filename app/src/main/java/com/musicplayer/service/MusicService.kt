package com.musicplayer.service

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.FileDescriptorDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musicplayer.data.Song

class MusicService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(BmeAwareDataSourceFactory(this))
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        fun buildMediaItem(song: Song): MediaItem =
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.path)
                .build()
    }
}

// ─── Factory that routes to correct DataSource ────────────────────────────────

private class BmeAwareDataSourceFactory(
    private val context: Context
) : DataSource.Factory {
    override fun createDataSource(): DataSource = BmeRoutingDataSource(context)
}

private class BmeRoutingDataSource(
    private val context: Context
) : DataSource {

    private var delegate: DataSource? = null
    private var pfd: ParcelFileDescriptor? = null
    private val TAG = "BmeRoutingDataSource"

    override fun open(dataSpec: DataSpec): Long {
        val uriString = dataSpec.uri.toString()
        val profile   = BmeProfileManager.findProfile(context, uriString)

        // Detect if this is a SAF document tree Uri (content://...tree...)
        val isSafUri = isSafDocumentUri(dataSpec.uri)

        val upstream: DataSource = if (isSafUri) {
            // For SAF uris, open via FileDescriptor
            SafFileDescriptorDataSource(context)
        } else {
            DefaultDataSource.Factory(context).createDataSource()
        }

        delegate = if (profile != null) {
            BmeDecryptDataSource(upstream = upstream, key = profile.key, startOffset = profile.startOffset)
        } else {
            upstream
        }

        return try {
            delegate!!.open(dataSpec)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open: ${dataSpec.uri}", e)
            throw e
        }
    }

    private fun isSafDocumentUri(uri: Uri): Boolean {
        return try {
            uri.scheme == "content" &&
            (DocumentsContract.isDocumentUri(context, uri) ||
             uri.pathSegments.contains("tree") ||
             uri.pathSegments.contains("document"))
        } catch (e: Exception) { false }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int) =
        delegate?.read(buffer, offset, length) ?: -1

    override fun getUri(): Uri? = delegate?.uri
    override fun close() {
        try { delegate?.close() } catch (e: Exception) { Log.e(TAG, "close error", e) }
        try { pfd?.close() } catch (e: Exception) { }
        delegate = null
        pfd = null
    }
    override fun addTransferListener(t: TransferListener) { delegate?.addTransferListener(t) }
}

// ─── SAF DataSource via FileDescriptor ───────────────────────────────────────

private class SafFileDescriptorDataSource(
    private val context: Context
) : DataSource {

    private var pfd: ParcelFileDescriptor? = null
    private var inputStream: java.io.FileInputStream? = null
    private var uri: Uri? = null
    private val TAG = "SafFileDescDS"

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        return try {
            pfd = context.contentResolver.openFileDescriptor(dataSpec.uri, "r")
            inputStream = java.io.FileInputStream(pfd!!.fileDescriptor)
            // Skip to position if needed
            if (dataSpec.position > 0) {
                inputStream!!.skip(dataSpec.position)
            }
            pfd!!.statSize.takeIf { it > 0 } ?: C.LENGTH_UNSET.toLong()
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open SAF uri: ${dataSpec.uri}", e)
            throw androidx.media3.datasource.DataSourceException(
                e, androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
            )
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return try {
            val n = inputStream?.read(buffer, offset, length) ?: -1
            if (n == -1) C.RESULT_END_OF_INPUT else n
        } catch (e: Exception) {
            Log.e(TAG, "read error", e)
            C.RESULT_END_OF_INPUT
        }
    }

    override fun getUri(): Uri? = uri
    override fun close() {
        try { inputStream?.close() } catch (e: Exception) { }
        try { pfd?.close() } catch (e: Exception) { }
        inputStream = null
        pfd = null
    }
    override fun addTransferListener(t: TransferListener) {}
}
