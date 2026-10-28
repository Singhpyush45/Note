package com.appname.notes.navigation

import androidx.navigation3.runtime.NavKey
import com.appname.notes.core.domain.model.NoteType

/*
 * Pure back-stack rules (no Android needed, fully unit-tested).
 *
 * Model: Home is always the root. Opening another top-level tab gives [Home, Tab], so Back from any tab
 * returns to Home, and Back from Home leaves the app — the behaviour Android users expect.
 */

/** The back stack that selecting [destination] should produce. */
fun topLevelStackFor(destination: TopLevelDestination): List<NavKey> =
    if (destination == TopLevelDestination.HOME) {
        listOf(HomeKey)
    } else {
        listOf(HomeKey, destination.key)
    }

/** Which tab to highlight: the nearest top-level key from the top of the stack. */
fun List<NavKey>.currentTopLevel(): TopLevelDestination =
    asReversed().firstNotNullOfOrNull { key ->
        if (key == TasksKey) TopLevelDestination.CALENDAR
        else TopLevelDestination.entries.firstOrNull { it.key == key }
    }
        ?: TopLevelDestination.HOME

/**
 * Switches tabs with the fewest list edits: keeps the shared prefix, replaces the rest.
 * Reselecting the current tab when already at its root does nothing.
 */
fun MutableList<NavKey>.navigateToTopLevel(destination: TopLevelDestination) {
    val target = topLevelStackFor(destination)
    if (this == target) return
    replaceWith(target)
}

/**
 * Opens a note in the editor for its type. Any editor already on top is replaced rather than stacked, so
 * on a tablet tapping another note swaps the detail pane, and Back still returns to the list in one step.
 */
fun MutableList<NavKey>.openNote(noteId: String, type: NoteType = NoteType.TEXT, page: Int? = null) {
    while (lastOrNull().isEditor()) removeAt(lastIndex)
    add(
        when (type) {
            NoteType.TEXT -> NoteEditorKey(noteId)
            NoteType.INK, NoteType.PDF -> InkEditorKey(noteId, page) // a PDF is ink pages over the document's pages
        },
    )
}

private fun NavKey?.isEditor() = this is NoteEditorKey || this is InkEditorKey

/** Opens a non-top-level screen on top, unless it is already showing. */
fun MutableList<NavKey>.push(key: NavKey) {
    if (lastOrNull() != key) add(key)
}

/** Back: pops one entry; at the root it does nothing (the system then leaves the app). */
fun MutableList<NavKey>.pop(): Boolean {
    if (size <= 1) return false
    removeAt(lastIndex)
    return true
}

/** Removes the editor of [noteId] (e.g. after the note was trashed from inside the editor). */
fun MutableList<NavKey>.closeNote(noteId: String) {
    val index = indexOfLast { it == NoteEditorKey(noteId) || (it as? InkEditorKey)?.noteId == noteId }
    if (index > 0) removeAt(index)
}

private fun MutableList<NavKey>.replaceWith(target: List<NavKey>) {
    var common = 0
    while (common < size && common < target.size && this[common] == target[common]) common++
    while (size > common) removeAt(lastIndex)
    addAll(target.subList(common, target.size))
}
