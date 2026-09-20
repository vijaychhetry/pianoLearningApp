package com.vijaychhetry.kidspiano.core.learning

import com.vijaychhetry.kidspiano.core.notes.lessonSets
import com.vijaychhetry.kidspiano.core.notes.nextLessonSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The phone bug: after five keys, "Done" was a label (it replaced 0/5) and
 * there was no Next. These tests fail if that comes back.
 */
class PracticeChromeTest {
    @Test
    fun acLearn15_inProgressShowsACounterNotADoneButtonAndHidesNext() {
        val chrome = inProgress(nextLabel = "C4–G4 mix")
        assertEquals("C4–G4  0/5", chrome.counterText)
        assertFalse(chrome.doneIsButton, "Done must not look tappable while keys remain")
        assertNull(chrome.nextButtonLabel, "Next mid-lesson would skip the rest of the keys")
        assertNull(chrome.nextHint)
        assertFalse(chrome.playAgain)
        assertEquals(PracticeFooter.START, chrome.footer)
        assertEquals("C", chrome.hero.letter)
        assertEquals(Copy.MIDDLE_C, chrome.hero.subtitle)
        assertNotEquals(Copy.DONE, chrome.counterText)
    }

    @Test
    fun acLearn15b_runningInProgressShowsPauseNotCompleteActions() {
        val chrome = inProgress(running = true, completed = 2, nextLabel = "C4–G4 mix")
        assertEquals("C4–G4  2/5", chrome.counterText)
        assertFalse(chrome.doneIsButton)
        assertNull(chrome.nextButtonLabel)
        assertEquals(PracticeFooter.PAUSE, chrome.footer)
    }

    @Test
    fun acLearn16_completeMakesDoneAButtonAndOffersTheNextCourse() {
        val chrome = complete(nextLabel = "C4–G4 mix")
        assertNull(chrome.counterText, "the 0/5 slot must not keep showing a fake Done label")
        assertTrue(chrome.doneIsButton)
        assertEquals(Copy.NEXT, chrome.nextButtonLabel)
        assertEquals("Next: C4–G4 mix", chrome.nextHint)
        assertTrue(chrome.playAgain)
        assertEquals(PracticeFooter.COMPLETE, chrome.footer)
        assertEquals("★", chrome.hero.letter)
        assertEquals(Copy.YOU_DID_IT, chrome.hero.subtitle)
        assertNotEquals(
            chrome.nextButtonLabel,
            chrome.hero.subtitle,
            "Next is a course action, not the You did it line",
        )
    }

    @Test
    fun acLearn17_lastCourseStillHasDoneButMustNotInventANextCourse() {
        val chrome = complete(nextLabel = null)
        assertTrue(chrome.doneIsButton)
        assertNull(chrome.nextButtonLabel)
        assertEquals(Copy.ALL_COURSES, chrome.nextHint)
        assertTrue(chrome.playAgain)
        assertEquals(PracticeFooter.COMPLETE, chrome.footer)
    }

    @Test
    fun acLearn18_coursesWalkC4ThenMixThenC3AndThenStop() {
        assertEquals(listOf("c4g4", "c4g4-mix", "c3g3"), lessonSets().map { it.id })
        val seen = mutableListOf<String>()
        var id: String? = "c4g4"
        repeat(10) {
            val current = id ?: return@repeat
            assertFalse(current in seen, "nextLessonSet looped back to $current")
            seen += current
            id = nextLessonSet(current)?.id
        }
        assertEquals(listOf("c4g4", "c4g4-mix", "c3g3"), seen)
        assertNull(id)
        assertEquals("C4–G4 mix", nextCourseLabel("c4g4"))
        assertEquals("C3–G3 (lower)", nextCourseLabel("c4g4-mix"))
        assertNull(nextCourseLabel("c3g3"))
    }
}

class HomeChromeTest {
    @Test
    fun acHome01_practiceStaysOffAndHidesTheCourseUntilCalibrated() {
        val chrome = homeChrome(
            pianoReady = false,
            courseLabel = "C4–G4 (first)",
            setupSummary = "Saved profile: GOOD · 5 notes",
        )
        assertFalse(chrome.practiceEnabled)
        assertNull(chrome.courseLabel, "a course name on a locked Practice is a lie")
        assertEquals(Copy.NOT_CALIBRATED, chrome.statusLine)
    }

    @Test
    fun acHome02_readyEnablesPracticeAndNamesTheCourse() {
        val chrome = homeChrome(
            pianoReady = true,
            courseLabel = "C4–G4 mix",
            setupSummary = "Saved profile: GOOD · 5 notes · UNPROCESSED",
        )
        assertTrue(chrome.practiceEnabled)
        assertEquals("C4–G4 mix", chrome.courseLabel)
        assertEquals("Saved profile: GOOD · 5 notes · UNPROCESSED", chrome.statusLine)
        assertNotNull(chrome.courseLabel)
        assertNotEquals(Copy.NOT_CALIBRATED, chrome.statusLine)
    }

    @Test
    fun acHome03_readyWithNoSummaryStillSaysThePianoIsReady() {
        val chrome = homeChrome(true, "C3–G3 (lower)", null)
        assertTrue(chrome.practiceEnabled)
        assertEquals(Copy.READY, chrome.statusLine)
        assertEquals("C3–G3 (lower)", chrome.courseLabel)
    }
}

private fun inProgress(
    running: Boolean = false,
    completed: Int = 0,
    nextLabel: String? = "C4–G4 mix",
): PracticeChrome = practiceChrome(
    complete = false,
    running = running,
    shortLabel = "C4–G4",
    completedCount = completed,
    total = 5,
    nextLabel = nextLabel,
    expectedMidi = 60,
    letter = "C",
    noteName = "C4",
)

private fun complete(nextLabel: String?): PracticeChrome = practiceChrome(
    complete = true,
    running = false,
    shortLabel = "C4–G4",
    completedCount = 5,
    total = 5,
    nextLabel = nextLabel,
    expectedMidi = null,
    letter = "C",
    noteName = "Done",
)
