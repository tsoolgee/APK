package com.musicplayer.service

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
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
    private val context: android.content.Context
) : DataSource.Factory {
    override fun createDataSource(): DataSource = BmeRoutingDataSource(context)
}

private class BmeRoutingDataSource(
    private val context: android.content.Context
) : DataSource {

    private var delegate: DataSource? = null

    override fun open(dataSpec: DataSpec): Long {
        val path = dataSpec.uri.path ?: dataSpec.uri.toString()
        val profile = BmeProfileManager.findProfile(context, path)
        delegate = if (profile != null) {
            BmeDecryptDataSource(
                upstream    = DefaultDataSource.Factory(context).createDataSource(),
                key         = profile.key,
                startOffset = profile.startOffset
            )
        } else {
            DefaultDataSource.Factory(context).createDataSource()
        }
        return delegate!!.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int) =
        delegate?.read(buffer, offset, length) ?: -1

    override fun getUri(): Uri? = delegate?.uri
    override fun close() { delegate?.close(); delegate = null }
    override fun addTransferListener(t: TransferListener) { delegate?.addTransferListener(t) }
}
