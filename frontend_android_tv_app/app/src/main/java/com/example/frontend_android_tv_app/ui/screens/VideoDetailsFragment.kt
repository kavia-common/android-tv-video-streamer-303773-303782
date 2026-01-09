package com.example.frontend_android_tv_app.ui.screens

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.frontend_android_tv_app.R
import com.example.frontend_android_tv_app.data.Video
import com.example.frontend_android_tv_app.data.toParcelable
import com.example.frontend_android_tv_app.data.toVideo

/**
 * Video details screen.
 * Shows title/description and a Play button (focusable).
 */
class VideoDetailsFragment : Fragment() {

    interface Host {
        fun playVideo(video: Video)
        fun goBack()
    }

    private lateinit var titleText: TextView
    private lateinit var descText: TextView
    private lateinit var playButton: Button

    private lateinit var video: Video

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parcel = requireArguments().getParcelable<com.example.frontend_android_tv_app.data.VideoParcelable>(ARG_VIDEO)
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

        titleText.text = video.title
        descText.text = video.description

        playButton.setOnClickListener {
            (activity as? Host)?.playVideo(video)
        }

        // Focus the Play button by default for TV readiness.
        playButton.post { playButton.requestFocus() }

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
