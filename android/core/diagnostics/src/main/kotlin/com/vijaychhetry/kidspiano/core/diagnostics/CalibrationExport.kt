package com.vijaychhetry.kidspiano.core.diagnostics

import com.vijaychhetry.kidspiano.core.calibration.CalibrationProfile
import com.vijaychhetry.kidspiano.core.notes.midiToNoteName
import java.util.Locale

fun calibrationToJson(profile: CalibrationProfile): String {
    val keys = profile.notes.joinToString(",") { note ->
        """{"note":"${midiToNoteName(note.midiNote)}","midi":${note.midiNote},"expectedHz":${fmt(note.expectedFrequency)},"medianHz":${fmt(note.observedMedianFrequency)},"spreadHz":${fmt(note.frequencySpread)},"samples":${note.sampleCount},"confidence":${fmt(note.confidence)}}"""
    }
    val piano = profile.pianoName?.let { "\"${escape(it)}\"" } ?: "null"
    return """{"v":1,"createdAt":${profile.createdAtEpochMs},"quality":"${profile.calibrationQuality.name}","sampleRate":${profile.sampleRate},"micSource":"${escape(profile.microphoneSource)}","pianoName":$piano,"keys":[$keys]}"""
}

private fun fmt(n: Double): String = String.format(Locale.US, "%.2f", n)

private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
