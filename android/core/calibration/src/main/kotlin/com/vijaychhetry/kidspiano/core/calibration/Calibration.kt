package com.vijaychhetry.kidspiano.core.calibration

import com.vijaychhetry.kidspiano.core.notes.A4_HZ
import com.vijaychhetry.kidspiano.core.notes.centsOff
import com.vijaychhetry.kidspiano.core.notes.midiToFreq
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

data class NoteCalibration(
    val midiNote: Int,
    val expectedFrequency: Double,
    val observedMedianFrequency: Double,
    val frequencySpread: Double,
    val confidence: Double,
    val sampleCount: Int,
)

data class CalibrationProfile(
    val id: String,
    val createdAtEpochMs: Long,
    val sampleRate: Int,
    val microphoneSource: String,
    val pianoName: String? = null,
    val notes: List<NoteCalibration>,
    val calibrationQuality: Quality,
) {
    enum class Quality { EXCELLENT, GOOD, NEEDS_IMPROVEMENT }

    /**
     * Spec §11: a poor profile must not silently become the production
     * default. The app may still store one the user explicitly accepts.
     */
    val usableAsDefault: Boolean
        get() = calibrationQuality != Quality.NEEDS_IMPROVEMENT
}

interface CalibrationEngine {
    fun buildProfile(
        samplesByMidi: Map<Int, List<Double>>,
        sampleRate: Int,
        microphoneSource: String,
    ): CalibrationProfile
}

/**
 * Per-note statistical profile (spec §§9–11). Quality is derived from
 * coverage of MVP notes C4–G4, kept-sample count, and IQR spread — never
 * from the first raw capture alone.
 */
class MedianCalibrationEngine(
    private val requiredMidi: List<Int> = MVP_CALIBRATION_MIDI,
    private val maxCentsFromExpected: Double = 40.0,
    private val minSamples: Int = 3,
    private val excellentSamples: Int = 4,
    private val excellentSpreadHz: Double = 3.0,
    private val goodSpreadHz: Double = 10.0,
) : CalibrationEngine {
    override fun buildProfile(
        samplesByMidi: Map<Int, List<Double>>,
        sampleRate: Int,
        microphoneSource: String,
    ): CalibrationProfile {
        val notes = samplesByMidi.map { (midi, freqs) ->
            val expected = midiToFreq(midi)
            val kept = freqs.filter { abs(centsOff(it, midi)) <= maxCentsFromExpected }.sorted()
            val median = percentile(kept, 50.0)
            val spread = if (kept.size >= 2) {
                percentile(kept, 75.0) - percentile(kept, 25.0)
            } else {
                0.0
            }
            NoteCalibration(
                midiNote = midi,
                expectedFrequency = expected,
                observedMedianFrequency = median,
                frequencySpread = spread,
                confidence = noteConfidence(kept.size, spread),
                sampleCount = kept.size,
            )
        }.sortedBy { it.midiNote }

        return CalibrationProfile(
            id = "local",
            createdAtEpochMs = System.currentTimeMillis(),
            sampleRate = sampleRate,
            microphoneSource = microphoneSource,
            notes = notes,
            calibrationQuality = scoreQuality(notes),
        )
    }

    private fun noteConfidence(sampleCount: Int, spread: Double): Double = when {
        sampleCount >= excellentSamples && spread < excellentSpreadHz -> 0.95
        sampleCount >= minSamples && spread < goodSpreadHz -> 0.8
        sampleCount >= minSamples -> 0.6
        else -> 0.4
    }

    private fun scoreQuality(notes: List<NoteCalibration>): CalibrationProfile.Quality {
        val byMidi = notes.associateBy { it.midiNote }
        val missingOrThin = requiredMidi.any { midi ->
            val note = byMidi[midi]
            note == null || note.sampleCount < minSamples
        }
        if (missingOrThin) return CalibrationProfile.Quality.NEEDS_IMPROVEMENT
        val required = requiredMidi.map { byMidi.getValue(it) }
        val allExcellent = required.all {
            it.sampleCount >= excellentSamples && it.frequencySpread < excellentSpreadHz
        }
        if (allExcellent) return CalibrationProfile.Quality.EXCELLENT
        val tooWide = required.any { it.frequencySpread > goodSpreadHz }
        if (tooWide) return CalibrationProfile.Quality.NEEDS_IMPROVEMENT
        return CalibrationProfile.Quality.GOOD
    }
}

/** Level-1 five white keys: C4 D4 E4 F4 G4. */
val MVP_CALIBRATION_MIDI: List<Int> = listOf(60, 62, 64, 65, 67)

/**
 * Geometric-mean ratio of observed / expected frequencies, applied to A440.
 * Live recognition uses this A4 so a piano that is tens of cents off concert
 * pitch is still named as the key the child pressed.
 */
fun concertA4Hz(profile: CalibrationProfile): Double {
    val usable = profile.notes.filter {
        it.expectedFrequency > 0.0 && it.observedMedianFrequency > 0.0
    }
    if (usable.isEmpty()) return A4_HZ
    val logMean = usable.sumOf {
        Math.log(it.observedMedianFrequency / it.expectedFrequency)
    } / usable.size
    return A4_HZ * Math.exp(logMean)
}

/**
 * Rebuild a profile from the compact string [CalibrationStore] persists.
 * Spread is not stored; quality already decided whether it may be the default.
 * Decimals are always a period so a comma locale cannot split the CSV.
 */
fun formatSavedNotes(notes: List<NoteCalibration>): String =
    notes.joinToString(",") {
        val freq = String.format(Locale.US, "%.2f", it.observedMedianFrequency)
        val spread = String.format(Locale.US, "%.2f", it.frequencySpread)
        "${it.midiNote}:$freq:${it.sampleCount}:$spread"
    }
fun parseSavedProfile(
    quality: String,
    notesCsv: String,
    source: String,
    sampleRate: Int,
    savedAt: Long,
): CalibrationProfile? {
    val parsedQuality = try {
        CalibrationProfile.Quality.valueOf(quality)
    } catch (_: IllegalArgumentException) {
        return null
    }
    if (notesCsv.isBlank()) return null
    val notes = notesCsv.split(',').mapNotNull { token ->
        val parts = token.split(':')
        if (parts.size < 2) return@mapNotNull null
        val midi = parts[0].toIntOrNull() ?: return@mapNotNull null
        val freq = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        val count = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val spread = parts.getOrNull(3)?.toDoubleOrNull() ?: 0.0
        NoteCalibration(
            midiNote = midi,
            expectedFrequency = midiToFreq(midi),
            observedMedianFrequency = freq,
            frequencySpread = spread,
            confidence = if (count >= 4) 0.95 else 0.6,
            sampleCount = count,
        )
    }
    if (notes.isEmpty()) return null
    return CalibrationProfile(
        id = "local",
        createdAtEpochMs = savedAt,
        sampleRate = sampleRate,
        microphoneSource = source,
        notes = notes,
        calibrationQuality = parsedQuality,
    )
}

internal fun percentile(sorted: List<Double>, p: Double): Double {
    if (sorted.isEmpty()) return 0.0
    if (sorted.size == 1) return sorted[0]
    val rank = (p / 100.0) * (sorted.size - 1)
    val lo = floor(rank).toInt()
    val hi = ceil(rank).toInt().coerceAtMost(sorted.lastIndex)
    if (lo == hi) return sorted[lo]
    val w = rank - lo
    return sorted[lo] * (1.0 - w) + sorted[hi] * w
}
