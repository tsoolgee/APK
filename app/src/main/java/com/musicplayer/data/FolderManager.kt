package com.musicplayer.data

import android.content.Context
import android.net.Uri

/**
 * Persists the list of user-chosen folder URIs in SharedPreferences.
 */
object FolderManager {

    private const val PREFS = "scanned_folders"
    private const val KEY   = "folder_uris"

    fun getFolders(context: Context): List<Uri> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "") ?: ""
        return raw.split("|").filter(String::isNotEmpty).map(Uri::parse)
    }

    fun addFolder(context: Context, uri: Uri) {
        val current = getFolders(context).map(Uri::toString).toMutableSet()
        current.add(uri.toString())
        save(context, current.toList())
    }

    fun removeFolder(context: Context, uri: Uri) {
        val current = getFolders(context).map(Uri::toString).toMutableList()
        current.remove(uri.toString())
        save(context, current)
    }

    private fun save(context: Context, uris: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, uris.joinToString("|")).apply()
    }
}
