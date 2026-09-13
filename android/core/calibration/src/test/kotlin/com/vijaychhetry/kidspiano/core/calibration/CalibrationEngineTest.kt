package com.vijaychhetry.kidspiano.core.calibration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CalibrationEngineTest {
    @Test
    fun poorIfTooFewSamples() {
        val engine = MedianCalibrationEngine()
        val profile = engine.buildProfile(
            samplesByMidi = mapOf(60 to listOf(261.0, 262.0)),
            sampleRate = 44100,
            microphoneSource = "UNPROCESSED",
        )
        assertEquals(CalibrationProfile.Quality.NEEDS_IMPROVEMENT, profile.calibrationQuality)
    }

    @Test
    fun excellentWhenStable() {
        val engine = MedianCalibrationEngine()
        val freqs = List(12) { 261.6 + (it % 3) * 0.1 }
        val profile = engine.buildProfile(
            mapOf(60 to freqs, 62 to freqs.map { it * 1.122 }, 64 to freqs.map { it * 1.26 }),
            44100,
            "UNPROCESSED",
        )
        assertEquals(CalibrationProfile.Quality.EXCELLENT, profile.calibrationQuality)
    }
}
