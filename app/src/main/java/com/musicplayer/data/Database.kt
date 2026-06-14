package com.musicplayer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─── Data Models ─────────────────────────────────────────────────────────────

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String = "",
    val duration: Long = 0L,          // ms
    val path: String,
    val albumArtUri: String? = null,
    val isFavorite: Boolean = false,
    val addedDate: Long = System.currentTimeMillis(),
    val playCount: Int = 0,
    val year: Int = 0,
    val trackNumber: Int = 0
)

@Entity(tableName = "queue_items")
data class QueueItem(
    @PrimaryKey(autoGenerate = true) val queueId: Long = 0,
    val songId: Long,
    val position: Int
)

// ─── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("""
        SELECT * FROM songs WHERE
        lower(title) LIKE '%' || lower(:query) || '%' OR
        lower(artist) LIKE '%' || lower(:query) || '%' OR
        lower(album) LIKE '%' || lower(:query) || '%' OR
        lower(genre) LIKE '%' || lower(:query) || '%'
        ORDER BY title ASC
    """)
    fun searchSongs(query: String): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavorites(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY title ASC")
    fun getSongsByGenre(genre: String): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album ASC, trackNumber ASC")
    fun getSongsByArtist(artist: String): Flow<List<Song>>

    @Query("SELECT DISTINCT genre FROM songs WHERE genre != '' ORDER BY genre ASC")
    fun getAllGenres(): Flow<List<String>>

    @Query("SELECT DISTINCT artist FROM songs ORDER BY artist ASC")
    fun getAllArtists(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): Song?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Update
    suspend fun updateSong(song: Song)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE songs SET playCount = playCount + 1 WHERE id = :id")
    suspend fun incrementPlayCount(id: Long)

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongCount(): Int

    @Delete
    suspend fun deleteSong(song: Song)
}

@Dao
interface QueueDao {

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN queue_items q ON s.id = q.songId
        ORDER BY q.position ASC
    """)
    fun getQueueWithSongs(): Flow<List<Song>>

    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    suspend fun getQueueItems(): List<QueueItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: QueueItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<QueueItem>)

    @Query("DELETE FROM queue_items")
    suspend fun clearQueue()

    @Query("DELETE FROM queue_items WHERE songId = :songId")
    suspend fun removeFromQueue(songId: Long)

    @Query("SELECT COUNT(*) FROM queue_items")
    suspend fun getQueueSize(): Int
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [Song::class, QueueItem::class],
    version = 1,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun queueDao(): QueueDao

    companion object {
        @Volatile private var INSTANCE: MusicDatabase? = null

        fun getDatabase(context: android.content.Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "music_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
