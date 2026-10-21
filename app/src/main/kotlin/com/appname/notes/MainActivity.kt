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
                val sharedPdf by viewModel.sharedPdf.collectAsStateWithLifecycle()
                val context = LocalContext.current
                LaunchedEffect(sharedPdf) {
                    when (val s = sharedPdf) {
                        SharedPdf.Importing -> Toast.makeText(context, R.string.pdf_importing, Toast.LENGTH_SHORT).show()
                        is SharedPdf.Failed -> {
                            Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
                            viewModel.sharedPdfHandled()
                        }
                        is SharedPdf.Opened, null -> Unit
                    }
                }
                NotesApp(
                    Modifier.semantics { testTagsAsResourceId = true },
                    openNoteRequest = (sharedPdf as? SharedPdf.Opened)?.noteId,
                    onOpenNoteRequestHandled = viewModel::sharedPdfHandled,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /** "Open with" (VIEW) or "Share" (SEND) of a PDF from another app: import it as a new note (SRS 7.1). */
    private fun handleIncoming(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        // Only content the sender granted us; file:// paths can't be read on current Android anyway.
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return
        viewModel.importShared(uri)
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
