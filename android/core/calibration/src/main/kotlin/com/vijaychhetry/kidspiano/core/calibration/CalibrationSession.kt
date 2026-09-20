package com.vijaychhetry.kidspiano.core.calibration

import com.vijaychhetry.kidspiano.core.notes.centsOff
import com.vijaychhetry.kidspiano.core.notes.hearable
import com.vijaychhetry.kidspiano.core.notes.isWhiteKey
import kotlin.math.abs

/**
 * Guided calibration capture (spec §§9–11): ask for one known key at a time,
 * collect several clean samples, reject anything that is not the key we asked
 * for, then hand the samples to a [CalibrationEngine].
 *
 * One sample means one key press. Frames arrive about every 46 ms, so taking a
 * sample per frame would fill the profile from a single sustained note and
 * measure the detector's own jitter rather than the piano's spread. The caller
 * must report the key being released via [onRelease] before the next sample of
 * the same note is accepted.
 */
class CalibrationSession(
    notes: List<Int> = MVP_CALIBRATION_MIDI,
    val samplesPerNote: Int = 4,
    private val maxCentsFromExpected: Double = 45.0,
    private val minConfidence: Double = 0.6,
) {
    enum class Offer { ACCEPTED, WRONG_KEY, UNCLEAR, SAME_PRESS, DONE }

    private val noteOrder = notes.toMutableList()
    val notes: List<Int> get() = noteOrder
    private val accepted = LinkedHashMap<Int, MutableList<Double>>()
    private var index = 0
    private var sampledThisPress = false

    /** The key the child is being asked to play, or null when finished. */
    val currentMidi: Int? get() = noteOrder.getOrNull(index)

    val isComplete: Boolean get() = index >= noteOrder.size

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
        if (sampledThisPress) return Offer.SAME_PRESS
        val bucket = accepted.getOrPut(target) { mutableListOf() }
        bucket += frequency
        sampledThisPress = true
        if (bucket.size >= samplesPerNote) {
            index++
            sampledThisPress = false
        }
        return Offer.ACCEPTED
    }

    /** The key was let go, so the next offer starts a new press. */
    fun onRelease() {
        sampledThisPress = false
    }

    /** Move on without samples; the profile will score the gap honestly. */
    fun skipCurrent() {
        if (!isComplete) index++
        sampledThisPress = false
    }

    fun restart() {
        accepted.clear()
        index = 0
        sampledThisPress = false
    }

    /**
     * Jump the dropdown to [midi]. Hearable white keys only. Re-measuring
     * a sampled key clears its samples (spec v4 §4.6).
     */
    fun jumpTo(midi: Int): Boolean {
        if (!isWhiteKey(midi) || !hearable(midi)) return false
        if (midi !in noteOrder) {
            val insert = noteOrder.indexOfFirst { it > midi }.let { if (it < 0) noteOrder.size else it }
            noteOrder.add(insert, midi)
        }
        accepted.remove(midi)
        index = noteOrder.indexOf(midi)
        sampledThisPress = false
        return true
    }

    fun samplesByMidi(): Map<Int, List<Double>> = accepted.mapValues { it.value.toList() }

    fun buildProfile(
        engine: CalibrationEngine,
        sampleRate: Int,
        microphoneSource: String,
    ): CalibrationProfile = engine.buildProfile(samplesByMidi(), sampleRate, microphoneSource)
}
