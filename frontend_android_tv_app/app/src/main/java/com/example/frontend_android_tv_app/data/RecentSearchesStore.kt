package com.example.frontend_android_tv_app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local-only store for recent search queries.
 *
 * Persists up to MAX_ITEMS queries in SharedPreferences (JSON).
 * This is used by Search screen to show recent searches when query is empty.
 */
class RecentSearchesStore private constructor(
    private val prefs: SharedPreferences
) {

    private fun readList(): List<String> {
        val raw = prefs.getString(KEY_JSON, null) ?: return emptyList()
        return try {
            val obj = JSONObject(raw)
            val arr: JSONArray = obj.optJSONArray("items") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val q = arr.optString(i, "").trim()
                    if (q.isNotBlank()) add(q)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeList(items: List<String>) {
        val arr = JSONArray().apply {
            items.take(MAX_ITEMS).forEach { put(it) }
        }
        val obj = JSONObject().apply { put("items", arr) }
        prefs.edit().putString(KEY_JSON, obj.toString()).apply()
    }

    // PUBLIC_INTERFACE
    fun list(): List<String> {
        /** Return recent searches (most recent first). */
        return readList()
    }

    // PUBLIC_INTERFACE
    fun push(query: String) {
        /** Add a query to the recent list, de-duplicated and capped to MAX_ITEMS. */
        val q = query.trim()
        if (q.isBlank()) return
        val current = readList()
        val next = buildList {
            add(q)
            current.forEach { if (!it.equals(q, ignoreCase = true)) add(it) }
        }
        writeList(next.take(MAX_ITEMS))
    }

    // PUBLIC_INTERFACE
    fun clear() {
        /** Clear all stored recent searches. */
        writeList(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "recent_searches_store"
        private const val KEY_JSON = "recent_searches_json"
        private const val MAX_ITEMS = 5

        // PUBLIC_INTERFACE
        fun from(context: Context): RecentSearchesStore {
            /** Create a RecentSearchesStore backed by SharedPreferences. */
            return RecentSearchesStore(
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
        }
    }
}
