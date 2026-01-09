package com.example.frontend_android_tv_app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Local-only store for tracking per-video playback progress.
 *
 * Persists: positionMs, durationMs, lastPlayedAt.
 * Stored in SharedPreferences as a JSON map keyed by videoId.
 */
class ProgressStore private constructor(
    private val prefs: SharedPreferences
) {

    data class Progress(
        val videoId: String,
        val positionMs: Long,
        val durationMs: Long,
        val lastPlayedAt: Long
    ) {
        val percent: Int
            get() = if (durationMs <= 0L) 0 else ((positionMs.toDouble() / durationMs.toDouble()) * 100.0)
                .coerceIn(0.0, 100.0)
                .roundToInt()
    }

    data class VideoProgress(
        val video: Video,
        val progress: Progress
    ) {
        val percent: Int get() = progress.percent
    }

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
    fun getProgress(videoId: String): Progress? {
        /** Get saved progress for a video ID, or null if none exists. */
        val all = readAll()
        val obj = all.optJSONObject(videoId) ?: return null
        val position = obj.optLong("positionMs", 0L)
        val duration = obj.optLong("durationMs", 0L)
        val lastPlayedAt = obj.optLong("lastPlayedAt", 0L)
        if (duration <= 0L) return null
        return Progress(videoId = videoId, positionMs = position, durationMs = duration, lastPlayedAt = lastPlayedAt)
    }

    // PUBLIC_INTERFACE
    fun setProgress(videoId: String, positionMs: Long, durationMs: Long) {
        /** Save progress for a video ID (position and duration in ms). */
        if (durationMs <= 0L) return

        val all = readAll()
        val obj = JSONObject().apply {
            put("positionMs", positionMs.coerceAtLeast(0L))
            put("durationMs", durationMs.coerceAtLeast(0L))
            put("lastPlayedAt", System.currentTimeMillis())
        }
        all.put(videoId, obj)
        writeAll(all)
    }

    // PUBLIC_INTERFACE
    fun clearProgress(videoId: String) {
        /** Remove saved progress for a video ID. */
        val all = readAll()
        all.remove(videoId)
        writeAll(all)
    }

    // PUBLIC_INTERFACE
    fun listInProgress(videosRepo: VideosRepository = VideosRepository): List<VideoProgress> {
        /**
         * Return a list of in-progress items (< 95% watched) sorted by lastPlayedAt desc.
         * Since the app is static dataset only, we resolve video IDs via VideosRepository.
         */
        val all = readAll()
        val out = mutableListOf<VideoProgress>()

        val allVideosById = videosRepo.videos.associateBy { it.id }

        val keys = all.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val video = allVideosById[id] ?: continue
            val progress = getProgress(id) ?: continue
            if (progress.durationMs <= 0L) continue

            // Filter out completed items
            if (progress.percent >= COMPLETE_PERCENT) continue

            out.add(VideoProgress(video = video, progress = progress))
        }

        return out.sortedByDescending { it.progress.lastPlayedAt }
    }

    companion object {
        private const val PREFS_NAME = "video_progress_store"
        private const val KEY_JSON = "progress_json"
        private const val COMPLETE_PERCENT = 95

        // PUBLIC_INTERFACE
        fun from(context: Context): ProgressStore {
            /** Create a ProgressStore backed by SharedPreferences. */
            return ProgressStore(
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
        }
    }
}
