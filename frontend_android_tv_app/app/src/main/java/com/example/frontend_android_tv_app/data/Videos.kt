package com.example.frontend_android_tv_app.data

/**
 * Static, in-repo dataset for the Android TV app.
 * No backend/database required.
 */
data class Video(
    val id: String,
    val title: String,
    val category: String,
    val thumbnailUrl: String,
    val description: String,
    val videoUrl: String
)

object VideosRepository {
    val categories: List<String> = listOf(
        "Trending",
        "Sports",
        "Music",
        "News"
    )

    val videos: List<Video> = listOf(
        // Trending
        Video(
            id = "v1",
            title = "Big Buck Bunny (Trailer)",
            category = "Trending",
            thumbnailUrl = "https://picsum.photos/seed/bunny1/640/360",
            description = "A friendly introduction to the classic open movie. Great for testing playback.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        ),
        Video(
            id = "v2",
            title = "Elephant Dream",
            category = "Trending",
            thumbnailUrl = "https://picsum.photos/seed/elephant/640/360",
            description = "The first open movie by the Blender Foundation.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
        ),
        Video(
            id = "v3",
            title = "For Bigger Blazes",
            category = "Trending",
            thumbnailUrl = "https://picsum.photos/seed/blazes/640/360",
            description = "Sample video for streaming UI and fast seeking.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
        ),
        Video(
            id = "v4",
            title = "For Bigger Escape",
            category = "Trending",
            thumbnailUrl = "https://picsum.photos/seed/escape/640/360",
            description = "Sample MP4 with good motion for playback testing.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
        ),

        // Sports
        Video(
            id = "v5",
            title = "For Bigger Fun",
            category = "Sports",
            thumbnailUrl = "https://picsum.photos/seed/fun/640/360",
            description = "A short sample suitable for Sports category browsing.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4"
        ),
        Video(
            id = "v6",
            title = "For Bigger Joyrides",
            category = "Sports",
            thumbnailUrl = "https://picsum.photos/seed/joyrides/640/360",
            description = "High-energy clip for remote-control seeking tests.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4"
        ),
        Video(
            id = "v7",
            title = "Sintel",
            category = "Sports",
            thumbnailUrl = "https://picsum.photos/seed/sintel/640/360",
            description = "A longer-form open movie for sustained playback tests.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
        ),
        Video(
            id = "v8",
            title = "Tears of Steel",
            category = "Sports",
            thumbnailUrl = "https://picsum.photos/seed/steel/640/360",
            description = "Cinematic sample for stress-testing decode and UI overlays.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
        ),

        // Music
        Video(
            id = "v9",
            title = "For Bigger Meltdowns",
            category = "Music",
            thumbnailUrl = "https://picsum.photos/seed/meltdowns/640/360",
            description = "Good for testing quick pause/play and UI responsiveness.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4"
        ),
        Video(
            id = "v10",
            title = "Subaru Outback On Street And Dirt",
            category = "Music",
            thumbnailUrl = "https://picsum.photos/seed/subaru/640/360",
            description = "A sample with varied scenes to test scrubbing and time display.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4"
        ),
        Video(
            id = "v11",
            title = "We Are Going On Bullrun",
            category = "Music",
            thumbnailUrl = "https://picsum.photos/seed/bullrun/640/360",
            description = "A longer sample video, useful for progress bar and seeking.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4"
        ),
        Video(
            id = "v12",
            title = "Volkswagen GTI Review",
            category = "Music",
            thumbnailUrl = "https://picsum.photos/seed/vw/640/360",
            description = "Sample content to verify category row browsing and details display.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/VolkswagenGTIReview.mp4"
        ),

        // News
        Video(
            id = "v13",
            title = "For Bigger Subtitles",
            category = "News",
            thumbnailUrl = "https://picsum.photos/seed/subtitles/640/360",
            description = "Useful for verifying overlay readability on light-themed UI.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerSubtitles.mp4"
        ),
        Video(
            id = "v14",
            title = "For Bigger Subtitles (Blender)",
            category = "News",
            thumbnailUrl = "https://picsum.photos/seed/subtitles2/640/360",
            description = "Another sample to test switching between items quickly.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerSubtitlesBlender.mp4"
        ),
        Video(
            id = "v15",
            title = "For Bigger Subtitles (Sintel)",
            category = "News",
            thumbnailUrl = "https://picsum.photos/seed/subtitles3/640/360",
            description = "Good for validating playback controls and time text contrast.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerSubtitlesSintel.mp4"
        ),
        Video(
            id = "v16",
            title = "For Bigger Escape (again)",
            category = "News",
            thumbnailUrl = "https://picsum.photos/seed/escape2/640/360",
            description = "A final sample for a fuller browsing grid and wrap-around focus tests.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
        )
    )

    fun videosForCategory(category: String): List<Video> =
        videos.filter { it.category == category }
}
