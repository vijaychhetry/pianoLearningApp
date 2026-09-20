package com.vijaychhetry.kidspiano.core.pitch

/**
 * Some phones accept `AudioSource.UNPROCESSED` and then hand back digital
 * silence. `AudioRecord.state == STATE_INITIALIZED` does not prove the source
 * works, so watch the level and tell the caller to try the next source.
 */
class SilenceWatchdog(
    private val windowMs: Long = 1_500,
    private val silenceRms: Double = 0.0008,
) {
    private var firstFrameAtMs: Long? = null
    private var sawSignal = false
    private var reported = false

    /** True exactly once when the source has produced only silence for [windowMs]. */
    fun onFrame(rms: Double, atMs: Long): Boolean {
        if (rms > silenceRms) {
            sawSignal = true
            return false
        }
        val start = firstFrameAtMs ?: atMs.also { firstFrameAtMs = it }
        if (sawSignal || reported) return false
        if (atMs - start < windowMs) return false
        reported = true
        return true
    }

    fun reset() {
        firstFrameAtMs = null
        sawSignal = false
        reported = false
    }
}
