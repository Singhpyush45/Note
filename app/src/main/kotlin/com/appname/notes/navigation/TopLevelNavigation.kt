package com.appname.notes.navigation

import androidx.navigation3.runtime.NavKey

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
    asReversed().firstNotNullOfOrNull { key -> TopLevelDestination.entries.firstOrNull { it.key == key } }
        ?: TopLevelDestination.HOME

/**
 * Switches tabs with the fewest list edits: keeps the shared prefix, replaces the rest.
 * Reselecting the current tab when already at its root does nothing.
 */
fun MutableList<NavKey>.navigateToTopLevel(destination: TopLevelDestination) {
    val target = topLevelStackFor(destination)
    if (this == target) return
    var common = 0
    while (common < size && common < target.size && this[common] == target[common]) common++
    while (size > common) removeAt(lastIndex)
    addAll(target.subList(common, target.size))
}
