package com.vijaychhetry.kidspiano.core.pitch

enum class NotePhase { IDLE, ATTACK, STABLE, RELEASE }

data class DebounceConfig(
    val stableFrames: Int = 3,
    val releaseFrames: Int = 4,
)

/**
 * Maps a stream of pitch estimates to a single physical key press
 * (IDLE → ATTACK → STABLE → RELEASE → IDLE).
 */
class NoteDebouncer(private val config: DebounceConfig = DebounceConfig()) {
    private var phase = NotePhase.IDLE
    private var candidate: Int? = null
    private var stableCount = 0
    private var releaseCount = 0
    var lockedMidi: Int? = null
        private set

    fun onFrame(midi: Int?): NotePhase {
        when (phase) {
            NotePhase.IDLE -> {
                if (midi != null) {
                    candidate = midi
                    stableCount = 1
                    phase = NotePhase.ATTACK
                }
            }
            NotePhase.ATTACK -> {
                if (midi == candidate) {
                    stableCount++
                    if (stableCount >= config.stableFrames) {
                        lockedMidi = candidate
                        phase = NotePhase.STABLE
                    }
                } else if (midi == null) {
                    reset()
                } else {
                    candidate = midi
                    stableCount = 1
                }
            }
            NotePhase.STABLE -> {
                if (midi == lockedMidi) {
                    releaseCount = 0
                } else {
                    phase = NotePhase.RELEASE
                    releaseCount = 1
                }
            }
            NotePhase.RELEASE -> {
                if (midi == lockedMidi) {
                    phase = NotePhase.STABLE
                    releaseCount = 0
                } else {
                    releaseCount++
                    if (releaseCount >= config.releaseFrames) reset()
                }
            }
        }
        return phase
    }

    private fun reset() {
        phase = NotePhase.IDLE
        candidate = null
        lockedMidi = null
        stableCount = 0
        releaseCount = 0
    }
}
