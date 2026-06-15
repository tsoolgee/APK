package com.musicplayer.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow

class MusicRepository(private val db: MusicDatabase) {

    private val songDao  = db.songDao()
    private val queueDao = db.queueDao()

    val allSongs:  Flow<List<Song>>   = songDao.getAllSongs()
    val favorites: Flow<List<Song>>   = songDao.getFavorites()
    val allGenres: Flow<List<String>> = songDao.getAllGenres()
    val allArtists:Flow<List<String>> = songDao.getAllArtists()

    fun searchSongs(query: String)   = songDao.searchSongs(query)
    fun songsByGenre(genre: String)  = songDao.getSongsByGenre(genre)
    fun songsByArtist(artist: String)= songDao.getSongsByArtist(artist)

    suspend fun getSongById(id: Long) = songDao.getSongById(id)

    suspend fun syncLibrary(context: Context) {
        upsertSongs(MediaScanner.scanDevice(context))
        // also re-scan all saved folders
        FolderManager.getFolders(context).forEach { uri ->
            upsertSongs(MediaScanner.scanFolder(context, uri))
        }
    }

    suspend fun syncFolder(context: Context, uri: Uri) {
        FolderManager.addFolder(context, uri)
        upsertSongs(MediaScanner.scanFolder(context, uri))
    }

    suspend fun removeFolder(context: Context, uri: Uri) {
        FolderManager.removeFolder(context, uri)
    }

    private suspend fun upsertSongs(songs: List<Song>) {
        songs.forEach { song ->
            val existing = songDao.getSongById(song.id)
            songDao.insertSong(song.copy(
                genre      = existing?.genre      ?: song.genre,
                isFavorite = existing?.isFavorite ?: false,
                playCount  = existing?.playCount  ?: 0
            ))
        }
    }

    suspend fun updateSong(song: Song)       = songDao.updateSong(song)
    suspend fun toggleFavorite(song: Song)   = songDao.setFavorite(song.id, !song.isFavorite)
    suspend fun incrementPlayCount(id: Long) = songDao.incrementPlayCount(id)

    val queue: Flow<List<Song>> = queueDao.getQueueWithSongs()

    suspend fun setQueue(songs: List<Song>) {
        queueDao.clearQueue()
        songs.forEachIndexed { i, s -> queueDao.insertQueueItem(QueueItem(songId = s.id, position = i)) }
    }

    suspend fun addToQueue(song: Song) {
        queueDao.insertQueueItem(QueueItem(songId = song.id, position = queueDao.getQueueSize()))
    }

    suspend fun removeFromQueue(songId: Long) = queueDao.removeFromQueue(songId)
    suspend fun clearQueue()                  = queueDao.clearQueue()

    companion object {
        @Volatile private var INSTANCE: MusicRepository? = null
        fun getInstance(context: Context) = INSTANCE ?: synchronized(this) {
            MusicRepository(MusicDatabase.getDatabase(context)).also { INSTANCE = it }
        }
    }
}
