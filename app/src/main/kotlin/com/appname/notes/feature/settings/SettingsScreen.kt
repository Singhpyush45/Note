package com.appname.notes.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.BuildConfig
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.SettingsActionRow
import com.appname.notes.core.designsystem.component.SettingsInfoRow
import com.appname.notes.core.designsystem.component.SettingsRadioGroup
import com.appname.notes.core.designsystem.component.SettingsRadioRow
import com.appname.notes.core.designsystem.component.SettingsSectionHeader
import com.appname.notes.core.designsystem.component.SettingsSwitchRow
import com.appname.notes.core.designsystem.theme.AppTheme
import com.appname.notes.core.designsystem.theme.supportsDynamicColor
import com.appname.notes.core.settings.AppearanceSettings
import com.appname.notes.core.settings.FingerMode
import com.appname.notes.core.settings.HoldDuration
import com.appname.notes.core.settings.InkSettings
import com.appname.notes.core.settings.ThemeMode

/** Connects the screen to its ViewModel. Kept separate so [SettingsScreen] is easy to preview and test. */
@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onDynamicColorChanged = viewModel::onDynamicColorChanged,
        onSaveErrorShown = viewModel::onSaveErrorShown,
        onRebuildSearchIndex = viewModel::onRebuildSearchIndex,
        onIndexRebuiltShown = viewModel::onIndexRebuiltShown,
        onShapeCorrectionChanged = viewModel::onShapeCorrectionChanged,
        onHoldDurationSelected = viewModel::onHoldDurationSelected,
        onFingerModeSelected = viewModel::onFingerModeSelected,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onSaveErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
    onRebuildSearchIndex: () -> Unit = {},
    onIndexRebuiltShown: () -> Unit = {},
    onShapeCorrectionChanged: (Boolean) -> Unit = {},
    onHoldDurationSelected: (HoldDuration) -> Unit = {},
    onFingerModeSelected: (FingerMode) -> Unit = {},
    dynamicColorSupported: Boolean = supportsDynamicColor(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMessage = stringResource(R.string.settings_save_failed)
    val rebuiltMessage = stringResource(R.string.settings_rebuild_index_done)
    val showSaveError = (uiState as? SettingsUiState.Ready)?.showSaveError == true
    val showRebuilt = (uiState as? SettingsUiState.Ready)?.showIndexRebuilt == true
    LaunchedEffect(showSaveError) {
        if (showSaveError) {
            snackbarHostState.showSnackbar(saveErrorMessage)
            onSaveErrorShown()
        }
    }
    LaunchedEffect(showRebuilt) {
        if (showRebuilt) {
            snackbarHostState.showSnackbar(rebuiltMessage)
            onIndexRebuiltShown()
        }
    }

    Scaffold(
        modifier = modifier.testTag(SettingsTestTags.SCREEN),
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (uiState) {
            // DataStore answers within milliseconds; an empty frame avoids a flickering spinner.
            SettingsUiState.Loading -> Box(Modifier.fillMaxSize().padding(innerPadding))
            is SettingsUiState.Ready -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                SettingsList(
                    appearance = uiState.appearance,
                    ink = uiState.ink,
                    dynamicColorSupported = dynamicColorSupported,
                    onThemeModeSelected = onThemeModeSelected,
                    onDynamicColorChanged = onDynamicColorChanged,
                    onRebuildSearchIndex = onRebuildSearchIndex,
                    onShapeCorrectionChanged = onShapeCorrectionChanged,
                    onHoldDurationSelected = onHoldDurationSelected,
                    onFingerModeSelected = onFingerModeSelected,
                )
            }
        }
    }
}

@Composable
private fun SettingsList(
    appearance: AppearanceSettings,
    ink: InkSettings,
    dynamicColorSupported: Boolean,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onRebuildSearchIndex: () -> Unit,
    onShapeCorrectionChanged: (Boolean) -> Unit,
    onHoldDurationSelected: (HoldDuration) -> Unit,
    onFingerModeSelected: (FingerMode) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .widthIn(max = AppTheme.spacing.maxContentWidth)
            .fillMaxWidth()
            .testTag(SettingsTestTags.LIST),
    ) {
        item { SettingsSectionHeader(stringResource(R.string.settings_section_appearance)) }
        item {
            SettingsRadioGroup {
                ThemeMode.entries.forEach { mode ->
                    SettingsRadioRow(
                        title = stringResource(mode.labelRes()),
                        summary = if (mode == ThemeMode.SYSTEM) {
                            stringResource(R.string.settings_theme_system_summary)
                        } else {
                            null
                        },
                        selected = appearance.themeMode == mode,
                        onClick = { onThemeModeSelected(mode) },
                    )
                }
            }
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                summary = stringResource(
                    if (dynamicColorSupported) {
                        R.string.settings_dynamic_color_summary
                    } else {
                        R.string.settings_dynamic_color_unsupported
                    },
                ),
                checked = appearance.dynamicColor && dynamicColorSupported,
                enabled = dynamicColorSupported,
                onCheckedChange = onDynamicColorChanged,
            )
        }

        item { HorizontalDivider() }
        item { SettingsSectionHeader(stringResource(R.string.settings_section_handwriting)) }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_shape_correction),
                summary = stringResource(R.string.settings_shape_correction_summary),
                checked = ink.shapeCorrection,
                onCheckedChange = onShapeCorrectionChanged,
            )
        }
        if (ink.shapeCorrection) {
            item { SettingsSectionHeader(stringResource(R.string.settings_hold_time)) }
            item {
                SettingsRadioGroup {
                    HoldDuration.entries.forEach { d ->
                        SettingsRadioRow(
                            title = stringResource(d.labelRes()),
                            selected = ink.holdDuration == d,
                            onClick = { onHoldDurationSelected(d) },
                        )
                    }
                }
            }
        }
        item { SettingsSectionHeader(stringResource(R.string.settings_finger)) }
        item {
            SettingsRadioGroup {
                FingerMode.entries.forEach { mode ->
                    SettingsRadioRow(
                        title = stringResource(mode.labelRes()),
                        summary = if (mode == FingerMode.AUTO) stringResource(R.string.settings_finger_auto_summary) else null,
                        selected = ink.fingerMode == mode,
                        onClick = { onFingerModeSelected(mode) },
                    )
                }
            }
        }

        item { HorizontalDivider() }
        item { SettingsSectionHeader(stringResource(R.string.settings_section_privacy)) }
        item {
            SettingsInfoRow(
                title = stringResource(R.string.settings_storage),
                value = stringResource(R.string.settings_storage_value),
            )
        }

        item { HorizontalDivider() }
        item { SettingsSectionHeader(stringResource(R.string.settings_section_maintenance)) }
        item {
            SettingsActionRow(
                title = stringResource(R.string.settings_rebuild_index),
                summary = stringResource(R.string.settings_rebuild_index_summary),
                onClick = onRebuildSearchIndex,
            )
        }

        item { HorizontalDivider() }
        item { SettingsSectionHeader(stringResource(R.string.settings_section_about)) }
        item {
            SettingsInfoRow(title = stringResource(R.string.settings_version), value = BuildConfig.VERSION_NAME)
        }
    }
}

private fun HoldDuration.labelRes(): Int = when (this) {
    HoldDuration.SHORT -> R.string.settings_hold_short
    HoldDuration.NORMAL -> R.string.settings_hold_normal
    HoldDuration.LONG -> R.string.settings_hold_long
}

private fun FingerMode.labelRes(): Int = when (this) {
    FingerMode.AUTO -> R.string.settings_finger_auto
    FingerMode.DRAW -> R.string.settings_finger_draw
    FingerMode.SCROLL -> R.string.settings_finger_scroll
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

object SettingsTestTags {
    const val SCREEN = "screen_settings"
    const val LIST = "settings_list"
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    AppTheme {
        SettingsScreen(
            uiState = SettingsUiState.Ready(AppearanceSettings(themeMode = ThemeMode.DARK)),
            onThemeModeSelected = {},
            onDynamicColorChanged = {},
            onSaveErrorShown = {},
            dynamicColorSupported = true,
        )
    }
}
