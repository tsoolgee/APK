package com.musicplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.musicplayer.data.MusicRepository
import com.musicplayer.data.Song
import com.musicplayer.service.MusicService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val progress: Long = 0L,          // ms
    val duration: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,  // OFF=0, ONE=1, ALL=2
    val isLoading: Boolean = false
)

data class LibraryState(
    val songs: List<Song> = emptyList(),
    val isScanning: Boolean = false,
    val searchQuery: String = "",
    val filteredSongs: List<Song> = emptyList(),
    val selectedGenre: String? = null,
    val selectedArtist: String? = null
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MusicRepository.getInstance(application)

    // ── Player state ──────────────────────────────────────────────────────

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    // ── Library state ─────────────────────────────────────────────────────

    private val _libraryState = MutableStateFlow(LibraryState())
    val libraryState: StateFlow<LibraryState> = _libraryState.asStateFlow()

    // ── Collections ───────────────────────────────────────────────────────

    val favorites: StateFlow<List<Song>> = repo.favorites
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val queue: StateFlow<List<Song>> = repo.queue
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val genres: StateFlow<List<String>> = repo.allGenres
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val artists: StateFlow<List<String>> = repo.allArtists
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ── Edit song dialog ──────────────────────────────────────────────────

    private val _editSong = MutableStateFlow<Song?>(null)
    val editSong: StateFlow<Song?> = _editSong.asStateFlow()

    // ── MediaController ───────────────────────────────────────────────────

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    init {
        loadLibrary()
        connectController()
        startProgressPoller()
    }

    private fun connectController() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), MusicService::class.java)
        )
        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            controller?.addListener(playerListener)
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId?.toLongOrNull() ?: return
            viewModelScope.launch {
                val song = repo.getSongById(id)
                _playerState.value = _playerState.value.copy(
                    currentSong = song,
                    duration = controller?.duration ?: 0L
                )
                if (song != null) repo.incrementPlayCount(id)
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _playerState.value = _playerState.value.copy(shuffleEnabled = shuffleModeEnabled)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _playerState.value = _playerState.value.copy(repeatMode = repeatMode)
        }
    }

    private fun startProgressPoller() {
        viewModelScope.launch {
            while (true) {
                controller?.let { c ->
                    if (c.isPlaying) {
                        _playerState.value = _playerState.value.copy(
                            progress = c.currentPosition,
                            duration = c.duration.coerceAtLeast(0)
                        )
                    }
                }
                delay(500)
            }
        }
    }

    // ── Library actions ───────────────────────────────────────────────────

    private fun loadLibrary() {
        viewModelScope.launch {
            repo.allSongs.collect { songs ->
                val state = _libraryState.value
                val filtered = filterSongs(songs, state.searchQuery)
                _libraryState.value = state.copy(songs = songs, filteredSongs = filtered)
            }
        }
    }

    fun syncLibrary() {
        viewModelScope.launch {
            _libraryState.value = _libraryState.value.copy(isScanning = true)
            repo.syncLibrary(getApplication())
            _libraryState.value = _libraryState.value.copy(isScanning = false)
        }
    }

    fun setSearchQuery(query: String) {
        val state = _libraryState.value
        val filtered = filterSongs(state.songs, query)
        _libraryState.value = state.copy(searchQuery = query, filteredSongs = filtered)
    }

    private fun filterSongs(songs: List<Song>, query: String): List<Song> {
        if (query.isBlank()) return songs
        val q = query.lowercase()
        return songs.filter {
            it.title.lowercase().contains(q) ||
            it.artist.lowercase().contains(q) ||
            it.album.lowercase().contains(q) ||
            it.genre.lowercase().contains(q)
        }
    }

    fun filterByGenre(genre: String?) {
        _libraryState.value = _libraryState.value.copy(selectedGenre = genre, selectedArtist = null)
    }

    fun filterByArtist(artist: String?) {
        _libraryState.value = _libraryState.value.copy(selectedArtist = artist, selectedGenre = null)
    }

    // ── Playback actions ──────────────────────────────────────────────────

    fun playSong(song: Song, songList: List<Song> = emptyList()) {
        viewModelScope.launch {
            val playlist = if (songList.isEmpty()) listOf(song) else songList
            repo.setQueue(playlist)

            val items = playlist.map { MusicService.buildMediaItem(it) }
            val startIndex = playlist.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

            controller?.apply {
                setMediaItems(items, startIndex, 0)
                prepare()
                play()
            }

            _playerState.value = _playerState.value.copy(currentSong = song, isPlaying = true)
        }
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun skipNext() { controller?.seekToNextMediaItem() }
    fun skipPrevious() {
        controller?.let {
            if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem()
        }
    }

    fun seekTo(position: Long) {
        controller?.seekTo(position)
        _playerState.value = _playerState.value.copy(progress = position)
    }

    fun toggleShuffle() {
        controller?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
        }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // ── Favorites ─────────────────────────────────────────────────────────

    fun toggleFavorite(song: Song) {
        viewModelScope.launch { repo.toggleFavorite(song) }
    }

    // ── Queue ─────────────────────────────────────────────────────────────

    fun addToQueue(song: Song) {
        viewModelScope.launch {
            repo.addToQueue(song)
            controller?.let { c ->
                c.addMediaItem(MusicService.buildMediaItem(song))
            }
        }
    }

    fun removeFromQueue(song: Song) {
        viewModelScope.launch { repo.removeFromQueue(song.id) }
    }

    fun clearQueue() {
        viewModelScope.launch {
            repo.clearQueue()
            controller?.clearMediaItems()
        }
    }

    // ── Edit song metadata ────────────────────────────────────────────────

    fun openEditSong(song: Song) { _editSong.value = song }
    fun closeEditSong() { _editSong.value = null }

    fun saveSong(song: Song) {
        viewModelScope.launch {
            repo.updateSong(song)
            _editSong.value = null
            // Refresh current song if it's the one being edited
            if (_playerState.value.currentSong?.id == song.id) {
                _playerState.value = _playerState.value.copy(currentSong = song)
            }
        }
    }

    override fun onCleared() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onCleared()
    }
}
