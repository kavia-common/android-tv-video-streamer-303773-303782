package com.example.frontend_android_tv_app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Optional local-only store to remember last played index per rowKey (category id / special row keys).
 *
 * This is intentionally separate from ProgressStore. It stores only indices, not positions/durations.
 */
class LastPlayedIndexStore private constructor(
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
    fun setLastIndex(rowKey: String, index: Int) {
        /** Store last played index for a given row key. */
        if (rowKey.isBlank()) return
        val all = readAll()
        all.put(rowKey, index.coerceAtLeast(0))
        writeAll(all)
    }

    // PUBLIC_INTERFACE
    fun getLastIndex(rowKey: String): Int? {
        /** Return last played index for rowKey, or null if not set. */
        if (rowKey.isBlank()) return null
        val all = readAll()
        if (!all.has(rowKey)) return null
        val v = all.optInt(rowKey, -1)
        return if (v >= 0) v else null
    }

    companion object {
        private const val PREFS_NAME = "video_last_played_index_store"
        private const val KEY_JSON = "last_index_json"

        // PUBLIC_INTERFACE
        fun from(context: Context): LastPlayedIndexStore {
            /** Create a LastPlayedIndexStore backed by SharedPreferences. */
            return LastPlayedIndexStore(
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
        }
    }
}
