package com.vijaychhetry.kidspiano.core.calibration

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
}

interface CalibrationEngine {
    fun buildProfile(
        samplesByMidi: Map<Int, List<Double>>,
        sampleRate: Int,
        microphoneSource: String,
    ): CalibrationProfile
}

class MedianCalibrationEngine : CalibrationEngine {
    override fun buildProfile(
        samplesByMidi: Map<Int, List<Double>>,
        sampleRate: Int,
        microphoneSource: String,
    ): CalibrationProfile {
        val notes = samplesByMidi.map { (midi, freqs) ->
            val sorted = freqs.sorted()
            val median = percentile(sorted, 50.0)
            val spread = percentile(sorted, 75.0) - percentile(sorted, 25.0)
            NoteCalibration(
                midiNote = midi,
                expectedFrequency = 440.0 * Math.pow(2.0, (midi - 69) / 12.0),
                observedMedianFrequency = median,
                frequencySpread = spread,
                confidence = if (sorted.size >= 8 && spread < 3.0) 0.95 else 0.7,
                sampleCount = sorted.size,
            )
        }
        val quality = when {
            notes.any { it.sampleCount < 6 } -> CalibrationProfile.Quality.NEEDS_IMPROVEMENT
            notes.all { it.confidence >= 0.9 } -> CalibrationProfile.Quality.EXCELLENT
            else -> CalibrationProfile.Quality.GOOD
        }
        return CalibrationProfile(
            id = "local",
            createdAtEpochMs = System.currentTimeMillis(),
            sampleRate = sampleRate,
            microphoneSource = microphoneSource,
            notes = notes,
            calibrationQuality = quality,
        )
    }
}

internal fun percentile(sorted: List<Double>, p: Double): Double {
    if (sorted.isEmpty()) return 0.0
    val idx = ((p / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.lastIndex)
    return sorted[idx]
}
