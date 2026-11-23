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
    fun `top level destinations follow Notes Calendar Todo order`() {
        assertThat(TopLevelDestination.entries).containsExactly(
            TopLevelDestination.HOME,
            TopLevelDestination.CALENDAR,
            TopLevelDestination.TODO,
        ).inOrder()
    }

    @Test
    fun `home produces a single root entry`() {
        assertThat(topLevelStackFor(TopLevelDestination.HOME)).containsExactly(HomeKey)
    }

    @Test
    fun `other tabs sit on top of home so back returns home`() {
        assertThat(topLevelStackFor(TopLevelDestination.CALENDAR)).containsExactly(HomeKey, CalendarKey).inOrder()
        assertThat(topLevelStackFor(TopLevelDestination.TODO)).containsExactly(HomeKey, TasksKey).inOrder()
    }

    @Test
    fun `selecting calendar or settings from home pushes the tab`() {
        val stack = mutableListOf<NavKey>(HomeKey)
        stack.navigateToTopLevel(TopLevelDestination.CALENDAR)
        assertThat(stack).containsExactly(HomeKey, CalendarKey).inOrder()

        stack.navigateToTopLevel(TopLevelDestination.TODO)
        assertThat(stack).containsExactly(HomeKey, TasksKey).inOrder()
    }

    @Test
    fun `selecting home from settings pops back to root`() {
        val stack = mutableListOf<NavKey>(HomeKey, TasksKey)
        stack.navigateToTopLevel(TopLevelDestination.HOME)
        assertThat(stack).containsExactly(HomeKey)
    }

    @Test
    fun `reselecting the current tab makes no edits`() {
        val stack = CountingList(listOf(HomeKey, TasksKey))
        stack.navigateToTopLevel(TopLevelDestination.TODO)
        assertThat(stack.edits).isEqualTo(0)
        assertThat(stack).containsExactly(HomeKey, TasksKey).inOrder()
    }

    @Test
    fun `switching tabs keeps the shared root untouched`() {
        val stack = CountingList(listOf(HomeKey, TasksKey))
        stack.navigateToTopLevel(TopLevelDestination.HOME)
        assertThat(stack.edits).isEqualTo(1) // only Settings removed
    }

    @Test
    fun `current tab is found from the top of the stack`() {
        assertThat(listOf<NavKey>(HomeKey).currentTopLevel()).isEqualTo(TopLevelDestination.HOME)
        assertThat(listOf(HomeKey, CalendarKey).currentTopLevel()).isEqualTo(TopLevelDestination.CALENDAR)
        assertThat(listOf(HomeKey, TasksKey).currentTopLevel()).isEqualTo(TopLevelDestination.TODO)
        assertThat(emptyList<NavKey>().currentTopLevel()).isEqualTo(TopLevelDestination.HOME)
    }

    @Test
    fun `nested screens above a tab still highlight that tab`() {
        val detail = object : NavKey {}
        assertThat(listOf(HomeKey, TasksKey, detail).currentTopLevel()).isEqualTo(TopLevelDestination.TODO)
    }

    @Test
    fun `switching tabs from a nested screen drops the nested screen`() {
        val detail = object : NavKey {}
        val stack = mutableListOf(HomeKey, detail)
        stack.navigateToTopLevel(TopLevelDestination.TODO)
        assertThat(stack).containsExactly(HomeKey, TasksKey).inOrder()
    }

    @Test
    fun `opening a note replaces an editor already on top`() {
        val stack = mutableListOf<NavKey>(HomeKey)
        stack.openNote("a")
        stack.openNote("b")
        assertThat(stack).containsExactly(HomeKey, NoteEditorKey("b")).inOrder()
    }

    @Test
    fun `opening a note from search keeps search underneath`() {
        val stack = mutableListOf<NavKey>(HomeKey, SearchKey)
        stack.openNote("a")
        assertThat(stack).containsExactly(HomeKey, SearchKey, NoteEditorKey("a")).inOrder()
        assertThat(stack.currentTopLevel()).isEqualTo(TopLevelDestination.HOME)
    }

    @Test
    fun `pop never removes the root`() {
        val stack = mutableListOf<NavKey>(HomeKey, SearchKey)
        assertThat(stack.pop()).isTrue()
        assertThat(stack.pop()).isFalse()
        assertThat(stack).containsExactly(HomeKey)
    }

    @Test
    fun `closing a note removes only that editor`() {
        val stack = mutableListOf<NavKey>(HomeKey, NoteEditorKey("a"))
        stack.closeNote("a")
        assertThat(stack).containsExactly(HomeKey)
        stack.closeNote("zzz")
        assertThat(stack).containsExactly(HomeKey)
    }

    @Test
    fun `push does not duplicate the screen on top`() {
        val stack = mutableListOf<NavKey>(HomeKey)
        stack.push(NotebooksKey)
        stack.push(NotebooksKey)
        assertThat(stack).containsExactly(HomeKey, NotebooksKey).inOrder()
    }
}
