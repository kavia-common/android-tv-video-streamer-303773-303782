package com.example.frontend_android_tv_app.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes

/**
 * Defines local bundled subtitle tracks per video (assets-based).
 *
 * IMPORTANT:
 * - Add your .vtt/.srt files under: app/src/main/assets/subtitles/
 * - Ensure the asset paths used below match exactly.
 */
object SubtitlesRepository {

    data class SubtitleTrack(
        val id: String,
        val label: String,
        val assetPath: String,
        val mimeType: String,
        val language: String? = null
    )

    // PUBLIC_INTERFACE
    fun tracksForVideo(videoId: String): List<SubtitleTrack> {
        /** Return the list of available local subtitle tracks for a given videoId. */
        return when (videoId) {
            // Example mapping (bundled samples). Add more as needed.
            "v1" -> listOf(
                SubtitleTrack(
                    id = "en",
                    label = "English",
                    assetPath = "subtitles/v1_en.vtt",
                    mimeType = MimeTypes.TEXT_VTT,
                    language = "en"
                )
            )
            "v2" -> listOf(
                SubtitleTrack(
                    id = "en",
                    label = "English",
                    assetPath = "subtitles/v2_en.srt",
                    mimeType = MimeTypes.APPLICATION_SUBRIP,
                    language = "en"
                )
            )
            else -> emptyList()
        }
    }

    /**
     * Build Media3 SubtitleConfiguration for an asset path.
     * ExoPlayer supports reading "asset:///" URIs when configured via DataSource internally.
     */
    fun toSubtitleConfiguration(track: SubtitleTrack): MediaItem.SubtitleConfiguration {
        val uri = Uri.parse("asset:///${track.assetPath}")
        return MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(track.mimeType)
            .setLanguage(track.language)
            .setLabel(track.label)
            .setSelectionFlags(0)
            .build()
    }
}
