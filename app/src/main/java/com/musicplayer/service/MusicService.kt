package com.musicplayer.service

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DefaultDataSource
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

// ─── Factory ──────────────────────────────────────────────────────────────────

private class BmeAwareDataSourceFactory(
    private val context: Context
) : DataSource.Factory {
    override fun createDataSource(): DataSource = BmeRoutingDataSource(context)
}

// ─── Router: picks plain / SAF / BME datasource ───────────────────────────────

private class BmeRoutingDataSource(
    private val context: Context
) : DataSource {

    private var delegate: DataSource? = null
    private val TAG = "BmeRoutingDS"

    override fun open(dataSpec: DataSpec): Long {
        val uriStr  = dataSpec.uri.toString()
        val profile = BmeProfileManager.findProfile(context, uriStr)
        val isSaf   = isSafUri(dataSpec.uri)

        val upstream: DataSource = if (isSaf) {
            SafDataSource(context)
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
            Log.e(TAG, "open failed: ${dataSpec.uri}", e)
            throw e
        }
    }

    private fun isSafUri(uri: Uri): Boolean = try {
        uri.scheme == "content" && (
            uri.pathSegments.contains("tree") ||
            uri.pathSegments.contains("document") ||
            DocumentsContract.isDocumentUri(context, uri)
        )
    } catch (e: Exception) { false }

    override fun read(buffer: ByteArray, offset: Int, length: Int) =
        delegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = delegate?.uri
    override fun close() { try { delegate?.close() } catch (e: Exception) { Log.e(TAG, "close", e) }; delegate = null }
    override fun addTransferListener(t: TransferListener) { delegate?.addTransferListener(t) }
}

// ─── SAF DataSource (FileDescriptor-based, no missing class) ─────────────────

private class SafDataSource(private val context: Context) : DataSource {

    private var pfd: ParcelFileDescriptor? = null
    private var stream: java.io.FileInputStream? = null
    private var uri: Uri? = null
    private val TAG = "SafDataSource"

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        return try {
            val descriptor = context.contentResolver.openFileDescriptor(dataSpec.uri, "r")
                ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
            pfd    = descriptor
            stream = java.io.FileInputStream(descriptor.fileDescriptor)
            if (dataSpec.position > 0) stream!!.skip(dataSpec.position)
            descriptor.statSize.takeIf { it > 0 } ?: C.LENGTH_UNSET.toLong()
        } catch (e: DataSourceException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open: ${dataSpec.uri}", e)
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return try {
            val n = stream?.read(buffer, offset, length) ?: return C.RESULT_END_OF_INPUT
            if (n == -1) C.RESULT_END_OF_INPUT else n
        } catch (e: Exception) {
            Log.e(TAG, "read error", e)
            C.RESULT_END_OF_INPUT
        }
    }

    override fun getUri(): Uri? = uri
    override fun close() {
        try { stream?.close() } catch (e: Exception) { }
        try { pfd?.close()    } catch (e: Exception) { }
        stream = null; pfd = null
    }
    override fun addTransferListener(t: TransferListener) {}
}
