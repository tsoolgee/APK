package com.overlaymanager.app

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * Equivalent of %APPDATA%\OverlayManager\config.ini from the original app:
 * auto-saves the full layer list to a JSON file in internal storage and
 * auto-loads it on next launch/service start.
 */
object LayerStore {

    private const val FILE_NAME = "layers_config.json"

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun load(context: Context): MutableList<OverlayLayer> {
        val f = file(context)
        if (!f.exists()) return mutableListOf()
        return try {
            val text = f.readText()
            val arr = JSONArray(text)
            val list = mutableListOf<OverlayLayer>()
            for (i in 0 until arr.length()) {
                list.add(OverlayLayer.fromJson(arr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    @Synchronized
    fun save(context: Context, layers: List<OverlayLayer>) {
        val arr = JSONArray()
        layers.forEach { arr.put(it.toJson()) }
        file(context).writeText(arr.toString())
    }
}
