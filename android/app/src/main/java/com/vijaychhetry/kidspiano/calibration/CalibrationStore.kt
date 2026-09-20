package com.vijaychhetry.kidspiano.calibration

import android.content.Context
import com.vijaychhetry.kidspiano.core.calibration.CalibrationProfile
import com.vijaychhetry.kidspiano.core.calibration.formatSavedNotes
import com.vijaychhetry.kidspiano.core.calibration.parseSavedProfile

/**
 * Minimal local persistence for the calibration profile (spec §12 calls for
 * Room/DataStore; this keeps Phase 2 dependency-free until storage lands).
 * No raw audio is stored.
 */
class CalibrationStore(context: Context) {
    private val prefs = context.getSharedPreferences("calibration", Context.MODE_PRIVATE)

    fun save(profile: CalibrationProfile) {
        prefs.edit()
            .putString(KEY_QUALITY, profile.calibrationQuality.name)
            .putString(KEY_SOURCE, profile.microphoneSource)
            .putInt(KEY_SAMPLE_RATE, profile.sampleRate)
            .putLong(KEY_SAVED_AT, profile.createdAtEpochMs)
            .putString(KEY_NOTES, formatSavedNotes(profile.notes))
            .apply()
    }

    fun load(): CalibrationProfile? {
        val quality = prefs.getString(KEY_QUALITY, null) ?: return null
        return parseSavedProfile(
            quality = quality,
            notesCsv = prefs.getString(KEY_NOTES, "") ?: return null,
            source = prefs.getString(KEY_SOURCE, "—") ?: "—",
            sampleRate = prefs.getInt(KEY_SAMPLE_RATE, 44100),
            savedAt = prefs.getLong(KEY_SAVED_AT, 0L),
        )
    }

    /** One line for the UI, or null when the piano has never been calibrated. */
    fun summary(): String? {
        val quality = prefs.getString(KEY_QUALITY, null) ?: return null
        val notes = prefs.getString(KEY_NOTES, "").orEmpty()
        val count = if (notes.isBlank()) 0 else notes.split(",").size
        val source = prefs.getString(KEY_SOURCE, "—")
        return "Saved profile: $quality · $count notes · $source"
    }

    fun lessonMidi(): List<Int> {
        val raw = prefs.getString(KEY_LESSON, null) ?: return com.vijaychhetry.kidspiano.core.notes.defaultLessonMidi()
        val parsed = raw.split(',').mapNotNull { it.toIntOrNull() }
        return parsed.ifEmpty { com.vijaychhetry.kidspiano.core.notes.defaultLessonMidi() }
    }

    fun lessonSetId(): String {
        prefs.getString(KEY_SET, null)?.let { return it }
        val midi = lessonMidi()
        return com.vijaychhetry.kidspiano.core.notes.lessonSets()
            .firstOrNull { it.notes == midi && !it.shuffle }
            ?.id
            ?: com.vijaychhetry.kidspiano.core.notes.DEFAULT_LESSON_SET_ID
    }

    fun lessonSet(): com.vijaychhetry.kidspiano.core.notes.LessonSet =
        com.vijaychhetry.kidspiano.core.notes.lessonSetById(lessonSetId())

    fun saveLessonSetId(id: String) {
        val set = com.vijaychhetry.kidspiano.core.notes.lessonSetById(id)
        prefs.edit()
            .putString(KEY_SET, set.id)
            .putString(KEY_LESSON, set.notes.joinToString(","))
            .apply()
    }

    fun saveLessonMidi(notes: List<Int>) {
        val match = com.vijaychhetry.kidspiano.core.notes.lessonSets()
            .firstOrNull { it.notes == notes && !it.shuffle }
        prefs.edit()
            .putString(KEY_LESSON, notes.joinToString(","))
            .putString(KEY_SET, match?.id ?: lessonSetId())
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_QUALITY = "quality"
        const val KEY_NOTES = "notes"
        const val KEY_SOURCE = "source"
        const val KEY_SAMPLE_RATE = "sampleRate"
        const val KEY_SAVED_AT = "savedAt"
        const val KEY_LESSON = "lessonMidi"
        const val KEY_SET = "lessonSet"
    }
}
