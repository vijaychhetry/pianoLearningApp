package com.vijaychhetry.kidspiano.core.diagnostics

import com.vijaychhetry.kidspiano.core.calibration.CalibrationProfile
import com.vijaychhetry.kidspiano.core.calibration.NoteCalibration
import com.vijaychhetry.kidspiano.core.common.PressEvent
import com.vijaychhetry.kidspiano.core.common.PressKind
import com.vijaychhetry.kidspiano.core.common.UnclearReason
import com.vijaychhetry.kidspiano.core.common.Verdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ExportTest {
    @Test
    fun acExport01_calibrationJsonNamesKeysAndHasNoAudio() {
        val profile = CalibrationProfile(
            id = "local",
            createdAtEpochMs = 1L,
            sampleRate = 48000,
            microphoneSource = "UNPROCESSED",
            pianoName = "PSR-F52",
            notes = listOf(
                NoteCalibration(60, 261.63, 262.1, 3.2, 0.97, 4),
            ),
            calibrationQuality = CalibrationProfile.Quality.EXCELLENT,
        )
        val json = calibrationToJson(profile)
        assertTrue(json.contains("\"note\":\"C4\""))
        assertTrue(json.contains("\"medianHz\":262.10"))
        assertTrue(json.contains("PSR-F52"))
        assertFalse(json.contains("pcm", ignoreCase = true))
        assertFalse(json.contains("wav", ignoreCase = true))
    }

    @Test
    fun acExport02_sessionLogRoundTripsAndMarksStalePresses() {
        val press = PressEvent(
            onsetNanos = 100,
            verdictNanos = 96_000_100,
            kind = PressKind.NOTE,
            hz = 329.6,
            midi = 64,
            centsOff = -4.1,
            confidence = 0.94,
            clarity = 0.97,
            levelDb = -31.2,
        )
        val line = SessionLogLine(
            ts = 1L,
            sessionId = "s-1",
            appVersion = "0.5.0",
            activity = "learn",
            levelId = 1,
            promptIndex = 0,
            expectedMidi = 60,
            verdict = Verdict.IgnoredStale(press),
        ).toJsonl()
        val parsed = SessionLogLine.parseJsonl(line)
        assertEquals("IGNORED_STALE", parsed["verdict"])
        assertEquals("C4", parsed["expected"])
        assertEquals("E4", parsed["heard"])
        assertEquals("96", parsed["onsetToVerdictMs"])
    }

    @Test
    fun acR17_theStorageLayerDoesNotWriteAudio() {
        val roots = listOf(
            File("../diagnostics/src/main"),
            File("src/main"),
            File("../../app/src/main/java/com/vijaychhetry/kidspiano"),
        )
        val hits = roots.filter { it.exists() }.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
                val text = file.readText()
                listOf("FileOutputStream", ".wav", "AudioRecord.", "writePcm", "WavWriter")
                    .filter { needle ->
                        needle == "AudioRecord." && text.contains("AudioRecord") &&
                            text.contains("write")
                    }
                    .map { file.path to it }
            }
        }
        val forbidden = roots.filter { it.exists() }.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" }.filter { file ->
                val text = file.readText()
                text.contains("WavWriter") ||
                    text.contains("writePcm") ||
                    Regex("""\.wav["']""").containsMatchIn(text)
            }.map { it.path }
        }
        assertTrue(forbidden.isEmpty(), "audio writers: $forbidden $hits")
    }
}
