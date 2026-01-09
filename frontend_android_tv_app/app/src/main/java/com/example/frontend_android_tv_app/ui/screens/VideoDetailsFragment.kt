package com.example.frontend_android_tv_app.ui.screens

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.frontend_android_tv_app.R
import com.example.frontend_android_tv_app.data.FavoritesStore
import com.example.frontend_android_tv_app.data.ProgressStore
import com.example.frontend_android_tv_app.data.Video
import com.example.frontend_android_tv_app.data.VideoParcelable
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
        fun goBack()
    }

    private lateinit var titleText: TextView
    private lateinit var descText: TextView
    private lateinit var playButton: Button
    private lateinit var resumeButton: Button
    private lateinit var favoriteButton: Button
    private lateinit var resumeHint: TextView

    private lateinit var video: Video
    private lateinit var progressStore: ProgressStore
    private lateinit var favoritesStore: FavoritesStore

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
        favoriteButton = view.findViewById(R.id.details_favorite_button)
        resumeHint = view.findViewById(R.id.details_resume_hint)

        progressStore = ProgressStore.from(requireContext())
        favoritesStore = FavoritesStore.from(requireContext())

        titleText.text = video.title
        descText.text = video.description

        playButton.setOnClickListener {
            (activity as? Host)?.playVideo(video)
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
                (activity as? Host)?.playVideo(video, progress.positionMs)
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
            favoritesStore.favoritesFlow().collectLatest { ids ->
                val isFav = ids.contains(video.id)
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
