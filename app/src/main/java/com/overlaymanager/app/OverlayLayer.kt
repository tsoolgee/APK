package com.overlaymanager.app

import org.json.JSONObject
import java.util.UUID

/**
 * Horizontal / vertical anchor, mirrors the 3x3 anchor grid from the original app
 * (left/center/right, top/middle/bottom) combined with a percentage offset from
 * the target edge.
 */
enum class AnchorH { LEFT, CENTER, RIGHT }
enum class AnchorV { TOP, MIDDLE, BOTTOM }

/**
 * One floating overlay ("layer" / "שכבה"). Equivalent of one row in the original
 * app's layer list + everything editable in its form.
 */
data class OverlayLayer(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "שכבה חדשה",
    var imageUri: String? = null,      // persisted content:// Uri (with permission taken)
    var widthPx: Int = 300,
    var heightPx: Int = 300,
    var anchorH: AnchorH = AnchorH.RIGHT,
    var anchorV: AnchorV = AnchorV.BOTTOM,
    var offsetXPercent: Float = 2f,    // distance from the anchored horizontal edge, in % of screen width
    var offsetYPercent: Float = 2f,    // distance from the anchored vertical edge, in % of screen height
    var alpha: Int = 200,              // 0-255, like the original transparency slider
    var tintColor: Int? = null,        // optional ARGB tint, null = no tint
    var locked: Boolean = true,        // true = click-through (FLAG_NOT_TOUCHABLE), false = draggable/resizable
    var enabled: Boolean = true        // layer is shown when the service runs
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("imageUri", imageUri ?: JSONObject.NULL)
        put("widthPx", widthPx)
        put("heightPx", heightPx)
        put("anchorH", anchorH.name)
        put("anchorV", anchorV.name)
        put("offsetXPercent", offsetXPercent.toDouble())
        put("offsetYPercent", offsetYPercent.toDouble())
        put("alpha", alpha)
        put("tintColor", tintColor ?: JSONObject.NULL)
        put("locked", locked)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(o: JSONObject): OverlayLayer = OverlayLayer(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "שכבה"),
            imageUri = if (o.isNull("imageUri")) null else o.optString("imageUri"),
            widthPx = o.optInt("widthPx", 300),
            heightPx = o.optInt("heightPx", 300),
            anchorH = runCatching { AnchorH.valueOf(o.optString("anchorH", "RIGHT")) }.getOrDefault(AnchorH.RIGHT),
            anchorV = runCatching { AnchorV.valueOf(o.optString("anchorV", "BOTTOM")) }.getOrDefault(AnchorV.BOTTOM),
            offsetXPercent = o.optDouble("offsetXPercent", 2.0).toFloat(),
            offsetYPercent = o.optDouble("offsetYPercent", 2.0).toFloat(),
            alpha = o.optInt("alpha", 200),
            tintColor = if (o.isNull("tintColor")) null else o.optInt("tintColor"),
            locked = o.optBoolean("locked", true),
            enabled = o.optBoolean("enabled", true)
        )
    }
}
