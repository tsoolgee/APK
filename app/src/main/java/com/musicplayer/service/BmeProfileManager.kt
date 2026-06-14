package com.musicplayer.service

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * ניהול פרופילי BME — שמירה וטעינה מ-SharedPreferences.
 * "BME" הוא השם שחשוף לממשק.
 */
data class BmeProfile(
    val id: String,
    val name: String,
    val key: Int,                                          // ערך פנימי (1-255)
    val startOffset: Long = BmeDecryptDataSource.AUTO_DETECT,
    val filePatterns: List<String> = emptyList(),          // ריק = כל הקבצים
    val isBuiltIn: Boolean = false
)

object BmeProfileManager {

    private const val PREFS = "bme_profiles"
    private const val COUNT = "count"

    // ── מובנים ────────────────────────────────────────────────────────────

    val BUILT_IN: List<BmeProfile> = listOf(
        BmeProfile(
            id           = "builtin_default",
            name         = "BME סטנדרטי",
            key          = 27,
            startOffset  = BmeDecryptDataSource.AUTO_DETECT,
            filePatterns = listOf("_bme"),
            isBuiltIn    = true
        )
    )

    // ── CRUD ──────────────────────────────────────────────────────────────

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAll(ctx: Context): List<BmeProfile> = BUILT_IN + getUserProfiles(ctx)

    fun getUserProfiles(ctx: Context): List<BmeProfile> {
        val p = prefs(ctx)
        return (0 until p.getInt(COUNT, 0)).mapNotNull { i ->
            val id   = p.getString("${i}_id",   null) ?: return@mapNotNull null
            val name = p.getString("${i}_name", null) ?: return@mapNotNull null
            val key  = p.getInt("${i}_key", -1).takeIf { it in 1..255 } ?: return@mapNotNull null
            BmeProfile(
                id           = id,
                name         = name,
                key          = key,
                startOffset  = p.getLong("${i}_offset", BmeDecryptDataSource.AUTO_DETECT),
                filePatterns = p.getString("${i}_patterns", "")
                                ?.split(",")?.map(String::trim)?.filter(String::isNotEmpty)
                                ?: emptyList(),
                isBuiltIn    = false
            )
        }
    }

    fun saveUserProfiles(ctx: Context, profiles: List<BmeProfile>) {
        prefs(ctx).edit {
            clear()
            putInt(COUNT, profiles.size)
            profiles.forEachIndexed { i, p ->
                putString("${i}_id",       p.id)
                putString("${i}_name",     p.name)
                putInt   ("${i}_key",      p.key)
                putLong  ("${i}_offset",   p.startOffset)
                putString("${i}_patterns", p.filePatterns.joinToString(","))
            }
        }
    }

    fun add(ctx: Context, profile: BmeProfile) {
        saveUserProfiles(ctx, getUserProfiles(ctx) + profile)
    }

    fun remove(ctx: Context, id: String) {
        saveUserProfiles(ctx, getUserProfiles(ctx).filter { it.id != id })
    }

    fun update(ctx: Context, profile: BmeProfile) {
        saveUserProfiles(ctx, getUserProfiles(ctx).map { if (it.id == profile.id) profile else it })
    }

    // ── Matching ──────────────────────────────────────────────────────────

    fun findProfile(ctx: Context, filePath: String): BmeProfile? {
        val lower = filePath.lowercase()
        // משתמש לפני מובנים (override)
        return (getUserProfiles(ctx) + BUILT_IN).firstOrNull { p ->
            p.filePatterns.isEmpty() || p.filePatterns.any { lower.contains(it.lowercase()) }
        }
    }
}
