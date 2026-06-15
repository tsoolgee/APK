package com.musicplayer.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow

class MusicRepository(private val db: MusicDatabase) {

    private val songDao = db.songDao()
    private val queueDao = db.queueDao()

    // ג”€ג”€ Songs ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

    val allSongs: Flow<List<Song>> = songDao.getAllSongs()
    val favorites: Flow<List<Song>> = songDao.getFavorites()
    val allGenres: Flow<List<String>> = songDao.getAllGenres()
    val allArtists: Flow<List<String>> = songDao.getAllArtists()

    fun searchSongs(query: String): Flow<List<Song>> = songDao.searchSongs(query)
    fun songsByGenre(genre: String): Flow<List<Song>> = songDao.getSongsByGenre(genre)
    fun songsByArtist(artist: String): Flow<List<Song>> = songDao.getSongsByArtist(artist)

    suspend fun getSongById(id: Long): Song? = songDao.getSongById(id)

    /** Full device scan via MediaStore */
    suspend fun syncLibrary(context: Context) {
        val scanned = MediaScanner.scanDevice(context)
        upsertSongs(scanned)
    }

    /** Scan a specific folder chosen by the user (SAF Uri) */
    suspend fun syncFolder(context: Context, folderUri: Uri) {
        val scanned = MediaScanner.scanFolder(context, folderUri)
        upsertSongs(scanned)
    }

    private suspend fun upsertSongs(songs: List<Song>) {
        songs.forEach { song ->
            val existing = songDao.getSongById(song.id)
            songDao.insertSong(
                song.copy(
                    genre      = existing?.genre      ?: song.genre,
                    isFavorite = existing?.isFavorite ?: false,
                    playCount  = existing?.playCount  ?: 0
                )
            )
        }
    }

    suspend fun updateSong(song: Song) = songDao.updateSong(song)

    suspend fun toggleFavorite(song: Song) {
        songDao.setFavorite(song.id, !song.isFavorite)
    }

    suspend fun incrementPlayCount(id: Long) = songDao.incrementPlayCount(id)

    // ג”€ג”€ Queue ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

    val queue: Flow<List<Song>> = queueDao.getQueueWithSongs()

    suspend fun setQueue(songs: List<Song>) {
        queueDao.clearQueue()
        songs.forEachIndexed { index, song ->
            queueDao.insertQueueItem(QueueItem(songId = song.id, position = index))
        }
    }

    suspend fun addToQueue(song: Song) {
        val size = queueDao.getQueueSize()
        queueDao.insertQueueItem(QueueItem(songId = song.id, position = size))
    }

    suspend fun removeFromQueue(songId: Long) = queueDao.removeFromQueue(songId)

    suspend fun clearQueue() = queueDao.clearQueue()

    companion object {
        @Volatile private var INSTANCE: MusicRepository? = null

        fun getInstance(context: Context): MusicRepository {
            return INSTANCE ?: synchronized(this) {
                MusicRepository(MusicDatabase.getDatabase(context)).also { INSTANCE = it }
            }
        }
    }
}

