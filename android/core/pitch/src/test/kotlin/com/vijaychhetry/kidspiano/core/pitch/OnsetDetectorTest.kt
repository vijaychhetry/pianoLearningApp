package com.vijaychhetry.kidspiano.core.pitch

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnsetDetectorTest {
    @Test
    fun acOnset01_silenceIsNotAnOnset() {
        val d = OnsetDetector()
        repeat(20) { i ->
            val r = d.onHop(FloatArray(512), i * 12L)
            assertFalse(r.onset, "silence hop $i")
        }
    }

    @Test
    fun acOnset02_aToneAfterSilenceIsAnOnset() {
        val d = OnsetDetector()
        repeat(8) { d.onHop(FloatArray(512), it * 12L) }
        val tone = pianoTone(261.63, 44100, 0.02, amplitude = 0.4f).copyOfRange(200, 712)
        val r = d.onHop(tone, 200)
        assertTrue(r.onset, "rmsDb=${r.rmsDb} floor=${r.floorDb}")
    }

    @Test
    fun acOnset03_aHeldToneIsNotASecondOnset() {
        val d = OnsetDetector()
        repeat(8) { d.onHop(FloatArray(512), it * 12L) }
        val tone = pianoTone(261.63, 44100, 0.4, amplitude = 0.4f)
        var onsets = 0
        var t = 200L
        var i = 0
        while (i + 512 < tone.size) {
            if (d.onHop(tone.copyOfRange(i, i + 512), t).onset) onsets++
            i += 512
            t += 12
        }
        assertTrue(onsets == 1, "held tone produced $onsets onsets")
    }

    @Test
    fun acOnset04_aNewAttackOnADecayingToneIsASecondOnset() {
        val d = OnsetDetector()
        repeat(6) { d.onHop(FloatArray(512), it * 12L) }
        val first = pianoTone(261.63, 44100, 0.25, amplitude = 0.45f)
        val decay = FloatArray(44100) { i ->
            val src = first.getOrElse(i) { 0f }
            (src * Math.exp(-3.0 * i / 44100.0)).toFloat()
        }
        val second = pianoTone(261.63, 44100, 0.2, amplitude = 0.45f)
        var onsets = 0
        var t = 100L
        fun feed(buf: FloatArray) {
            var i = 0
            while (i + 512 <= buf.size) {
                if (d.onHop(buf.copyOfRange(i, i + 512), t).onset) onsets++
                i += 512
                t += 12
            }
        }
        feed(decay)
        // gap of 160 ms of quiet-but-not-zero so MIN_ONSET_GAP_MS is satisfied
        repeat(16) {
            d.onHop(FloatArray(512) { 0.002f }, t)
            t += 12
        }
        feed(second)
        assertTrue(onsets >= 2, "re-press was not heard; onsets=$onsets")
    }
}
