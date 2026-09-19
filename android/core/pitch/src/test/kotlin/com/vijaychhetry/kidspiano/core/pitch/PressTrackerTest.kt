package com.vijaychhetry.kidspiano.core.pitch

import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.common.PressKind
import com.vijaychhetry.kidspiano.core.notes.midiToFreq
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val SR = 44100
private const val FRAME_MS = 46L

class PressTrackerTest {
    @Test
    fun acR02_aHeldKeyProducesOnePressEvent() {
        val tracker = PressTracker()
        var events = 0
        var lastKind: PressKind? = null
        repeat(20) { i ->
            val e = tracker.onFrame(tone(60, i))
            if (e != null) {
                events++
                lastKind = e.kind
            }
        }
        assertEquals(1, events)
        assertEquals(PressKind.NOTE, lastKind)
    }

    @Test
    fun acR03_aSecondStrikeAfterReleaseIsASecondPress() {
        val tracker = PressTracker()
        var events = 0
        repeat(10) { tracker.onFrame(tone(60, it)) }
        repeat(8) { i -> tracker.onFrame(AudioFrame(FloatArray(2048), SR, (10 + i) * FRAME_MS)) }
        repeat(10) { i ->
            if (tracker.onFrame(tone(60, 20 + i)) != null) events++
        }
        assertTrue(events >= 1, "second strike produced no event")
    }

    @Test
    fun acR05_holdingCWhileNothingNewHappensDoesNotEmitAgain() {
        val tracker = PressTracker()
        val first = (0 until 10).map { tracker.onFrame(tone(60, it)) }.firstOrNull { it != null }
        assertNotNull(first)
        val later = (10 until 18).map { tracker.onFrame(tone(60, it)) }
        assertTrue(later.all { it == null })
    }

    @Test
    fun acPress01_silenceEmitsNothing() {
        val tracker = PressTracker()
        assertNull(tracker.onFrame(AudioFrame(FloatArray(2048), SR, 0)))
    }

    private fun tone(midi: Int, index: Int): AudioFrame {
        val t = pianoTone(midiToFreq(midi), SR, 1.0)
        val start = (2000 + index % 8 * 64).coerceAtMost(t.size - 2048)
        return AudioFrame(t.copyOfRange(start, start + 2048), SR, index * FRAME_MS)
    }
}
