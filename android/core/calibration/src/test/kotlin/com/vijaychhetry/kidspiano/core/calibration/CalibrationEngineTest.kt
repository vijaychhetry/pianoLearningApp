package com.vijaychhetry.kidspiano.core.calibration

import com.vijaychhetry.kidspiano.core.notes.midiToFreq
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * AC-CAL-* — statistical calibration, not "first sample wins" / enum-only checks.
 */
class CalibrationEngineTest {
    private val engine = MedianCalibrationEngine()

    @Test
    fun acCal01_tooFewSamplesCannotBeExcellentOrGood() {
        val profile = engine.buildProfile(
            samplesByMidi = mapOf(60 to listOf(261.0, 262.0)),
            sampleRate = 44100,
            microphoneSource = "UNPROCESSED",
        )
        assertEquals(CalibrationProfile.Quality.NEEDS_IMPROVEMENT, profile.calibrationQuality)
        val c4 = profile.notes.single { it.midiNote == 60 }
        assertEquals(2, c4.sampleCount)
        assertTrue(c4.confidence < 0.9)
    }

    @Test
    fun acCal02_missingG4CannotBeExcellent() {
        val profile = engine.buildProfile(
            mapOf(
                60 to cluster(60),
                62 to cluster(62),
                64 to cluster(64),
                65 to cluster(65),
            ),
            44100,
            "UNPROCESSED",
        )
        assertNotEquals(CalibrationProfile.Quality.EXCELLENT, profile.calibrationQuality)
        assertEquals(CalibrationProfile.Quality.NEEDS_IMPROVEMENT, profile.calibrationQuality)
        assertTrue(profile.notes.none { it.midiNote == 67 })
    }

    @Test
    fun acCal03_medianIgnoresFirstSampleAndDropsWrongKey() {
        val clusterHz = 262.20
        val captures = listOf(261.0, 400.0) + List(10) { clusterHz }
        val profile = engine.buildProfile(
            mapOf(60 to captures) + completeExcept(60),
            44100,
            "UNPROCESSED",
        )
        val c4 = profile.notes.single { it.midiNote == 60 }
        assertEquals(11, c4.sampleCount, "400 Hz is >40¢ from C4 and must be dropped")
        assertEquals(clusterHz, c4.observedMedianFrequency, 0.05)
        assertNotEquals(captures.first(), c4.observedMedianFrequency, 0.01)
        assertTrue(c4.observedMedianFrequency < 300.0, "outlier 400 Hz must not become the profile")
        assertEquals(261.625565, c4.expectedFrequency, 0.01)
    }

    @Test
    fun acCal04_excellentRequiresAllFiveNotesTightSpreadAndEnoughSamples() {
        val samples = MVP_CALIBRATION_MIDI.associateWith { cluster(it, count = 12, jitterHz = 0.08) }
        val profile = engine.buildProfile(samples, 44100, "UNPROCESSED")
        assertEquals(CalibrationProfile.Quality.EXCELLENT, profile.calibrationQuality)
        assertEquals(setOf(60, 62, 64, 65, 67), profile.notes.map { it.midiNote }.toSet())
        for (note in profile.notes) {
            assertTrue(note.sampleCount >= 8, "midi ${note.midiNote} sampleCount=${note.sampleCount}")
            assertTrue(note.frequencySpread < 3.0, "midi ${note.midiNote} spread=${note.frequencySpread}")
            assertEquals(midiToFreq(note.midiNote), note.observedMedianFrequency, 0.5)
            assertEquals(midiToFreq(note.midiNote), note.expectedFrequency, 0.01)
        }
    }

    @Test
    fun acCal05_wideSpreadOnEveryNoteIsNeedsImprovement() {
        val samples = MVP_CALIBRATION_MIDI.associateWith { midi ->
            val f = midiToFreq(midi)
            val lo = f * Math.pow(2.0, -35.0 / 1200.0)
            val hi = f * Math.pow(2.0, 35.0 / 1200.0)
            List(6) { lo } + List(6) { hi }
        }
        val profile = engine.buildProfile(samples, 44100, "UNPROCESSED")
        assertEquals(CalibrationProfile.Quality.NEEDS_IMPROVEMENT, profile.calibrationQuality)
        for (note in profile.notes) {
            assertTrue(note.frequencySpread > 10.0, "midi ${note.midiNote} spread=${note.frequencySpread}")
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [60, 62, 64, 65, 67])
    fun acCal04b_excellentMedianIsTheNamedNote(midi: Int) {
        val samples = MVP_CALIBRATION_MIDI.associateWith { cluster(it) }
        val profile = engine.buildProfile(samples, 44100, "UNPROCESSED")
        val note = profile.notes.single { it.midiNote == midi }
        assertEquals(midiToFreq(midi), note.observedMedianFrequency, 0.4)
        assertTrue(note.frequencySpread < 1.0)
    }

    @Test
    fun acCal06_goodIsReachableAndIsNotExcellent() {
        val samples = MVP_CALIBRATION_MIDI.associateWith { cluster(it, count = 3, jitterHz = 2.0) }
        val profile = engine.buildProfile(samples, 44100, "MIC")
        assertEquals(
            CalibrationProfile.Quality.GOOD,
            profile.calibrationQuality,
            "all five notes present and inside the good spread, but too few presses for excellent",
        )
        for (note in profile.notes) {
            assertEquals(3, note.sampleCount)
            assertTrue(note.frequencySpread < 10.0, "midi ${note.midiNote} spread=${note.frequencySpread}")
        }
    }

    @Test
    fun acCal07_twoPressesPerNoteIsNeverGoodEnough() {
        val samples = MVP_CALIBRATION_MIDI.associateWith { cluster(it, count = 2, jitterHz = 0.05) }
        val profile = engine.buildProfile(samples, 44100, "MIC")
        assertEquals(
            CalibrationProfile.Quality.NEEDS_IMPROVEMENT,
            profile.calibrationQuality,
            "two samples cannot describe how a note varies, however tight they look",
        )
    }

    @Test
    fun acCal08_onlyExcellentOrGoodMayBecomeTheDefault() {
        val excellent = engine.buildProfile(
            MVP_CALIBRATION_MIDI.associateWith { cluster(it, count = 12, jitterHz = 0.08) },
            44100,
            "MIC",
        )
        val good = engine.buildProfile(
            MVP_CALIBRATION_MIDI.associateWith { cluster(it, count = 3, jitterHz = 2.0) },
            44100,
            "MIC",
        )
        val poor = engine.buildProfile(mapOf(60 to cluster(60)), 44100, "MIC")
        assertEquals(CalibrationProfile.Quality.EXCELLENT, excellent.calibrationQuality)
        assertEquals(CalibrationProfile.Quality.GOOD, good.calibrationQuality)
        assertEquals(CalibrationProfile.Quality.NEEDS_IMPROVEMENT, poor.calibrationQuality)
        assertTrue(excellent.usableAsDefault)
        assertTrue(good.usableAsDefault)
        assertFalse(poor.usableAsDefault, "spec §11: a poor profile must not silently become the default")
    }

    @Test
    fun percentileInterpolatesMedian() {
        assertEquals(3.0, percentile(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 50.0), 1e-9)
        assertEquals(2.5, percentile(listOf(1.0, 2.0, 3.0, 4.0), 50.0), 1e-9)
        assertEquals(0.0, percentile(emptyList(), 50.0), 0.0)
    }

    private fun cluster(midi: Int, count: Int = 12, jitterHz: Double = 0.12): List<Double> {
        val f = midiToFreq(midi)
        return List(count) { i -> f + ((i % 5) - 2) * jitterHz }
    }

    /** Other MVP notes as tight clusters so only the overridden midi is under test. */
    private fun completeExcept(midi: Int): Map<Int, List<Double>> =
        MVP_CALIBRATION_MIDI.filter { it != midi }.associateWith { cluster(it) }
}
