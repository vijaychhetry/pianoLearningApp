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

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_QUALITY = "quality"
        const val KEY_NOTES = "notes"
        const val KEY_SOURCE = "source"
        const val KEY_SAMPLE_RATE = "sampleRate"
        const val KEY_SAVED_AT = "savedAt"
    }
}
