package com.appname.notes.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.appname.notes.feature.home.HomeScreen
import com.appname.notes.feature.settings.SettingsRoute

/**
 * App shell: adaptive navigation (bottom bar on phones, rail on tablets — chosen automatically from the
 * window size) around the screen [NavDisplay] shows for the top of the back stack.
 */
@Composable
fun NotesApp(modifier: Modifier = Modifier) {
    // Saved across rotation and process death because every key is @Serializable.
    val backStack = rememberNavBackStack(HomeKey)
    val currentTopLevel = backStack.currentTopLevel()

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = destination == currentTopLevel
                item(
                    selected = selected,
                    onClick = { backStack.navigateToTopLevel(destination) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null, // the label below already names it
                        )
                    },
                    label = { Text(stringResource(destination.label)) },
                    modifier = Modifier.testTag(destination.testTag),
                )
            }
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(), // keeps scroll/text state per screen
                rememberViewModelStoreNavEntryDecorator(), // each screen gets its own ViewModels
            ),
            entryProvider = entryProvider {
                entry<HomeKey> { HomeScreen() }
                entry<SettingsKey> { SettingsRoute() }
            },
        )
    }
}
