package com.vijaychhetry.kidspiano.core.calibration

import com.vijaychhetry.kidspiano.core.notes.centsOff
import kotlin.math.abs

/**
 * Guided calibration capture (spec §§9–11): ask for one known key at a time,
 * collect several clean samples, reject anything that is not the key we asked
 * for, then hand the samples to a [CalibrationEngine].
 */
class CalibrationSession(
    val notes: List<Int> = MVP_CALIBRATION_MIDI,
    private val samplesPerNote: Int = 8,
    private val maxCentsFromExpected: Double = 45.0,
    private val minConfidence: Double = 0.6,
) {
    enum class Offer { ACCEPTED, WRONG_KEY, UNCLEAR, DONE }

    private val accepted = LinkedHashMap<Int, MutableList<Double>>()
    private var index = 0

    /** The key the child is being asked to play, or null when finished. */
    val currentMidi: Int? get() = notes.getOrNull(index)

    val isComplete: Boolean get() = index >= notes.size

    fun acceptedCount(midi: Int): Int = accepted[midi]?.size ?: 0

    fun samplesNeeded(midi: Int): Int = (samplesPerNote - acceptedCount(midi)).coerceAtLeast(0)

    /** 0f..1f across the whole session, for a progress bar. */
    val progress: Float
        get() {
            val total = notes.size * samplesPerNote
            val done = notes.sumOf { acceptedCount(it).coerceAtMost(samplesPerNote) }
            return if (total == 0) 1f else done.toFloat() / total
        }

    fun offer(midi: Int?, frequency: Double?, confidence: Double): Offer {
        val target = currentMidi ?: return Offer.DONE
        if (midi == null || frequency == null || confidence < minConfidence) return Offer.UNCLEAR
        if (midi != target) return Offer.WRONG_KEY
        if (abs(centsOff(frequency, target)) > maxCentsFromExpected) return Offer.UNCLEAR
        val bucket = accepted.getOrPut(target) { mutableListOf() }
        bucket += frequency
        if (bucket.size >= samplesPerNote) index++
        return Offer.ACCEPTED
    }

    /** Move on without samples; the profile will score the gap honestly. */
    fun skipCurrent() {
        if (!isComplete) index++
    }

    fun restart() {
        accepted.clear()
        index = 0
    }

    fun samplesByMidi(): Map<Int, List<Double>> = accepted.mapValues { it.value.toList() }

    fun buildProfile(
        engine: CalibrationEngine,
        sampleRate: Int,
        microphoneSource: String,
    ): CalibrationProfile = engine.buildProfile(samplesByMidi(), sampleRate, microphoneSource)
}
