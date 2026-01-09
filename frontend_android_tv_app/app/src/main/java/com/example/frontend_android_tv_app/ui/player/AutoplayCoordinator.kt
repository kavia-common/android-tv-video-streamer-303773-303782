package com.example.frontend_android_tv_app.ui.player

import android.os.Handler
import android.os.Looper
import com.example.frontend_android_tv_app.data.Video

/**
 * Player-layer coordinator for "autoplay next" behavior.
 *
 * Responsibilities:
 * - Decide the next-up candidate given a row context (rowKey + ordered list + currentIndex).
 * - Manage a 5 second countdown, including:
 *   - cancel
 *   - play now
 *   - DPAD left/right to change the candidate within the same row
 * - Surface state changes via a listener callback.
 */
class AutoplayCoordinator(
    private val handler: Handler = Handler(Looper.getMainLooper())
) {

    data class RowContext(
        val rowKey: String,
        val rowVideos: List<Video>,
        val currentIndex: Int,
        val globalFallback: List<Video> = emptyList()
    )

    data class NextUp(
        val rowKey: String,
        val index: Int,
        val video: Video
    )

    data class State(
        val visible: Boolean,
        val nextUp: NextUp?,
        val remainingMs: Long,
        val totalMs: Long,
        val mode: Mode
    )

    enum class Mode {
        NEXT_UP, // countdown running
        REPLAY // end-of-row with no fallback
    }

    interface Listener {
        // PUBLIC_INTERFACE
        fun onStateChanged(state: State)
        // PUBLIC_INTERFACE
        fun onPlayRequested(nextUp: NextUp)
        // PUBLIC_INTERFACE
        fun onCanceled()
    }

    private var listener: Listener? = null

    private var ctx: RowContext? = null
    private var nextUp: NextUp? = null

    private val totalMs = 5_000L
    private var remainingMs = totalMs

    private var active = false
    private var mode: Mode = Mode.NEXT_UP

    private var lastTickUptimeMs: Long = 0L

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!active) return

            val now = android.os.SystemClock.uptimeMillis()
            val delta = if (lastTickUptimeMs == 0L) 0L else (now - lastTickUptimeMs).coerceAtLeast(0L)
            lastTickUptimeMs = now

            // Decrease remaining; allow "don't start over fully" behavior when candidate changes:
            // the remaining time keeps counting down based on time elapsed.
            remainingMs = (remainingMs - delta).coerceAtLeast(0L)

            emitState()

            if (remainingMs <= 0L) {
                // Auto-trigger play.
                val n = nextUp
                if (mode == Mode.NEXT_UP && n != null) {
                    listener?.onPlayRequested(n)
                } else {
                    cancelInternal(userInitiated = false)
                }
                return
            }

            handler.postDelayed(this, 250L)
        }
    }

    // PUBLIC_INTERFACE
    fun setListener(listener: Listener?) {
        /** Set (or clear) a listener that receives state updates and play/cancel callbacks. */
        this.listener = listener
    }

    // PUBLIC_INTERFACE
    fun startOnEnded(context: RowContext) {
        /**
         * Called when playback ends and the player wants to begin "Next Up" flow.
         * If a next-up candidate cannot be found, enters REPLAY mode.
         */
        ctx = context
        lastTickUptimeMs = 0L
        remainingMs = totalMs

        val candidate = computeNextUpCandidate(context)
        if (candidate == null) {
            mode = Mode.REPLAY
            nextUp = null
            active = true
            emitState(visible = true)
            return
        }

        mode = Mode.NEXT_UP
        nextUp = candidate
        active = true
        emitState(visible = true)
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    // PUBLIC_INTERFACE
    fun cancel(userInitiated: Boolean = true) {
        /** Cancel autoplay and hide overlay. */
        cancelInternal(userInitiated = userInitiated)
    }

    // PUBLIC_INTERFACE
    fun playNow() {
        /** Immediately request playback of the current next-up candidate (if any). */
        val n = nextUp
        if (!active) return
        if (mode != Mode.NEXT_UP || n == null) return
        listener?.onPlayRequested(n)
    }

    // PUBLIC_INTERFACE
    fun replay() {
        /** In REPLAY mode, treat "play now" as replaying current (handled by caller); we just cancel UI. */
        cancelInternal(userInitiated = true)
    }

    // PUBLIC_INTERFACE
    fun moveCandidateLeft(): Boolean {
        /** Move next-up selection to the previous item within the current row. */
        if (!active) return false
        if (mode != Mode.NEXT_UP) return false
        val c = ctx ?: return false
        val current = nextUp ?: return false
        if (current.index <= 0) return false

        nextUp = NextUp(rowKey = c.rowKey, index = current.index - 1, video = c.rowVideos[current.index - 1])
        emitState()
        return true
    }

    // PUBLIC_INTERFACE
    fun moveCandidateRight(): Boolean {
        /** Move next-up selection to the next item within the current row. */
        if (!active) return false
        if (mode != Mode.NEXT_UP) return false
        val c = ctx ?: return false
        val current = nextUp ?: return false
        if (current.index >= c.rowVideos.size - 1) return false

        nextUp = NextUp(rowKey = c.rowKey, index = current.index + 1, video = c.rowVideos[current.index + 1])
        emitState()
        return true
    }

    // PUBLIC_INTERFACE
    fun isActive(): Boolean {
        /** Return true if the autoplay overlay/countdown flow is currently active. */
        return active
    }

    private fun cancelInternal(userInitiated: Boolean) {
        handler.removeCallbacks(tickRunnable)
        active = false
        lastTickUptimeMs = 0L
        emitState(visible = false)
        if (userInitiated) {
            listener?.onCanceled()
        }
    }

    private fun emitState(visible: Boolean = true) {
        val s = State(
            visible = visible && active,
            nextUp = nextUp,
            remainingMs = remainingMs,
            totalMs = totalMs,
            mode = mode
        )
        listener?.onStateChanged(s)
    }

    private fun computeNextUpCandidate(context: RowContext): NextUp? {
        val row = context.rowVideos
        val idx = context.currentIndex

        // Primary: next in same row.
        val nextIdx = idx + 1
        if (nextIdx in row.indices) {
            return NextUp(rowKey = context.rowKey, index = nextIdx, video = row[nextIdx])
        }

        // Fallback: global curated sequence: pick next after current (by id) if available,
        // otherwise pick first item from first category's next item (simplified: first of globalFallback).
        if (context.globalFallback.isNotEmpty()) {
            val byIdIndex = context.globalFallback.indexOfFirst { it.id == row.getOrNull(idx)?.id }
            val fallbackIdx = if (byIdIndex >= 0 && byIdIndex + 1 < context.globalFallback.size) {
                byIdIndex + 1
            } else {
                0
            }
            val v = context.globalFallback.getOrNull(fallbackIdx) ?: return null
            return NextUp(rowKey = ROWKEY_GLOBAL_FALLBACK, index = fallbackIdx, video = v)
        }

        return null
    }

    companion object {
        const val ROWKEY_GLOBAL_FALLBACK = "global_fallback"
    }
}
