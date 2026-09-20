package com.vijaychhetry.kidspiano.core.pitch

/**
 * Detects a microphone that opened but never delivers audio.
 *
 * [SilenceWatchdog] only sees frames, so it cannot notice the case where
 * `AudioRecord.read()` never returns anything — the screen would sit on
 * "waiting for the first frame" forever. This is driven by a clock instead,
 * so the caller can tick it from a timer that is independent of the audio flow.
 */
class FrameArrivalMonitor(private val stallMs: Long = 1_500) {
    private var startedAtMs: Long? = null
    private var lastFrameAtMs: Long? = null
    private var reported = false

    fun start(atMs: Long) {
        startedAtMs = atMs
        lastFrameAtMs = null
        reported = false
    }

    fun onFrame(atMs: Long) {
        lastFrameAtMs = atMs
        reported = false
    }

    /** True exactly once per stall: no frame for [stallMs] since start or the last frame. */
    fun isStalled(nowMs: Long): Boolean {
        val since = lastFrameAtMs ?: startedAtMs ?: return false
        if (reported || nowMs - since < stallMs) return false
        reported = true
        return true
    }
}
