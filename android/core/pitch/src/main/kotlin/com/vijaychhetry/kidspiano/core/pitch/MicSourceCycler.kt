package com.vijaychhetry.kidspiano.core.pitch

/**
 * Walks the microphone sources once. A phone with a genuinely muted mic must
 * not be cycled forever, so [advance] returns null after the last source and
 * the caller shows a terminal message instead.
 *
 * Sources are plain ints so this stays testable off-device; the app passes
 * `MediaRecorder.AudioSource` values.
 */
class MicSourceCycler(private val sources: List<Int>) {
    init {
        require(sources.isNotEmpty()) { "need at least one audio source" }
    }

    private var index = 0

    val current: Int get() = sources[index]

    /** How many sources have been tried, including the current one. */
    var attempts: Int = 1
        private set

    val exhausted: Boolean get() = attempts >= sources.size

    /** The next untried source, or null once every source has been tried. */
    fun advance(): Int? {
        if (exhausted) return null
        index = (index + 1) % sources.size
        attempts++
        return current
    }

    /** Call when audio is confirmed healthy, so a later stall gets a full pass again. */
    fun reset() {
        attempts = 1
    }

    /** The user asked for another source by hand, which re-arms the automatic cycle. */
    fun forceAdvance(): Int {
        index = (index + 1) % sources.size
        attempts = 1
        return current
    }
}
