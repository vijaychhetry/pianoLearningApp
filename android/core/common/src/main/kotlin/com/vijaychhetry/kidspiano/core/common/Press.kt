package com.vijaychhetry.kidspiano.core.common

enum class PressKind { NOTE, TWO_NOTES, NOISY, LOW_CONFIDENCE, TOO_SHORT, TOO_QUIET }

data class OctaveEvidence(
    val expectedFundamentalDb: Double,
    val detectedFundamentalDb: Double,
)

data class PressEvent(
    val onsetNanos: Long,
    val verdictNanos: Long,
    val kind: PressKind,
    val hz: Double?,
    val midi: Int?,
    val centsOff: Double?,
    val confidence: Double,
    val clarity: Double,
    val levelDb: Double,
    val octaveEvidence: OctaveEvidence? = null,
)

enum class UnclearReason {
    TWO_NOTES,
    NOISY,
    LOW_CONFIDENCE,
    OCTAVE_UNSURE,
    TOO_QUIET,
    TOO_SHORT,
    NO_SIGNAL,
}

sealed interface Verdict {
    val press: PressEvent?

    data class Correct(override val press: PressEvent) : Verdict
    data class WrongKey(val heardMidi: Int, override val press: PressEvent) : Verdict
    data class WrongOctave(val heardMidi: Int, override val press: PressEvent) : Verdict
    data class Unclear(val reason: UnclearReason, override val press: PressEvent?) : Verdict
    data class IgnoredStale(override val press: PressEvent) : Verdict
}
