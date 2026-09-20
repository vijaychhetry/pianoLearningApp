package com.vijaychhetry.kidspiano.core.learning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CourseAccessTest {
    @Test
    fun acCourse01_productionFlagKeepsSequentialUnlockOff() {
        assertFalse(
            REQUIRE_SEQUENTIAL_UNLOCK,
            "flip this flag only after the app is complete; review needs every course open",
        )
        val menu = courseMenu(currentId = "c4g4", completedIds = emptySet())
        assertTrue(menu.all { it.enabled }, "flag-off must not lock any course: $menu")
        assertEquals(listOf("C4–G4 (first)", "C4–G4 mix", "C3–G3 (lower)"), menu.map { it.title })
        assertEquals("C D E F G in order, middle of the piano", menu[0].subtitle)
        assertTrue(menu[0].selected)
        assertFalse(menu[1].selected)
        assertTrue(menu.all { it.lockedReason == null })
    }

    @Test
    fun acCourse02_sequentialUnlockLocksLaterCoursesUntilThePreviousIsDone() {
        val locked = courseMenu(
            currentId = "c4g4",
            completedIds = emptySet(),
            requireSequential = true,
        )
        assertTrue(locked[0].enabled)
        assertFalse(locked[1].enabled)
        assertFalse(locked[2].enabled)
        assertEquals("Finish C4–G4 first", locked[1].lockedReason)
        assertNull(locked[0].lockedReason)

        val afterFirst = courseMenu(
            currentId = "c4g4-mix",
            completedIds = setOf("c4g4"),
            requireSequential = true,
        )
        assertTrue(afterFirst[0].enabled)
        assertTrue(afterFirst[1].enabled)
        assertFalse(afterFirst[2].enabled)
        assertTrue(afterFirst[1].selected)
        assertEquals("Finish Mix first", afterFirst[2].lockedReason)
    }

    @Test
    fun acCourse03_flagOffIgnoresMissingCompletionsSoReviewCanOpenAnyCourse() {
        assertTrue(courseIsOpen("c3g3", completedIds = emptySet(), requireSequential = false))
        assertFalse(courseIsOpen("c3g3", completedIds = emptySet(), requireSequential = true))
        assertTrue(courseIsOpen("c3g3", completedIds = setOf("c4g4", "c4g4-mix"), requireSequential = true))
    }
}
