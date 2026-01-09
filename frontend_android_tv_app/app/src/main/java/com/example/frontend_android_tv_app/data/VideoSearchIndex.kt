package com.example.frontend_android_tv_app.data

/**
 * Simple in-memory search index over the static VideosRepository dataset.
 *
 * Matches:
 * - title contains query (case-insensitive)
 * - category contains query
 * - description contains query
 */
object VideoSearchIndex {

    // PUBLIC_INTERFACE
    fun search(queryRaw: String, limit: Int = 200): List<Video> {
        /** Search videos by query; returns stable results ordered by a simple relevance heuristic. */
        val query = queryRaw.trim()
        if (query.isBlank()) return emptyList()

        val q = query.lowercase()
        val scored = VideosRepository.videos.mapNotNull { v ->
            val title = v.title.lowercase()
            val cat = v.category.lowercase()
            val desc = v.description.lowercase()

            var score = 0
            // Basic relevance: prefix > contains, title > category > description
            if (title.startsWith(q)) score += 100
            else if (title.contains(q)) score += 70

            if (cat.startsWith(q)) score += 35
            else if (cat.contains(q)) score += 20

            if (desc.contains(q)) score += 10

            if (score > 0) v to score else null
        }

        return scored
            .sortedWith(compareByDescending<Pair<Video, Int>> { it.second }.thenBy { it.first.title })
            .map { it.first }
            .take(limit.coerceAtLeast(0))
    }
}
