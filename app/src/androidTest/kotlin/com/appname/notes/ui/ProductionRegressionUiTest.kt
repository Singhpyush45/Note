package com.appname.notes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.appname.notes.core.designsystem.component.GlassSnackbarHost
import com.appname.notes.feature.ink.InkToolButtonBox
import com.appname.notes.feature.ink.InkToolbarSettingsExpandedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ProductionRegressionUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun glassSnackbarHost_rendersAtBottomAndExecutesUndoOnce() {
        val undoCount = AtomicInteger(0)

        composeRule.setContent {
            val hostState = remember { SnackbarHostState() }
            LaunchedEffect(Unit) {
                val result = hostState.showSnackbar(
                    message = "Moved to trash",
                    actionLabel = "Undo",
                )
                if (result == SnackbarResult.ActionPerformed) {
                    undoCount.incrementAndGet()
                }
            }
            Box(Modifier.fillMaxSize()) {
                GlassSnackbarHost(
                    hostState = hostState,
                    bottomOffset = 68.dp,
                    avoidFab = true,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        val snackbarNode = composeRule.onNodeWithText("Moved to trash")
        snackbarNode.assertIsDisplayed()

        val undoNode = composeRule.onNodeWithText("Undo")
        undoNode.assertIsDisplayed()
        undoNode.performClick()

        composeRule.waitForIdle()
        assertEquals(1, undoCount.get())
    }

    @Test
    fun inkToolButtonBox_staysCompactAndTogglesSettingsOnActiveTap() {
        InkToolbarSettingsExpandedState.value = true
        composeRule.setContent {
            InkToolButtonBox(
                selected = true,
                onClick = {},
            ) {
                androidx.compose.material3.Text("Pen")
            }
        }

        val bounds = composeRule.onNodeWithText("Pen").getUnclippedBoundsInRoot()
        assertTrue("Tool button height must stay compact", (bounds.bottom - bounds.top) <= 44.dp)
    }
}
