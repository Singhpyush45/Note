package com.appname.notes.navigation

import androidx.navigation3.runtime.NavKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TopLevelNavigationTest {

    /** Records how many edits were made, to prove we don't rebuild the stack needlessly. */
    private class CountingList(initial: List<NavKey>) : AbstractMutableList<NavKey>() {
        private val items = initial.toMutableList()
        var edits = 0
        override val size: Int get() = items.size
        override fun get(index: Int) = items[index]
        override fun add(index: Int, element: NavKey) { edits++; items.add(index, element) }
        override fun removeAt(index: Int): NavKey { edits++; return items.removeAt(index) }
        override fun set(index: Int, element: NavKey): NavKey { edits++; return items.set(index, element) }
    }

    @Test
    fun `home produces a single root entry`() {
        assertThat(topLevelStackFor(TopLevelDestination.HOME)).containsExactly(HomeKey)
    }

    @Test
    fun `other tabs sit on top of home so back returns home`() {
        assertThat(topLevelStackFor(TopLevelDestination.SETTINGS)).containsExactly(HomeKey, SettingsKey).inOrder()
    }

    @Test
    fun `selecting settings from home pushes settings`() {
        val stack = mutableListOf<NavKey>(HomeKey)
        stack.navigateToTopLevel(TopLevelDestination.SETTINGS)
        assertThat(stack).containsExactly(HomeKey, SettingsKey).inOrder()
    }

    @Test
    fun `selecting home from settings pops back to root`() {
        val stack = mutableListOf<NavKey>(HomeKey, SettingsKey)
        stack.navigateToTopLevel(TopLevelDestination.HOME)
        assertThat(stack).containsExactly(HomeKey)
    }

    @Test
    fun `reselecting the current tab makes no edits`() {
        val stack = CountingList(listOf(HomeKey, SettingsKey))
        stack.navigateToTopLevel(TopLevelDestination.SETTINGS)
        assertThat(stack.edits).isEqualTo(0)
        assertThat(stack).containsExactly(HomeKey, SettingsKey).inOrder()
    }

    @Test
    fun `switching tabs keeps the shared root untouched`() {
        val stack = CountingList(listOf(HomeKey, SettingsKey))
        stack.navigateToTopLevel(TopLevelDestination.HOME)
        assertThat(stack.edits).isEqualTo(1) // only Settings removed
    }

    @Test
    fun `current tab is found from the top of the stack`() {
        assertThat(listOf<NavKey>(HomeKey).currentTopLevel()).isEqualTo(TopLevelDestination.HOME)
        assertThat(listOf(HomeKey, SettingsKey).currentTopLevel()).isEqualTo(TopLevelDestination.SETTINGS)
        assertThat(emptyList<NavKey>().currentTopLevel()).isEqualTo(TopLevelDestination.HOME)
    }

    @Test
    fun `nested screens above a tab still highlight that tab`() {
        val detail = object : NavKey {}
        assertThat(listOf(HomeKey, SettingsKey, detail).currentTopLevel()).isEqualTo(TopLevelDestination.SETTINGS)
    }

    @Test
    fun `switching tabs from a nested screen drops the nested screen`() {
        val detail = object : NavKey {}
        val stack = mutableListOf(HomeKey, detail)
        stack.navigateToTopLevel(TopLevelDestination.SETTINGS)
        assertThat(stack).containsExactly(HomeKey, SettingsKey).inOrder()
    }
}
