package com.example.frontend_android_tv_app.ui.screens

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.view.accessibility.AccessibilityEvent
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.example.frontend_android_tv_app.R
import com.example.frontend_android_tv_app.data.FavoritesStore
import com.example.frontend_android_tv_app.data.LastPlayedIndexStore
import com.example.frontend_android_tv_app.data.ProgressStore
import com.example.frontend_android_tv_app.data.SubtitleSelectionStore
import com.example.frontend_android_tv_app.data.SubtitlesRepository
import com.example.frontend_android_tv_app.data.Video
import com.example.frontend_android_tv_app.data.VideoParcelable
import com.example.frontend_android_tv_app.data.VideosRepository
import com.example.frontend_android_tv_app.data.toParcelable
import com.example.frontend_android_tv_app.data.toVideo
import com.example.frontend_android_tv_app.ui.player.AutoplayCoordinator
import kotlin.math.roundToInt

/**
 * Player screen with DPAD/keyboard controls:
 * - Left/Right: seek ±10s (unless Next Up overlay is active, then it changes candidate)
 * - Up/Down: volume ±10% (unless Next Up overlay is active, then Up cancels)
 * - Enter/Center/Space: play/pause (unless Next Up overlay is active, then it triggers Play Now)
 * - M: mute toggle
 * - Back/Escape: exit player
 *
 * Overlay:
 * - Shows controls on interaction; auto-hides after 3 seconds.
 *
 * Autoplay Next:
 * - On playback end, show a 5 second "Next Up" overlay and auto-play next item.
 * - DPAD: Left/Right change candidate (within row), Up cancels, OK plays now.
 * - If user seeks back / exits before countdown finishes, autoplay is canceled.
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

    // Next Up overlay
    private lateinit var nextUpOverlay: View
    private lateinit var nextUpThumb: ImageView
    private lateinit var nextUpTitle: TextView
    private lateinit var nextUpCountdownText: TextView
    private lateinit var nextUpProgress: ProgressBar
    private lateinit var btnNextUpPlayNow: Button
    private lateinit var btnNextUpCancel: Button
    private lateinit var nextUpLabel: TextView

    private lateinit var video: Video
    private var resumePositionMs: Long = 0L

    private lateinit var progressStore: ProgressStore
    private lateinit var favoritesStore: FavoritesStore
    private lateinit var lastPlayedIndexStore: LastPlayedIndexStore
    private lateinit var subtitleSelectionStore: SubtitleSelectionStore

    // Subtitle track state
    private var availableSubtitleTracks: List<SubtitlesRepository.SubtitleTrack> = emptyList()
    private var selectedSubtitleTrackId: String? = null // null => not loaded yet; "__off__" => Off

    // Subtitle settings overlay (in-player)
    private lateinit var subtitleSettingsOverlay: View
    private lateinit var subtitleSettingsList: ListView
    private lateinit var subtitleSettingsTitle: TextView
    private var subtitleOverlayAdapter: ArrayAdapter<String>? = null

    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideOverlayRunnable = Runnable { setOverlayVisible(false) }
    private val progressTick = object : Runnable {
        override fun run() {
            updateTimeAndProgress()
            uiHandler.postDelayed(this, 250)
        }
    }

    private val progressPersistTick = object : Runnable {
        override fun run() {
            persistProgressIfNeeded()
            uiHandler.postDelayed(this, 4000) // every ~4 seconds
        }
    }

    private var muted = false
    private var lastVolume = 1.0f

    // Autoplay
    private val autoplayCoordinator = AutoplayCoordinator()
    private var rowKey: String = ""
    private var rowVideoIds: ArrayList<String> = arrayListOf()
    private var currentIndex: Int = 0
    private var cancelAutoplayForThisSession: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parcel = requireArguments().getParcelable<VideoParcelable>(ARG_VIDEO)
            ?: error("PlayerFragment requires a Video argument")
        video = parcel.toVideo()
        resumePositionMs = requireArguments().getLong(ARG_RESUME_POSITION_MS, 0L)

        rowKey = requireArguments().getString(ARG_ROW_KEY, "") ?: ""
        rowVideoIds = requireArguments().getStringArrayList(ARG_ROW_VIDEO_IDS) ?: arrayListOf()
        currentIndex = requireArguments().getInt(ARG_CURRENT_INDEX, 0)

        progressStore = ProgressStore.from(requireContext())
        favoritesStore = FavoritesStore.from(requireContext())
        lastPlayedIndexStore = LastPlayedIndexStore.from(requireContext())
        subtitleSelectionStore = SubtitleSelectionStore.from(requireContext())
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

        // Next Up overlay bindings
        nextUpOverlay = view.findViewById(R.id.nextup_overlay)
        nextUpThumb = view.findViewById(R.id.nextup_thumbnail)
        nextUpTitle = view.findViewById(R.id.nextup_title)
        nextUpCountdownText = view.findViewById(R.id.nextup_countdown_text)
        nextUpProgress = view.findViewById(R.id.nextup_progress)
        btnNextUpPlayNow = view.findViewById(R.id.btn_nextup_play_now)
        btnNextUpCancel = view.findViewById(R.id.btn_nextup_cancel)
        nextUpLabel = view.findViewById(R.id.nextup_label)

        // Subtitle settings overlay bindings
        subtitleSettingsOverlay = view.findViewById(R.id.subtitle_settings_overlay)
        subtitleSettingsList = view.findViewById(R.id.subtitle_settings_list)
        subtitleSettingsTitle = view.findViewById(R.id.subtitle_settings_title)
        subtitleSettingsOverlay.visibility = View.GONE

        titleText.text = video.title

        // Accessibility: static control descriptions (dynamic labels updated elsewhere).
        btnBack.contentDescription = getString(R.string.a11y_cancel) // Back acts like exit from player
        btnRewind.contentDescription = getString(R.string.a11y_seek_backward_10)
        btnForward.contentDescription = getString(R.string.a11y_seek_forward_10)
        // btnPlayPause contentDescription set in updatePlayPauseLabel()
        // btnMute contentDescription set in toggleMute()/updateVolumeText

        // Next Up panel: make countdown a polite live region and give panel an overall label.
        nextUpOverlay.contentDescription = getString(R.string.a11y_next_up_panel)
        ViewCompat.setAccessibilityLiveRegion(nextUpCountdownText, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)

        btnNextUpPlayNow.contentDescription = getString(R.string.a11y_play_now)
        btnNextUpCancel.contentDescription = getString(R.string.a11y_cancel)

        btnBack.setOnClickListener {
            cancelAutoplay("back pressed")
            (activity as? Host)?.goBack()
        }
        btnRewind.setOnClickListener { seekBy(-10_000) }
        btnForward.setOnClickListener { seekBy(10_000) }
        btnPlayPause.setOnClickListener { togglePlayPause() }
        btnMute.setOnClickListener { toggleMute() }

        btnNextUpPlayNow.setOnClickListener { autoplayCoordinator.playNow() }
        btnNextUpCancel.setOnClickListener { cancelAutoplay("user canceled") }

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
                    // User manually seeks during/after end screen => cancel autoplay.
                    cancelAutoplay("user seek")
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

        // Accessibility: when the seekbar is focused, speak "position of duration".
        progressBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                updateSeekbarA11y()
            }
        }

        autoplayCoordinator.setListener(object : AutoplayCoordinator.Listener {
            override fun onStateChanged(state: AutoplayCoordinator.State) {
                if (!isAdded) return
                renderNextUpState(state)
            }

            override fun onPlayRequested(nextUp: AutoplayCoordinator.NextUp) {
                // Trigger immediate play of nextUp candidate.
                playNextUp(nextUp.video, nextUp.rowKey, nextUp.index)
            }

            override fun onCanceled() {
                hideNextUpOverlay()
                // Keep player paused at end screen (requirement)
                player?.pause()
            }
        })

        // Player view consumes key events; capture on root (support ACTION_DOWN + long-press).
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            // If subtitle settings overlay is open, give it first chance to handle Back.
            if (subtitleSettingsOverlay.isVisible) {
                if (event.action == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE)
                ) {
                    hideSubtitleSettingsOverlay()
                    return@setOnKeyListener true
                }
                // Allow ListView navigation by not consuming DPAD keys here.
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    return@setOnKeyListener false
                }
            }

            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    // Long-press Enter/Center toggles subtitles.
                    val longPressTimeout = ViewConfiguration.getLongPressTimeout()
                    if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) &&
                        event.downTime != 0L &&
                        event.eventTime - event.downTime >= longPressTimeout
                    ) {
                        toggleSubtitlesQuick()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener handleKey(keyCode)
                }
                KeyEvent.ACTION_MULTIPLE -> false
                else -> false
            }
        }

        // Show overlay initially for discoverability.
        setOverlayVisible(true)
        scheduleOverlayHide()
    }

    override fun onStart() {
        super.onStart()
        val ctx = requireContext()

        // Load local tracks and restore persisted selection for this video.
        availableSubtitleTracks = SubtitlesRepository.tracksForVideo(video.id)
        selectedSubtitleTrackId = subtitleSelectionStore.getSelectedTrackId(video.id)
            ?: SubtitleSelectionStore.TRACK_ID_OFF

        // If persisted selection points to a missing track, clear it (edge-case requirement).
        if (selectedSubtitleTrackId != SubtitleSelectionStore.TRACK_ID_OFF) {
            val exists = availableSubtitleTracks.any { it.id == selectedSubtitleTrackId }
            if (!exists) {
                selectedSubtitleTrackId = SubtitleSelectionStore.TRACK_ID_OFF
                subtitleSelectionStore.setSelectedTrackId(video.id, SubtitleSelectionStore.TRACK_ID_OFF)
            }
        }

        player = ExoPlayer.Builder(ctx).build().also { exo ->
            playerView.player = exo

            // Apply TV-appropriate subtitle styling (readability).
            @UnstableApi
            fun applySubtitleStyling() {
                val subtitleView: SubtitleView? = playerView.subtitleView
                subtitleView?.setStyle(
                    CaptionStyleCompat(
                        /* foregroundColor= */ Color.WHITE,
                        /* backgroundColor= */ Color.parseColor("#66000000"),
                        /* windowColor= */ Color.TRANSPARENT,
                        /* edgeType= */ CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                        /* edgeColor= */ Color.BLACK,
                        /* typeface= */ null
                    )
                )
                // Use a larger text size suitable for TV viewing.
                // Media3 SubtitleView#setFixedTextSize expects a sizeType int:
                // 0 = fraction, 1 = px, 2 = sp. (Keep numeric to avoid API constant mismatch across versions.)
                subtitleView?.setFixedTextSize(2, 22f)
            }
            applySubtitleStyling()

            val mediaItem = buildMediaItemForCurrentVideo()
            exo.setMediaItem(mediaItem)
            exo.prepare()

            exo.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlayPauseLabel()
                    // Persist on key state changes too (pause/play)
                    persistProgressIfNeeded()

                    // Starting playback of any kind should hide next up overlay.
                    if (isPlaying) {
                        hideNextUpOverlay()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        // Completed => clear progress so it doesn't show in Continue Watching.
                        progressStore.clearProgress(video.id)

                        // Start autoplay countdown if we have context and user hasn't canceled.
                        if (!cancelAutoplayForThisSession) {
                            startAutoplayFlow()
                        } else {
                            hideNextUpOverlay()
                        }
                    } else {
                        persistProgressIfNeeded()
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    // If the user seeks back before countdown finishes, cancel autoplay.
                    if (autoplayCoordinator.isActive() && reason == Player.DISCONTINUITY_REASON_SEEK) {
                        cancelAutoplay("seek discontinuity")
                    }
                }
            })

            // Seek before playWhenReady to satisfy resume behavior.
            val startPos = resumePositionMs.coerceAtLeast(0L)
            if (startPos > 0L) {
                exo.seekTo(startPos)
            }
            exo.playWhenReady = true
        }

        // Configure subtitle settings overlay UI once we have tracks.
        configureSubtitleOverlayUi()

        uiHandler.post(progressTick)
        uiHandler.post(progressPersistTick)
        updateVolumeText()
    }

    override fun onStop() {
        super.onStop()
        // Leaving player cancels autoplay.
        cancelAutoplay("onStop")
        persistProgressIfNeeded()
        uiHandler.removeCallbacks(progressTick)
        uiHandler.removeCallbacks(progressPersistTick)
        uiHandler.removeCallbacks(hideOverlayRunnable)
        playerView.player = null
        player?.release()
        player = null
    }

    private fun handleKey(keyCode: Int): Boolean {
        // If Next Up overlay active, DPAD is repurposed:
        // Left/Right change candidate, Up cancels, OK plays now.
        if (nextUpOverlay.visibility == View.VISIBLE && autoplayCoordinator.isActive()) {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> autoplayCoordinator.moveCandidateLeft()
                KeyEvent.KEYCODE_DPAD_RIGHT -> autoplayCoordinator.moveCandidateRight()
                KeyEvent.KEYCODE_DPAD_UP -> {
                    cancelAutoplay("dpad up cancel")
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    autoplayCoordinator.playNow()
                    true
                }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    cancelAutoplay("back/escape")
                    (activity as? Host)?.goBack()
                    true
                }
                else -> false
            }
        }

        // If subtitle overlay is visible, handle OK/Center to select, and S to toggle.
        if (subtitleSettingsOverlay.isVisible) {
            return when (keyCode) {
                KeyEvent.KEYCODE_S -> {
                    toggleSubtitlesQuick()
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    val pos = subtitleSettingsList.checkedItemPosition
                    if (pos != ListView.INVALID_POSITION) {
                        applySubtitleSelectionByIndex(pos)
                    }
                    true
                }
                else -> false
            }
        }

        showOverlayTemporarily()

        return when (keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                cancelAutoplay("back/escape")
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
            KeyEvent.KEYCODE_S -> {
                // Quick toggle subtitles on/off (defaults to first track when enabling).
                toggleSubtitlesQuick()
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
        // If user manually resumes/pauses while NextUp is visible, cancel autoplay.
        if (autoplayCoordinator.isActive()) {
            cancelAutoplay("manual play/pause")
        }
        if (p.isPlaying) p.pause() else p.play()
        updatePlayPauseLabel()
        showOverlayTemporarily()
    }

    private fun updatePlayPauseLabel() {
        val p = player ?: return
        val isPlaying = p.isPlaying
        btnPlayPause.text = if (isPlaying) "Pause" else "Play"
        btnPlayPause.contentDescription = if (isPlaying) {
            getString(R.string.a11y_pause)
        } else {
            getString(R.string.a11y_play)
        }
    }

    private fun seekBy(deltaMs: Long) {
        val p = player ?: return
        // User interaction cancels autoplay.
        if (autoplayCoordinator.isActive()) {
            cancelAutoplay("seekBy")
        }
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
            btnMute.contentDescription = getString(R.string.a11y_unmute)
        } else {
            p.volume = lastVolume.coerceAtLeast(0.1f)
            btnMute.text = "Mute"
            btnMute.contentDescription = getString(R.string.a11y_mute)
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

        // Accessibility: keep seekbar description current for TalkBack focus.
        updateSeekbarA11y()
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

    private fun persistProgressIfNeeded() {
        val p = player ?: return
        val dur = p.duration
        val pos = p.currentPosition

        if (dur <= 0L) return

        val percent = ((pos.toDouble() / dur.toDouble()) * 100.0).coerceIn(0.0, 100.0)
        if (percent >= 95.0) {
            progressStore.clearProgress(video.id)
        } else {
            progressStore.setProgress(video.id, pos, dur)
        }
    }

    private fun startAutoplayFlow() {
        // Resolve row order using navigation context; favorites/continue watching need special handling.
        val rowVideos = resolveRowVideos(rowKey, rowVideoIds)

        // Compute a safe current index from ids if possible.
        val currentIdx = rowVideos.indexOfFirst { it.id == video.id }.let { idx ->
            if (idx >= 0) idx else currentIndex.coerceIn(0, (rowVideos.size - 1).coerceAtLeast(0))
        }

        // Optional persist last-played index per row key.
        if (rowKey.isNotBlank()) {
            lastPlayedIndexStore.setLastIndex(rowKey, currentIdx)
        }

        val globalFallback = VideosRepository.categories
            .firstOrNull()
            ?.let { cat -> VideosRepository.videosForCategory(cat) }
            ?: emptyList()

        autoplayCoordinator.startOnEnded(
            AutoplayCoordinator.RowContext(
                rowKey = rowKey,
                rowVideos = rowVideos,
                currentIndex = currentIdx,
                globalFallback = globalFallback
            )
        )
    }

    private fun resolveRowVideos(rowKey: String, ids: List<String>): List<Video> {
        // Prefer explicit ordered IDs if provided.
        if (ids.isNotEmpty()) {
            val byId = VideosRepository.videos.associateBy { it.id }
            return ids.mapNotNull { byId[it] }
        }

        // Fall back to resolving based on rowKey.
        return when (rowKey) {
            ROWKEY_FAVORITES -> {
                val favIds = favoritesStore.favoritesFlowSnapshot()
                val byId = VideosRepository.videos.associateBy { it.id }
                favIds.mapNotNull { byId[it] }
            }
            ROWKEY_CONTINUE_WATCHING -> {
                progressStore.listInProgress().map { it.video }
            }
            else -> {
                if (rowKey.isBlank()) VideosRepository.videosForCategory(video.category)
                else VideosRepository.videosForCategory(rowKey)
            }
        }
    }

    private var lastAnnouncedNextUpSeconds: Int = -1

    private fun renderNextUpState(state: AutoplayCoordinator.State) {
        if (!state.visible) {
            hideNextUpOverlay()
            lastAnnouncedNextUpSeconds = -1
            return
        }

        nextUpOverlay.visibility = View.VISIBLE
        nextUpProgress.max = 1000

        if (state.mode == AutoplayCoordinator.Mode.REPLAY) {
            nextUpLabel.text = "Replay"
            nextUpTitle.text = video.title
            nextUpCountdownText.text = "End of row"
            nextUpProgress.progress = 0
            btnNextUpPlayNow.text = "Replay"
            btnNextUpCancel.text = "Cancel"

            // Accessibility: panel announcement for replay mode (single shot).
            nextUpCountdownText.contentDescription =
                getString(R.string.a11y_next_up_in_seconds, video.title, 0)

            // Use current video thumb as preview (best available).
            Glide.with(nextUpThumb)
                .load(video.thumbnailUrl)
                .centerCrop()
                .into(nextUpThumb)

            btnNextUpPlayNow.setOnClickListener {
                // Replay current.
                cancelAutoplay("replay")
                player?.seekTo(0L)
                player?.playWhenReady = true
            }
            btnNextUpCancel.setOnClickListener { cancelAutoplay("replay cancel") }

            btnNextUpPlayNow.post { btnNextUpPlayNow.requestFocus() }
            return
        }

        val next = state.nextUp ?: return
        nextUpLabel.text = "Up Next"
        nextUpTitle.text = next.video.title

        // Countdown UI
        val secLeft = ((state.remainingMs + 999) / 1000).toInt().coerceAtLeast(0)
        nextUpCountdownText.text = "Playing in ${secLeft}s"
        val fraction = (state.remainingMs.toDouble() / state.totalMs.toDouble()).coerceIn(0.0, 1.0)
        nextUpProgress.progress = (fraction * 1000).roundToInt()

        // Accessibility: announce "Next up: <title> in N seconds" on second changes.
        if (secLeft != lastAnnouncedNextUpSeconds) {
            lastAnnouncedNextUpSeconds = secLeft
            val announcement = getString(R.string.a11y_next_up_in_seconds, next.video.title, secLeft)
            nextUpCountdownText.contentDescription = announcement
            nextUpCountdownText.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
        }

        btnNextUpPlayNow.text = "Play Now"
        btnNextUpCancel.text = "Cancel"
        btnNextUpPlayNow.setOnClickListener { autoplayCoordinator.playNow() }
        btnNextUpCancel.setOnClickListener { cancelAutoplay("cancel button") }

        // Prefetch thumbnail via Glide (this loads into the ImageView; Glide will cache).
        Glide.with(nextUpThumb)
            .load(next.video.thumbnailUrl)
            .centerCrop()
            .into(nextUpThumb)

        // Prefer focusing primary action.
        btnNextUpPlayNow.post { btnNextUpPlayNow.requestFocus() }
    }

    private fun updateSeekbarA11y() {
        val p = player ?: return
        val dur = p.duration
        if (dur <= 0L) return
        val posStr = formatTime(p.currentPosition)
        val durStr = formatTime(dur)
        progressBar.contentDescription = getString(R.string.a11y_seekbar_time, posStr, durStr)
    }

    private fun hideNextUpOverlay() {
        nextUpOverlay.visibility = View.GONE
    }

    private fun cancelAutoplay(reason: String) {
        // Cancel for remainder of this PlayerFragment instance once user intervenes/cancels.
        cancelAutoplayForThisSession = true
        if (autoplayCoordinator.isActive()) {
            autoplayCoordinator.cancel(userInitiated = true)
        }
        hideNextUpOverlay()
    }

    private fun playNextUp(next: Video, nextRowKey: String, nextIndex: Int) {
        // Hide overlay and start playback of next item.
        hideNextUpOverlay()
        hideSubtitleSettingsOverlay()
        cancelAutoplayForThisSession = false

        video = next
        titleText.text = next.title
        resumePositionMs = 0L

        // Update "where we are" so further next-up selection is correct.
        rowKey = nextRowKey
        currentIndex = nextIndex

        // Refresh subtitle state for new video.
        availableSubtitleTracks = SubtitlesRepository.tracksForVideo(video.id)
        selectedSubtitleTrackId = subtitleSelectionStore.getSelectedTrackId(video.id)
            ?: SubtitleSelectionStore.TRACK_ID_OFF

        if (selectedSubtitleTrackId != SubtitleSelectionStore.TRACK_ID_OFF) {
            val exists = availableSubtitleTracks.any { it.id == selectedSubtitleTrackId }
            if (!exists) {
                selectedSubtitleTrackId = SubtitleSelectionStore.TRACK_ID_OFF
                subtitleSelectionStore.setSelectedTrackId(video.id, SubtitleSelectionStore.TRACK_ID_OFF)
            }
        }

        configureSubtitleOverlayUi()

        // Reset and play new media with subtitle configs.
        val p = player ?: return
        p.setMediaItem(buildMediaItemForCurrentVideo())
        p.prepare()
        p.playWhenReady = true
    }

    private fun buildMediaItemForCurrentVideo(): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(video.videoUrl)

        // Always attach available subtitle configurations, even if currently Off.
        // We control whether they show via track selection parameters.
        val configs = availableSubtitleTracks.map { SubtitlesRepository.toSubtitleConfiguration(it) }
        if (configs.isNotEmpty()) {
            builder.setSubtitleConfigurations(configs)
        }

        return builder.build()
    }

    private fun configureSubtitleOverlayUi() {
        if (!this::subtitleSettingsOverlay.isInitialized) return

        // Hide entire overlay if no tracks exist.
        if (availableSubtitleTracks.isEmpty()) {
            subtitleSettingsOverlay.visibility = View.GONE
            return
        }

        val items = buildList {
            add("Off")
            addAll(availableSubtitleTracks.map { it.label })
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_single_choice,
            items
        )
        subtitleOverlayAdapter = adapter
        subtitleSettingsList.adapter = adapter
        subtitleSettingsList.choiceMode = ListView.CHOICE_MODE_SINGLE

        // Restore selection to overlay
        val checkedIndex = when (val id = selectedSubtitleTrackId) {
            null, SubtitleSelectionStore.TRACK_ID_OFF -> 0
            else -> {
                val idx = availableSubtitleTracks.indexOfFirst { it.id == id }
                if (idx >= 0) idx + 1 else 0
            }
        }
        subtitleSettingsList.setItemChecked(checkedIndex, true)

        subtitleSettingsList.setOnItemClickListener { _, _, position, _ ->
            applySubtitleSelectionByIndex(position)
        }
    }

    private fun showSubtitleSettingsOverlay() {
        if (availableSubtitleTracks.isEmpty()) return
        // Coexist with existing overlays: if Next Up is visible, don't open.
        if (nextUpOverlay.isVisible) return

        subtitleSettingsOverlay.visibility = View.VISIBLE
        // Do not hide playback controls just because subtitle overlay is open.
        // We keep the main overlay timer running, but we can re-show for discoverability.
        showOverlayTemporarily()

        subtitleSettingsList.post {
            subtitleSettingsList.requestFocus()
        }
    }

    private fun hideSubtitleSettingsOverlay() {
        if (!this::subtitleSettingsOverlay.isInitialized) return
        subtitleSettingsOverlay.visibility = View.GONE
    }

    private fun applySubtitleSelectionByIndex(index: Int) {
        if (availableSubtitleTracks.isEmpty()) return

        if (index <= 0) {
            applySubtitleSelection(SubtitleSelectionStore.TRACK_ID_OFF)
        } else {
            val track = availableSubtitleTracks.getOrNull(index - 1)
            if (track == null) {
                applySubtitleSelection(SubtitleSelectionStore.TRACK_ID_OFF)
            } else {
                applySubtitleSelection(track.id)
            }
        }
        hideSubtitleSettingsOverlay()
    }

    private fun applySubtitleSelection(trackId: String) {
        selectedSubtitleTrackId = trackId
        subtitleSelectionStore.setSelectedTrackId(video.id, trackId)

        // Apply to player track selection.
        // Media3 uses TrackSelectionParameters; we enable/disable text tracks.
        val p = player ?: return
        val enableText = trackId != SubtitleSelectionStore.TRACK_ID_OFF

        val builder = p.trackSelectionParameters
            .buildUpon()
            // If disabled, don't select text tracks.
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !enableText)

        // Try to prefer language/label if possible. If we can't, enabling text will let Exo choose default.
        if (enableText) {
            val track = availableSubtitleTracks.firstOrNull { it.id == trackId }
            if (track?.language != null) {
                builder.setPreferredTextLanguage(track.language)
            }
        }

        p.trackSelectionParameters = builder.build()

        // Keep overlay list selection in sync.
        configureSubtitleOverlayUi()
    }

    private fun toggleSubtitlesQuick() {
        if (availableSubtitleTracks.isEmpty()) return

        val current = selectedSubtitleTrackId ?: SubtitleSelectionStore.TRACK_ID_OFF
        val next = if (current == SubtitleSelectionStore.TRACK_ID_OFF) {
            // Turn on => choose last persisted if present and valid; else first available.
            val persisted = subtitleSelectionStore.getSelectedTrackId(video.id)
            val validPersisted =
                persisted != null &&
                    persisted != SubtitleSelectionStore.TRACK_ID_OFF &&
                    availableSubtitleTracks.any { it.id == persisted }
            if (validPersisted) persisted!! else availableSubtitleTracks.first().id
        } else {
            SubtitleSelectionStore.TRACK_ID_OFF
        }

        applySubtitleSelection(next)

        // When user toggles, show the subtitle overlay to confirm current state.
        showSubtitleSettingsOverlay()
    }

    companion object {
        private const val ARG_VIDEO = "arg_video"
        private const val ARG_RESUME_POSITION_MS = "arg_resume_position_ms"

        private const val ARG_ROW_KEY = "arg_row_key"
        private const val ARG_ROW_VIDEO_IDS = "arg_row_video_ids"
        private const val ARG_CURRENT_INDEX = "arg_current_index"

        const val ROWKEY_FAVORITES = "favorites"
        const val ROWKEY_CONTINUE_WATCHING = "continue_watching"

        // PUBLIC_INTERFACE
        fun newInstance(video: Video): PlayerFragment {
            /** Create a new player fragment for the given video. */
            return PlayerFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_VIDEO, video.toParcelable())
                    putLong(ARG_RESUME_POSITION_MS, 0L)
                }
            }
        }

        // PUBLIC_INTERFACE
        fun newInstance(video: Video, resumePositionMs: Long): PlayerFragment {
            /** Create a new player fragment for the given video, resuming at the specified position (ms). */
            return PlayerFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_VIDEO, video.toParcelable())
                    putLong(ARG_RESUME_POSITION_MS, resumePositionMs.coerceAtLeast(0L))
                }
            }
        }

        // PUBLIC_INTERFACE
        fun newInstanceWithContext(
            video: Video,
            resumePositionMs: Long,
            rowKey: String,
            rowVideoIds: ArrayList<String>,
            currentIndex: Int
        ): PlayerFragment {
            /** Create a player fragment with row context so autoplay-next can be computed. */
            return PlayerFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_VIDEO, video.toParcelable())
                    putLong(ARG_RESUME_POSITION_MS, resumePositionMs.coerceAtLeast(0L))
                    putString(ARG_ROW_KEY, rowKey)
                    putStringArrayList(ARG_ROW_VIDEO_IDS, rowVideoIds)
                    putInt(ARG_CURRENT_INDEX, currentIndex.coerceAtLeast(0))
                }
            }
        }
    }
}

/**
 * Helper to read the latest favorites set synchronously for autoplay decisions.
 *
 * This keeps Home UI reactive using Flow, but the Player needs a snapshot at end-of-playback time.
 */
private fun FavoritesStore.favoritesFlowSnapshot(): List<String> {
    // We don't have a direct synchronous accessor in FavoritesStore; this app stores favorites in prefs.
    // Re-create store quickly by reading from the public isFavorite across known ids.
    val all = VideosRepository.videos
    return all.filter { isFavorite(it.id) }.map { it.id }
}
