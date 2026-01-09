package com.example.frontend_android_tv_app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Persist per-video subtitle selection in SharedPreferences.
 *
 * Stored JSON format:
 * {
 *   "v1": { "trackId": "en" },
 *   "v2": { "trackId": "__off__" }
 * }
 */
class SubtitleSelectionStore private constructor(
    private val prefs: SharedPreferences
) {
    private fun readAll(): JSONObject {
        val raw = prefs.getString(KEY_JSON, null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun writeAll(json: JSONObject) {
        prefs.edit().putString(KEY_JSON, json.toString()).apply()
    }

    // PUBLIC_INTERFACE
    fun getSelectedTrackId(videoId: String): String? {
        /** Return the saved trackId for this video, or null if none. "__off__" means Off. */
        if (videoId.isBlank()) return null
        val all = readAll()
        val obj = all.optJSONObject(videoId) ?: return null
        // JSONObject#optString defaultValue cannot be null (expects String). Handle null explicitly.
        val raw = obj.optString("trackId", "")
        return raw.ifBlank { null }
    }

    // PUBLIC_INTERFACE
    fun setSelectedTrackId(videoId: String, trackId: String?) {
        /**
         * Persist selection for a video.
         * - trackId == null clears stored selection
         * - trackId == "__off__" means explicitly Off
         */
        if (videoId.isBlank()) return
        val all = readAll()

        if (trackId == null) {
            all.remove(videoId)
            writeAll(all)
            return
        }

        val obj = JSONObject().apply {
            put("trackId", trackId)
        }
        all.put(videoId, obj)
        writeAll(all)
    }

    companion object {
        const val TRACK_ID_OFF = "__off__"
        private const val PREFS_NAME = "subtitle_selection_store"
        private const val KEY_JSON = "subtitle_selection_json"

        // PUBLIC_INTERFACE
        fun from(context: Context): SubtitleSelectionStore {
            /** Create a SubtitleSelectionStore backed by SharedPreferences. */
            return SubtitleSelectionStore(
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
        }
    }
}
