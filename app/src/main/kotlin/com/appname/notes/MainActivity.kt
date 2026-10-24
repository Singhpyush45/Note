package com.appname.notes

import android.content.ContentResolver
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.core.designsystem.theme.AppTheme
import com.appname.notes.core.settings.ThemeMode
import com.appname.notes.navigation.NotesApp
import dagger.hilt.android.AndroidEntryPoint

@OptIn(ExperimentalComposeUiApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen() // must run before super.onCreate
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }
        // Only a fresh start: after rotation or process death the intent was already handled.
        if (savedInstanceState == null) handleIncoming(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = shouldUseDarkTheme(uiState)
            val dynamicColor = (uiState as? MainUiState.Ready)?.appearance?.dynamicColor == true

            // Draw behind system bars; flip status/nav bar icon colours with the app's theme,
            // which can differ from the system theme when the user forces Light or Dark.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose {}
            }

            AppTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                // Exposes test tags as resource ids so UiAutomator tests (which run in another process,
                // e.g. the process-death tests in :e2e) can find screens and fields.
                val shared by viewModel.shared.collectAsStateWithLifecycle()
                val context = LocalContext.current
                LaunchedEffect(shared) {
                    when (val s = shared) {
                        is SharedImport.Importing -> Toast.makeText(context, s.message, Toast.LENGTH_SHORT).show()
                        is SharedImport.Failed -> {
                            Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
                            viewModel.sharedHandled()
                        }
                        is SharedImport.Opened -> s.notice?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                        null -> Unit
                    }
                }
                val opened = shared as? SharedImport.Opened
                NotesApp(
                    Modifier.semantics { testTagsAsResourceId = true },
                    openNoteRequest = opened?.let { it.noteId to it.type },
                    onOpenNoteRequestHandled = viewModel::sharedHandled,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /**
     * Content from another app:
     * - "Open with" (VIEW) or "Share" (SEND) of a PDF → a new PDF note (SRS 7.1);
     * - "Share" of one or more photos (SEND / SEND_MULTIPLE) → a new note with them attached (FR-009).
     */
    private fun handleIncoming(intent: Intent?) {
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }.filter { it.scheme == ContentResolver.SCHEME_CONTENT } // only content the sender granted us
        if (uris.isEmpty()) return
        val type = intent?.type.orEmpty()
        if (type.startsWith("image/")) viewModel.importSharedImages(uris) else viewModel.importSharedPdf(uris.first())
    }

    private companion object {
        // Same scrims androidx.activity uses by default for 3-button navigation.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}

@Composable
private fun shouldUseDarkTheme(uiState: MainUiState): Boolean = when (uiState) {
    MainUiState.Loading -> isSystemInDarkTheme()
    is MainUiState.Ready -> when (uiState.appearance.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}
