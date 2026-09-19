package com.vijaychhetry.kidspiano.core.diagnostics

import com.vijaychhetry.kidspiano.core.common.Verdict
import com.vijaychhetry.kidspiano.core.notes.midiToNoteName
import java.util.Locale

data class SessionLogLine(
    val ts: Long,
    val sessionId: String,
    val appVersion: String,
    val activity: String,
    val levelId: Int?,
    val promptIndex: Int,
    val expectedMidi: Int?,
    val verdict: Verdict,
    val hinted: Boolean = false,
    val helped: Boolean = false,
    val wrongCountThisPrompt: Int = 0,
    val timestampEstimated: Boolean = false,
) {
    fun toJsonl(): String {
        val press = verdict.press
        val heardMidi = when (verdict) {
            is Verdict.Correct -> press?.midi
            is Verdict.WrongKey -> verdict.heardMidi
            is Verdict.WrongOctave -> verdict.heardMidi
            else -> press?.midi
        }
        val name = when (verdict) {
            is Verdict.Correct -> "CORRECT"
            is Verdict.WrongKey -> "WRONG_KEY"
            is Verdict.WrongOctave -> "WRONG_OCTAVE"
            is Verdict.Unclear -> "UNCLEAR"
            is Verdict.IgnoredStale -> "IGNORED_STALE"
        }
        val reason = (verdict as? Verdict.Unclear)?.reason?.name
        val onset = press?.onsetNanos
        val decided = press?.verdictNanos
        val latency = if (onset != null && decided != null) (decided - onset) / 1_000_000 else null
        return buildString {
            append("{")
            append("\"v\":1,")
            append("\"ts\":$ts,")
            append("\"sessionId\":\"${escape(sessionId)}\",")
            append("\"appVersion\":\"${escape(appVersion)}\",")
            append("\"activity\":\"${escape(activity)}\",")
            append("\"levelId\":${levelId ?: "null"},")
            append("\"promptIndex\":$promptIndex,")
            append("\"expected\":${expectedMidi?.let { "\"${midiToNoteName(it)}\"" } ?: "null"},")
            append("\"expectedMidi\":${expectedMidi ?: "null"},")
            append("\"heard\":${heardMidi?.let { "\"${midiToNoteName(it)}\"" } ?: "null"},")
            append("\"heardMidi\":${heardMidi ?: "null"},")
            append("\"verdict\":\"$name\",")
            append("\"unclearReason\":${reason?.let { "\"$it\"" } ?: "null"},")
            append("\"confidence\":${fmt(press?.confidence ?: 0.0)},")
            append("\"clarity\":${fmt(press?.clarity ?: 0.0)},")
            append("\"levelDb\":${fmt(press?.levelDb ?: 0.0)},")
            append("\"centsOff\":${press?.centsOff?.let { fmt(it) } ?: "null"},")
            append("\"onsetNanos\":${onset ?: "null"},")
            append("\"verdictNanos\":${decided ?: "null"},")
            append("\"onsetToVerdictMs\":${latency ?: "null"},")
            append("\"timestampEstimated\":$timestampEstimated,")
            append("\"hinted\":$hinted,")
            append("\"helped\":$helped,")
            append("\"wrongCountThisPrompt\":$wrongCountThisPrompt")
            append("}")
        }
    }

    companion object {
        fun parseJsonl(line: String): Map<String, String> {
            val out = linkedMapOf<String, String>()
            val body = line.trim().removePrefix("{").removeSuffix("}")
            val regex = """"([^"]+)":("(?:\\.|[^"])*"|[^,}]+)""".toRegex()
            regex.findAll(body).forEach { m ->
                out[m.groupValues[1]] = m.groupValues[2].trim().removePrefix("\"").removeSuffix("\"")
            }
            return out
        }
    }
}

private fun fmt(n: Double): String = String.format(Locale.US, "%.2f", n)
private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
