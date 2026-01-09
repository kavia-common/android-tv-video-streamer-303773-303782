package com.example.frontend_android_tv_app.ui.screens

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.frontend_android_tv_app.R
import com.example.frontend_android_tv_app.data.Video
import com.example.frontend_android_tv_app.data.VideoParcelable
import com.example.frontend_android_tv_app.data.toParcelable
import com.example.frontend_android_tv_app.data.toVideo
import kotlin.math.roundToInt

/**
 * Player screen with DPAD/keyboard controls:
 * - Left/Right: seek ±10s
 * - Up/Down: volume ±10%
 * - Enter/Center/Space: play/pause
 * - M: mute toggle
 * - Back/Escape: exit player
 *
 * Overlay:
 * - Shows controls on interaction; auto-hides after 3 seconds.
 */
class PlayerFragment : Fragment() {

    interface Host {
        fun goBack()
    }

    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null

    private lateinit var overlay: View
    private lateinit var titleText: TextView
    private lateinit var timeText: TextView
    private lateinit var volumeText: TextView
    private lateinit var progressBar: SeekBar

    private lateinit var btnBack: Button
    private lateinit var btnRewind: Button
    private lateinit var btnPlayPause: Button
    private lateinit var btnForward: Button
    private lateinit var btnMute: Button

    private lateinit var video: Video

    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideOverlayRunnable = Runnable { setOverlayVisible(false) }
    private val progressTick = object : Runnable {
        override fun run() {
            updateTimeAndProgress()
            uiHandler.postDelayed(this, 250)
        }
    }

    private var muted = false
    private var lastVolume = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parcel = requireArguments().getParcelable<VideoParcelable>(ARG_VIDEO)
            ?: error("PlayerFragment requires a Video argument")
        video = parcel.toVideo()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        playerView = view.findViewById(R.id.player_view)
        overlay = view.findViewById(R.id.controls_overlay)
        titleText = view.findViewById(R.id.player_title)
        timeText = view.findViewById(R.id.time_text)
        volumeText = view.findViewById(R.id.volume_text)
        progressBar = view.findViewById(R.id.progress_bar)

        btnBack = view.findViewById(R.id.btn_back)
        btnRewind = view.findViewById(R.id.btn_rewind)
        btnPlayPause = view.findViewById(R.id.btn_play_pause)
        btnForward = view.findViewById(R.id.btn_forward)
        btnMute = view.findViewById(R.id.btn_mute)

        titleText.text = video.title

        btnBack.setOnClickListener { (activity as? Host)?.goBack() }
        btnRewind.setOnClickListener { seekBy(-10_000) }
        btnForward.setOnClickListener { seekBy(10_000) }
        btnPlayPause.setOnClickListener { togglePlayPause() }
        btnMute.setOnClickListener { toggleMute() }

        // Make overlay buttons focusable; initial focus on Play/Pause for quick control.
        btnPlayPause.post { btnPlayPause.requestFocus() }

        progressBar.max = 1000
        progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val p = player ?: return
                val dur = p.duration
                if (dur > 0) {
                    val target = (dur * (progress / 1000f)).roundToInt().toLong()
                    p.seekTo(target)
                    showOverlayTemporarily()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                showOverlayTemporarily()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                showOverlayTemporarily()
            }
        })

        // Player view consumes key events; capture on root.
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            handleKey(keyCode)
        }

        // Show overlay initially for discoverability.
        setOverlayVisible(true)
        scheduleOverlayHide()
    }

    override fun onStart() {
        super.onStart()
        val ctx = requireContext()
        player = ExoPlayer.Builder(ctx).build().also { exo ->
            playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(video.videoUrl))
            exo.prepare()
            exo.playWhenReady = true

            exo.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlayPauseLabel()
                }
            })
        }

        uiHandler.post(progressTick)
        updateVolumeText()
    }

    override fun onStop() {
        super.onStop()
        uiHandler.removeCallbacks(progressTick)
        uiHandler.removeCallbacks(hideOverlayRunnable)
        playerView.player = null
        player?.release()
        player = null
    }

    private fun handleKey(keyCode: Int): Boolean {
        showOverlayTemporarily()

        return when (keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                (activity as? Host)?.goBack()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                seekBy(-10_000)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                seekBy(10_000)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                changeVolumeBy(0.1f)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                changeVolumeBy(-0.1f)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                togglePlayPause()
                true
            }
            KeyEvent.KEYCODE_M -> {
                toggleMute()
                true
            }
            // "F toggle full screen" is effectively always full screen in this TV player.
            // We still acknowledge it to match requested shortcut behavior.
            KeyEvent.KEYCODE_F -> true
            else -> false
        }
    }

    private fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
        updatePlayPauseLabel()
        showOverlayTemporarily()
    }

    private fun updatePlayPauseLabel() {
        val p = player ?: return
        btnPlayPause.text = if (p.isPlaying) "Pause" else "Play"
    }

    private fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val cur = p.currentPosition
        val target = (cur + deltaMs).coerceIn(0, p.duration.coerceAtLeast(0))
        p.seekTo(target)
        updateTimeAndProgress()
    }

    private fun changeVolumeBy(delta: Float) {
        val p = player ?: return
        val newVol = (p.volume + delta).coerceIn(0f, 1f)
        p.volume = newVol
        if (newVol > 0f) {
            muted = false
            btnMute.text = "Mute"
            lastVolume = newVol
        }
        updateVolumeText()
    }

    private fun toggleMute() {
        val p = player ?: return
        muted = !muted
        if (muted) {
            lastVolume = p.volume
            p.volume = 0f
            btnMute.text = "Unmute"
        } else {
            p.volume = lastVolume.coerceAtLeast(0.1f)
            btnMute.text = "Mute"
        }
        updateVolumeText()
        showOverlayTemporarily()
    }

    private fun updateVolumeText() {
        val p = player ?: return
        val pct = (p.volume * 100).roundToInt()
        volumeText.text = "Vol ${pct}%"
    }

    private fun updateTimeAndProgress() {
        val p = player ?: return
        val pos = p.currentPosition
        val dur = p.duration

        val posStr = formatTime(pos)
        val durStr = if (dur > 0) formatTime(dur) else "--:--"
        timeText.text = "$posStr / $durStr"

        if (dur > 0) {
            val fraction = (pos.toDouble() / dur.toDouble()).coerceIn(0.0, 1.0)
            progressBar.progress = (fraction * 1000).roundToInt()
        } else {
            progressBar.progress = 0
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }

    private fun setOverlayVisible(visible: Boolean) {
        overlay.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun showOverlayTemporarily() {
        setOverlayVisible(true)
        scheduleOverlayHide()
    }

    private fun scheduleOverlayHide() {
        uiHandler.removeCallbacks(hideOverlayRunnable)
        uiHandler.postDelayed(hideOverlayRunnable, 3000)
    }

    companion object {
        private const val ARG_VIDEO = "arg_video"

        // PUBLIC_INTERFACE
        fun newInstance(video: Video): PlayerFragment {
            /** Create a new player fragment for the given video. */
            return PlayerFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_VIDEO, video.toParcelable())
                }
            }
        }
    }
}
