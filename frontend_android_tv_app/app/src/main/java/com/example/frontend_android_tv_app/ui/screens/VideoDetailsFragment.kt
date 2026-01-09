package com.example.frontend_android_tv_app.ui.screens

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.frontend_android_tv_app.R
import com.example.frontend_android_tv_app.data.FavoritesStore
import com.example.frontend_android_tv_app.data.ProgressStore
import com.example.frontend_android_tv_app.data.SubtitleSelectionStore
import com.example.frontend_android_tv_app.data.SubtitlesRepository
import com.example.frontend_android_tv_app.data.Video
import com.example.frontend_android_tv_app.data.VideoParcelable
import com.example.frontend_android_tv_app.data.VideosRepository
import com.example.frontend_android_tv_app.data.toParcelable
import com.example.frontend_android_tv_app.data.toVideo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Video details screen.
 * Shows title/description and primary actions: Resume (if applicable), Play, and Favorite toggle.
 */
class VideoDetailsFragment : Fragment() {

    interface Host {
        fun playVideo(video: Video)
        fun playVideo(video: Video, resumePositionMs: Long)

        // PUBLIC_INTERFACE
        fun playVideoWithContext(
            video: Video,
            resumePositionMs: Long,
            rowKey: String,
            rowVideoIds: ArrayList<String>,
            currentIndex: Int
        )

        fun goBack()
    }

    private lateinit var titleText: TextView
    private lateinit var descText: TextView
    private lateinit var playButton: Button
    private lateinit var resumeButton: Button
    private lateinit var subtitlesButton: Button
    private lateinit var favoriteButton: Button
    private lateinit var resumeHint: TextView

    private lateinit var video: Video
    private lateinit var progressStore: ProgressStore
    private lateinit var favoritesStore: FavoritesStore
    private lateinit var subtitleSelectionStore: SubtitleSelectionStore

    private var favoritesCollectJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parcel = requireArguments().getParcelable<VideoParcelable>(ARG_VIDEO)
            ?: error("VideoDetailsFragment requires a Video argument")
        video = parcel.toVideo()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_video_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        titleText = view.findViewById(R.id.details_title)
        descText = view.findViewById(R.id.details_description)
        playButton = view.findViewById(R.id.details_play_button)
        resumeButton = view.findViewById(R.id.details_resume_button)
        subtitlesButton = view.findViewById(R.id.details_subtitles_button)
        favoriteButton = view.findViewById(R.id.details_favorite_button)
        resumeHint = view.findViewById(R.id.details_resume_hint)

        progressStore = ProgressStore.from(requireContext())
        favoritesStore = FavoritesStore.from(requireContext())
        subtitleSelectionStore = SubtitleSelectionStore.from(requireContext())

        titleText.text = video.title
        descText.text = video.description

        // Subtitles button: show only if there are local tracks for this video.
        val tracks = SubtitlesRepository.tracksForVideo(video.id)
        if (tracks.isEmpty()) {
            subtitlesButton.visibility = View.GONE
        } else {
            subtitlesButton.visibility = View.VISIBLE
            subtitlesButton.setOnClickListener {
                showSubtitleSelectorDialog(videoId = video.id)
            }
        }

        val rowList = VideosRepository.videosForCategory(video.category)
        val ids = ArrayList(rowList.map { it.id })
        val idx = rowList.indexOfFirst { it.id == video.id }.let { if (it >= 0) it else 0 }

        playButton.setOnClickListener {
            (activity as? Host)?.playVideoWithContext(
                video = video,
                resumePositionMs = 0L,
                rowKey = video.category,
                rowVideoIds = ids,
                currentIndex = idx
            )
        }

        favoriteButton.setOnClickListener {
            favoritesStore.toggleFavorite(video.id)
        }

        val progress = progressStore.getProgress(video.id)
        val percent = progress?.percent ?: 0
        val canResume = progress != null && percent in 1..94

        if (canResume) {
            val remainingMs = (progress!!.durationMs - progress.positionMs).coerceAtLeast(0L)
            resumeHint.text = "Resume • ${formatTime(remainingMs)} remaining"
            resumeHint.visibility = View.VISIBLE

            resumeButton.visibility = View.VISIBLE
            resumeButton.setOnClickListener {
                (activity as? Host)?.playVideoWithContext(
                    video = video,
                    resumePositionMs = progress.positionMs,
                    rowKey = video.category,
                    rowVideoIds = ids,
                    currentIndex = idx
                )
            }

            // Focus Resume by default when applicable.
            resumeButton.post { resumeButton.requestFocus() }
        } else {
            resumeButton.visibility = View.GONE
            resumeHint.visibility = View.GONE
            // Focus Play by default for TV readiness.
            playButton.post { playButton.requestFocus() }
        }

        // Keep favorite button label in sync with current favorite state.
        favoritesCollectJob?.cancel()
        favoritesCollectJob = viewLifecycleOwner.lifecycleScope.launch {
            favoritesStore.favoritesFlow().collectLatest { favIds ->
                val isFav = favIds.contains(video.id)
                favoriteButton.text = if (isFav) "Remove from Favorites" else "Add to Favorites"
            }
        }

        // Handle Back at fragment view level.
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                (activity as? Host)?.goBack()
                true
            } else false
        }
    }

    private fun showSubtitleSelectorDialog(videoId: String) {
        val tracks = SubtitlesRepository.tracksForVideo(videoId)
        if (tracks.isEmpty()) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_subtitle_selector, null)
        val listView: ListView = dialogView.findViewById(R.id.subtitle_list)

        val items = buildList {
            add("Off")
            addAll(tracks.map { it.label })
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_single_choice,
            items
        )
        listView.adapter = adapter

        val saved = subtitleSelectionStore.getSelectedTrackId(videoId)
        val initiallySelectedIndex = when {
            saved == null -> 0 // default Off if none selected previously
            saved == SubtitleSelectionStore.TRACK_ID_OFF -> 0
            else -> {
                val idx = tracks.indexOfFirst { it.id == saved }
                if (idx >= 0) idx + 1 else 0 // missing => default Off
            }
        }
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.setItemChecked(initiallySelectedIndex, true)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                subtitleSelectionStore.setSelectedTrackId(videoId, SubtitleSelectionStore.TRACK_ID_OFF)
            } else {
                val track = tracks.getOrNull(position - 1)
                if (track != null) {
                    subtitleSelectionStore.setSelectedTrackId(videoId, track.id)
                } else {
                    // Defensive: clear if something is off
                    subtitleSelectionStore.setSelectedTrackId(videoId, SubtitleSelectionStore.TRACK_ID_OFF)
                }
            }
            dialog.dismiss()
        }

        dialog.show()
        // Focus list for DPAD navigation.
        listView.post { listView.requestFocus() }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }

    companion object {
        private const val ARG_VIDEO = "arg_video"

        // PUBLIC_INTERFACE
        fun newInstance(video: Video): VideoDetailsFragment {
            /** Create a new details fragment for the given video. */
            return VideoDetailsFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_VIDEO, video.toParcelable())
                }
            }
        }
    }
}
