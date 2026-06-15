package com.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaScanner {

    private const val TAG = "MediaScanner"

    // ── Full device scan ──────────────────────────────────────────────────

    suspend fun scanDevice(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.TRACK,
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 " +
                            "AND ${MediaStore.Audio.Media.DURATION} > 10000"

            context.contentResolver.query(
                collection, projection, selection, null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor -> songs.addAll(cursorToSongs(cursor)) }
        } catch (e: Exception) {
            Log.e(TAG, "scanDevice failed", e)
        }
        songs
    }

    // ── SAF folder scan — recursive, safe ────────────────────────────────

    suspend fun scanFolder(context: Context, treeUri: Uri): List<Song> =
        withContext(Dispatchers.IO) {
            val songs = mutableListOf<Song>()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                // SAF tree Uris require API 21+
                Log.w(TAG, "SAF not supported below API 21")
                return@withContext songs
            }
            try {
                // Verify we still have permission
                val perms = context.contentResolver.persistedUriPermissions
                val hasPermission = perms.any { it.uri == treeUri && it.isReadPermission }
                if (!hasPermission) {
                    Log.w(TAG, "No persisted permission for $treeUri")
                    return@withContext songs
                }
                val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                scanDir(context, treeUri, rootDocId, songs)
            } catch (e: Exception) {
                Log.e(TAG, "scanFolder failed for $treeUri", e)
            }
            songs
        }

    private fun scanDir(
        context: Context,
        treeUri: Uri,
        docId: String,
        out: MutableList<Song>
    ) {
        val childrenUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        } catch (e: Exception) {
            Log.e(TAG, "buildChildDocumentsUriUsingTree failed: $docId", e)
            return
        }

        try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null
            )?.use { cursor ->
                val idCol   = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    try {
                        val childDocId = cursor.getString(idCol)   ?: continue
                        val name       = cursor.getString(nameCol) ?: continue
                        val mime       = cursor.getString(mimeCol) ?: continue

                        when {
                            mime == DocumentsContract.Document.MIME_TYPE_DIR ->
                                scanDir(context, treeUri, childDocId, out)

                            isAudioFile(mime, name) -> {
                                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                                val path    = fileUri.toString()
                                val id      = (treeUri.toString() + childDocId)
                                    .hashCode().toLong() and 0x7FFFFFFFFFFFFFFFL

                                out.add(Song(
                                    id          = id,
                                    title       = name.substringBeforeLast("."),
                                    artist      = "Unknown Artist",
                                    album       = "Unknown Album",
                                    duration    = 0L,
                                    path        = path,
                                    albumArtUri = null
                                ))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing row", e)
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scanning $childrenUri", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning $childrenUri", e)
        }
    }

    private fun isAudioFile(mime: String, name: String): Boolean {
        if (mime.startsWith("audio/")) return true
        val lower = name.lowercase()
        return lower.endsWith(".mp3")  || lower.endsWith(".flac") ||
               lower.endsWith(".ogg")  || lower.endsWith(".m4a")  ||
               lower.endsWith(".wav")  || lower.endsWith(".aac")  ||
               lower.endsWith(".bme")
    }

    // ── MediaStore cursor → Song list ─────────────────────────────────────

    private fun cursorToSongs(cursor: Cursor): List<Song> {
        val songs       = mutableListOf<Song>()
        val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumIdCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val dataCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val yearCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
        val trackCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

        while (cursor.moveToNext()) {
            try {
                val id      = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                songs.add(Song(
                    id          = id,
                    title       = cursor.getString(titleCol)  ?: "Unknown",
                    artist      = cursor.getString(artistCol) ?: "Unknown Artist",
                    album       = cursor.getString(albumCol)  ?: "Unknown Album",
                    duration    = cursor.getLong(durationCol),
                    path        = cursor.getString(dataCol)   ?: "",
                    albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), albumId
                    ).toString(),
                    year        = cursor.getInt(yearCol),
                    trackNumber = cursor.getInt(trackCol)
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Error reading cursor row", e)
            }
        }
        return songs
    }

    fun formatDuration(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}
