package com.vijaychhetry.kidspiano.core.notes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PianoKeyboardTest {
    @Test
    fun acKey01_psrF52IsC2ToC7AndSelectableIsA2ToF6() {
        val keys = psrF52Keys()
        assertEquals(61, keys.size)
        assertEquals(36, keys.first().midi)
        assertEquals(96, keys.last().midi)
        val pick = selectableMidi()
        assertEquals(45, pick.first())
        assertEquals(89, pick.last())
        assertTrue(pick.all { isWhiteKey(it) })
        assertFalse(hearable(36))
        assertTrue(hearable(60))
    }

    @Test
    fun acKey02_fiveTaughtKeysHaveFixedColoursAndMiddleCIsNamed() {
        assertEquals("#E5484D", keyColorHex(60))
        assertEquals("#F2731E", keyColorHex(62))
        assertEquals("#F1B92B", keyColorHex(64))
        assertEquals("#2FA66A", keyColorHex(65))
        assertEquals("#3B82E0", keyColorHex(67))
        assertEquals("C4 (middle C)", displayNoteName(60))
        assertEquals("D4", displayNoteName(62))
    }

    @Test
    fun acKey03_distanceSentenceCountsWhiteKeys() {
        assertEquals("C4 is two keys to the left ←", distanceSentence(64, 60))
        assertEquals("E4 is two keys to the right →", distanceSentence(60, 64))
        assertEquals(null, distanceSentence(61, 60))
    }

    @Test
    fun acKey04_twoLessonSetsAreC4ClusterAndC3Cluster() {
        val sets = lessonSets()
        assertEquals(2, sets.size)
        assertEquals(listOf(60, 62, 64, 65, 67), sets[0].second)
        assertEquals(listOf(48, 50, 52, 53, 55), sets[1].second)
    }

    @Test
    fun acKey05_zoomAlwaysContainsTheHighlightedWhiteKey() {
        listOf(45, 48, 60, 69, 89).forEach { midi ->
            val (from, to) = zoomWindow(midi)
            assertTrue(midi in from..to, "zoom $from..$to missed $midi")
        }
        assertEquals(listOf(36, 48, 60, 72, 84, 96), cOctaveMarkers())
    }
}
