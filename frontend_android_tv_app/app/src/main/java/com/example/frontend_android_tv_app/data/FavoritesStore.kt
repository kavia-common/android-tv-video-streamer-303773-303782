package com.example.frontend_android_tv_app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local-only store for tracking user's favorite videos (Watchlist).
 *
 * Persists a set of video IDs in SharedPreferences (JSON).
 * Exposes a Flow so UI can react immediately to changes (Home row, cards, details).
 */
class FavoritesStore private constructor(
    private val prefs: SharedPreferences
) {

    private val favoritesState = MutableStateFlow(readFavoritesFromPrefs())

    private fun readFavoritesFromPrefs(): Set<String> {
        val raw = prefs.getString(KEY_JSON, null) ?: return emptySet()
        return try {
            // Stored format: { "ids": ["v1","v2"] }
            val obj = JSONObject(raw)
            val arr: JSONArray = obj.optJSONArray("ids") ?: return emptySet()
            buildSet {
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i, null) ?: continue
                    if (id.isNotBlank()) add(id)
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun writeFavoritesToPrefs(ids: Set<String>) {
        val arr = JSONArray().apply {
            // Stable ordering for easier debugging.
            ids.toList().sorted().forEach { put(it) }
        }
        val obj = JSONObject().apply { put("ids", arr) }
        prefs.edit().putString(KEY_JSON, obj.toString()).apply()
    }

    private fun setFavorites(ids: Set<String>) {
        // Persist first (requirement: persist immediately), then update flow.
        writeFavoritesToPrefs(ids)
        favoritesState.value = ids
    }

    // PUBLIC_INTERFACE
    fun isFavorite(videoId: String): Boolean {
        /** Return true if the given videoId is currently marked as a favorite. */
        if (videoId.isBlank()) return false
        return favoritesState.value.contains(videoId)
    }

    // PUBLIC_INTERFACE
    fun toggleFavorite(videoId: String) {
        /** Toggle favorite state for the given videoId. */
        if (videoId.isBlank()) return
        val current = favoritesState.value
        val next = if (current.contains(videoId)) current - videoId else current + videoId
        setFavorites(next)
    }

    // PUBLIC_INTERFACE
    fun setFavorite(videoId: String, value: Boolean) {
        /** Set favorite state for the given videoId. */
        if (videoId.isBlank()) return
        val current = favoritesState.value
        val next = if (value) current + videoId else current - videoId
        setFavorites(next)
    }

    // PUBLIC_INTERFACE
    fun favoritesFlow(): Flow<Set<String>> {
        /** A reactive stream of the current favorites set. */
        return favoritesState.asStateFlow()
    }

    companion object {
        private const val PREFS_NAME = "video_favorites_store"
        private const val KEY_JSON = "favorites_json"

        // PUBLIC_INTERFACE
        fun from(context: Context): FavoritesStore {
            /** Create a FavoritesStore backed by SharedPreferences. */
            return FavoritesStore(
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
        }
    }
}
