package com.vijaychhetry.kidspiano.core.learning

import com.vijaychhetry.kidspiano.core.notes.LessonSet
import com.vijaychhetry.kidspiano.core.notes.lessonSets

/**
 * Sequential unlock for the course menu.
 *
 * Keep this **false** until the app is complete so every course stays
 * open for review. Flip to true to require finishing the previous
 * course before the next one can be opened.
 */
const val REQUIRE_SEQUENTIAL_UNLOCK = false

data class CourseMenuItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val selected: Boolean,
    val lockedReason: String?,
)

fun courseIsOpen(
    id: String,
    completedIds: Set<String>,
    requireSequential: Boolean = REQUIRE_SEQUENTIAL_UNLOCK,
    sets: List<LessonSet> = lessonSets(),
): Boolean {
    if (!requireSequential) return true
    val index = sets.indexOfFirst { it.id == id }
    if (index <= 0) return true
    return sets[index - 1].id in completedIds
}

fun courseMenu(
    currentId: String,
    completedIds: Set<String>,
    requireSequential: Boolean = REQUIRE_SEQUENTIAL_UNLOCK,
    sets: List<LessonSet> = lessonSets(),
): List<CourseMenuItem> = sets.map { set ->
    val open = courseIsOpen(set.id, completedIds, requireSequential, sets)
    val previous = sets.getOrNull(sets.indexOfFirst { it.id == set.id } - 1)
    CourseMenuItem(
        id = set.id,
        title = set.label,
        subtitle = set.subtitle,
        enabled = open,
        selected = set.id == currentId,
        lockedReason = if (open) null else "Finish ${previous?.shortLabel ?: "the previous course"} first",
    )
}
