package com.vijaychhetry.kidspiano.core.pitch

import com.vijaychhetry.kidspiano.core.common.RecognitionStatus

data class LabLogEntry(
    val atMs: Long,
    val label: String,
    val status: RecognitionStatus,
    val confidence: Double,
    val hz: Double?,
)

/**
 * Audio Lab scrolling log (spec §15).
 *
 * The log must never sit empty while the microphone is running: a silent room
 * is itself a reading. A line is written when the status or note changes, and a
 * heartbeat line is written when nothing has changed for [heartbeatMs] so the
 * screen proves frames are still arriving.
 */
class LabEventLog(
    private val capacity: Int = 40,
    private val heartbeatMs: Long = 2_000,
) {
    private val buffer = ArrayDeque<LabLogEntry>()
    private var lastAppendedAtMs: Long? = null

    /** Newest first, for a top-anchored list. */
    val entries: List<LabLogEntry> get() = buffer.toList()

    fun onFrame(
        status: RecognitionStatus,
        label: String,
        confidence: Double,
        hz: Double?,
        atMs: Long,
    ): Boolean {
        val last = buffer.firstOrNull()
        val changed = last == null || last.status != status || last.label != label
        val stale = lastAppendedAtMs?.let { atMs - it >= heartbeatMs } ?: true
        if (!changed && !stale) return false
        buffer.addFirst(LabLogEntry(atMs, label, status, confidence, hz))
        while (buffer.size > capacity) buffer.removeLast()
        lastAppendedAtMs = atMs
        return true
    }

    fun clear() {
        buffer.clear()
        lastAppendedAtMs = null
    }
}
