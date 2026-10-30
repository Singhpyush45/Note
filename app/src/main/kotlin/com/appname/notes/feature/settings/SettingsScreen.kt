package com.appname.notes.feature.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import com.appname.notes.navigation.AppLaunchAnimationState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.BuildConfig
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.SettingsActionRow
import com.appname.notes.core.designsystem.component.SettingsCard
import com.appname.notes.core.designsystem.component.SettingsInfoRow
import com.appname.notes.core.designsystem.component.SettingsLinkRow
import com.appname.notes.core.designsystem.component.SettingsRadioGroup
import com.appname.notes.core.designsystem.component.SettingsRadioRow
import com.appname.notes.core.designsystem.component.SettingsSegmentedRow
import com.appname.notes.core.designsystem.component.SettingsSliderRow
import com.appname.notes.core.designsystem.component.SettingsSwitchRow
import com.appname.notes.core.designsystem.component.WallpaperSpec
import com.appname.notes.core.designsystem.component.WallpaperThumbnail
import com.appname.notes.core.designsystem.theme.Accent
import com.appname.notes.core.designsystem.theme.AppTheme
import com.appname.notes.core.designsystem.theme.CornerRadius
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import com.appname.notes.core.designsystem.theme.Palette
import com.appname.notes.core.designsystem.theme.UiDensity
import com.appname.notes.core.designsystem.theme.WallpaperPreset
import com.appname.notes.core.designsystem.theme.supportsBlur
import com.appname.notes.core.designsystem.theme.supportsDynamicColor
import com.appname.notes.core.settings.AppearanceSettings
import com.appname.notes.core.settings.NoteDefaults
import com.appname.notes.core.settings.FingerMode
import com.appname.notes.core.settings.HoldDuration
import com.appname.notes.core.settings.InkSettings
import com.appname.notes.core.settings.ThemeMode
import com.appname.notes.core.settings.WallpaperChoice

/** Appearance controls (owner brief §19); grouped so the screen's signature stays manageable. */
data class AppearanceActions(
    val onAccent: (Accent) -> Unit = {},
    val onCustomHue: (Float) -> Unit = {},
    val onGlass: (Int) -> Unit = {},
    val onBlur: (Int) -> Unit = {},
    val onCorner: (Int) -> Unit = {},
    val onDensity: (UiDensity) -> Unit = {},
    val onWallpaper: (WallpaperChoice) -> Unit = {},
    val onWallpaperPhoto: (Uri) -> Unit = {},
    val onReset: () -> Unit = {},
    val onPhotoFailedShown: () -> Unit = {},
)

/** Connects the screen to its ViewModel. Kept separate so [SettingsScreen] is easy to preview and test. */
@Composable
fun SettingsRoute(
    onBack: (() -> Unit)? = null,
    onManageTemplates: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val wallpaperPhoto by viewModel.wallpaperPhoto.collectAsStateWithLifecycle()
    BackHandler(enabled = onBack != null) { onBack?.invoke() }
    SettingsScreen(
        uiState = uiState,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onDynamicColorChanged = viewModel::onDynamicColorChanged,
        onSaveErrorShown = viewModel::onSaveErrorShown,
        onBack = onBack,
        onRebuildSearchIndex = viewModel::onRebuildSearchIndex,
        onIndexRebuiltShown = viewModel::onIndexRebuiltShown,
        onShapeCorrectionChanged = viewModel::onShapeCorrectionChanged,
        onHoldDurationSelected = viewModel::onHoldDurationSelected,
        onFingerModeSelected = viewModel::onFingerModeSelected,
        onDocumentTextChanged = viewModel::onDocumentTextChanged,
        appearanceActions = AppearanceActions(
            onAccent = viewModel::onAccentSelected,
            onCustomHue = viewModel::onCustomHueSelected,
            onGlass = viewModel::onGlassIntensityChanged,
            onBlur = viewModel::onBlurIntensityChanged,
            onCorner = viewModel::onCornerRadiusChanged,
            onDensity = viewModel::onDensitySelected,
            onWallpaper = viewModel::onWallpaperSelected,
            onWallpaperPhoto = viewModel::onWallpaperPhotoPicked,
            onReset = viewModel::onResetAppearance,
            onPhotoFailedShown = viewModel::onPhotoFailedShown,
        ),
        wallpaperPhoto = wallpaperPhoto,
        onManageTemplates = onManageTemplates,
        onNoteDefaults = viewModel::onNoteDefaults,
        onInk = viewModel::onInk,
        backupCard = { com.appname.notes.feature.backup.BackupCard() },
        securityCard = { com.appname.notes.feature.lock.SecurityCard() },
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
    onBack: (() -> Unit)? = null,
    onRebuildSearchIndex: () -> Unit = {},
    onIndexRebuiltShown: () -> Unit = {},
    onShapeCorrectionChanged: (Boolean) -> Unit = {},
    onHoldDurationSelected: (HoldDuration) -> Unit = {},
    onFingerModeSelected: (FingerMode) -> Unit = {},
    onDocumentTextChanged: (Boolean) -> Unit = {},
    appearanceActions: AppearanceActions = AppearanceActions(),
    wallpaperPhoto: ImageBitmap? = null,
    dynamicColorSupported: Boolean = supportsDynamicColor(),
    blurSupported: Boolean = supportsBlur(),
    onManageTemplates: () -> Unit = {},
    onNoteDefaults: ((NoteDefaults) -> NoteDefaults) -> Unit = {},
    onInk: ((InkSettings) -> InkSettings) -> Unit = {},
    backupCard: @Composable () -> Unit = {},
    securityCard: @Composable () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMessage = stringResource(R.string.settings_save_failed)
    val rebuiltMessage = stringResource(R.string.settings_rebuild_index_done)
    val photoFailedMessage = stringResource(R.string.settings_wallpaper_photo_failed)
    val ready = uiState as? SettingsUiState.Ready
    val showSaveError = ready?.showSaveError == true
    val showRebuilt = ready?.showIndexRebuilt == true
    val showPhotoFailed = ready?.showPhotoFailed == true
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
    LaunchedEffect(showPhotoFailed) {
        if (showPhotoFailed) {
            snackbarHostState.showSnackbar(photoFailedMessage)
            appearanceActions.onPhotoFailedShown()
        }
    }

    Scaffold(
        modifier = modifier.testTag(SettingsTestTags.SCREEN),
        // The wallpaper shows through; the cards are glass.
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag(SettingsTestTags.BACK)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (uiState) {
            // DataStore answers within milliseconds; an empty frame avoids a flickering spinner.
            SettingsUiState.Loading -> Box(Modifier.fillMaxSize().padding(innerPadding))
            is SettingsUiState.Ready -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                SettingsList(
                    appearance = uiState.appearance,
                    ink = uiState.ink,
                    documentText = uiState.documentText,
                    onDocumentTextChanged = onDocumentTextChanged,
                    dynamicColorSupported = dynamicColorSupported,
                    blurSupported = blurSupported,
                    onThemeModeSelected = onThemeModeSelected,
                    onDynamicColorChanged = onDynamicColorChanged,
                    onRebuildSearchIndex = onRebuildSearchIndex,
                    onShapeCorrectionChanged = onShapeCorrectionChanged,
                    onHoldDurationSelected = onHoldDurationSelected,
                    onFingerModeSelected = onFingerModeSelected,
                    actions = appearanceActions,
                    wallpaperPhoto = wallpaperPhoto,
                    onManageTemplates = onManageTemplates,
                    defaults = uiState.defaults,
                    onNoteDefaults = onNoteDefaults,
                    onInk = onInk,
                    backupCard = backupCard,
                    securityCard = securityCard,
                )
            }
        }
    }
}

/** Theme choices in the order the segmented control shows them. */
private val THEME_ORDER = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)

@Composable
private fun SettingsList(
    appearance: AppearanceSettings,
    ink: InkSettings,
    documentText: Boolean,
    onDocumentTextChanged: (Boolean) -> Unit,
    dynamicColorSupported: Boolean,
    blurSupported: Boolean,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onRebuildSearchIndex: () -> Unit,
    onShapeCorrectionChanged: (Boolean) -> Unit,
    onHoldDurationSelected: (HoldDuration) -> Unit,
    onFingerModeSelected: (FingerMode) -> Unit,
    actions: AppearanceActions,
    wallpaperPhoto: ImageBitmap?,
    onManageTemplates: () -> Unit,
    defaults: NoteDefaults,
    onNoteDefaults: ((NoteDefaults) -> NoteDefaults) -> Unit,
    onInk: ((InkSettings) -> InkSettings) -> Unit = {},
    backupCard: @Composable () -> Unit = {},
    securityCard: @Composable () -> Unit = {},
) {
    var pickingWallpaper by rememberSaveable { mutableStateOf(false) }
    var pickingHue by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier
            .widthIn(max = AppTheme.spacing.maxContentWidth)
            .fillMaxWidth()
            .testTag(SettingsTestTags.LIST),
    ) {
        item {
            SettingsCard(stringResource(R.string.settings_section_appearance), icon = Icons.Outlined.Palette) {
                SettingsSegmentedRow(
                    title = stringResource(R.string.settings_theme),
                    options = THEME_ORDER.map { stringResource(it.shortLabelRes()) },
                    selectedIndex = THEME_ORDER.indexOf(appearance.themeMode),
                    onSelect = { onThemeModeSelected(THEME_ORDER[it]) },
                    optionTag = { SettingsTestTags.theme(THEME_ORDER[it]) },
                )
                AccentRow(
                    appearance = appearance,
                    dimmed = appearance.dynamicColor && dynamicColorSupported,
                    onAccent = actions.onAccent,
                    onCustom = { pickingHue = true },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(
                        if (dynamicColorSupported) R.string.settings_dynamic_color_summary else R.string.settings_dynamic_color_unsupported,
                    ),
                    checked = appearance.dynamicColor && dynamicColorSupported,
                    enabled = dynamicColorSupported,
                    onCheckedChange = onDynamicColorChanged,
                )
                SettingsSliderRow(
                    title = stringResource(R.string.settings_glass),
                    value = appearance.glassIntensity.toFloat(),
                    valueRange = 0f..100f,
                    valueLabel = { "${it.toInt()}%" },
                    onValueCommitted = { actions.onGlass(it.toInt()) },
                    modifier = Modifier.testTag(SettingsTestTags.GLASS),
                )
                SettingsSliderRow(
                    title = stringResource(R.string.settings_blur),
                    value = appearance.blurIntensity.toFloat(),
                    valueRange = 0f..100f,
                    valueLabel = { "${it.toInt()}%" },
                    onValueCommitted = { actions.onBlur(it.toInt()) },
                    summary = if (blurSupported) null else stringResource(R.string.settings_blur_unsupported),
                    enabled = blurSupported,
                    modifier = Modifier.testTag(SettingsTestTags.BLUR),
                )
                SettingsSliderRow(
                    title = stringResource(R.string.settings_corner_radius),
                    value = appearance.cornerRadius.toFloat(),
                    valueRange = CornerRadius.MIN.value..CornerRadius.MAX.value,
                    valueLabel = { "${kotlin.math.round(it).toInt()} dp" },
                    onValueCommitted = { actions.onCorner(kotlin.math.round(it).toInt()) },
                    modifier = Modifier.testTag(SettingsTestTags.CORNER),
                )
                SettingsLinkRow(
                    title = stringResource(R.string.settings_wallpaper),
                    summary = stringResource(appearance.wallpaper.labelRes()),
                    onClick = { pickingWallpaper = true },
                    modifier = Modifier.testTag(SettingsTestTags.WALLPAPER),
                ) {
                    WallpaperThumbnail(
                        appearance.wallpaper.toSpec(wallpaperPhoto),
                        Modifier.width(72.dp).height(40.dp).clip(MaterialTheme.shapes.small)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
                    )
                }
                SettingsSegmentedRow(
                    title = stringResource(R.string.settings_density),
                    options = listOf(stringResource(R.string.settings_density_compact), stringResource(R.string.settings_density_comfortable)),
                    selectedIndex = if (appearance.density == UiDensity.COMPACT) 0 else 1,
                    onSelect = { actions.onDensity(if (it == 0) UiDensity.COMPACT else UiDensity.COMFORTABLE) },
                    optionTag = { if (it == 0) SettingsTestTags.DENSITY_COMPACT else SettingsTestTags.DENSITY_COMFORTABLE },
                )
                SettingsActionRow(
                    title = stringResource(R.string.settings_replay_launch_animation),
                    summary = stringResource(R.string.settings_replay_launch_animation_summary),
                    icon = Icons.Outlined.AutoAwesome,
                    onClick = { AppLaunchAnimationState.triggerReplay() },
                )
                SettingsActionRow(
                    title = stringResource(R.string.settings_reset_appearance),
                    summary = stringResource(R.string.settings_reset_appearance_summary),
                    onClick = { confirmReset = true },
                    modifier = Modifier.testTag(SettingsTestTags.RESET),
                )
            }
        }

        item {
            NoteDefaultsCard(defaults, onNoteDefaults)
        }

        item {
            SettingsCard(stringResource(R.string.settings_section_templates), icon = Icons.Outlined.DashboardCustomize) {
                SettingsLinkRow(
                    title = stringResource(R.string.templates_title_manage),
                    summary = stringResource(R.string.settings_templates_summary),
                    onClick = onManageTemplates,
                    modifier = Modifier.testTag(SettingsTestTags.TEMPLATES),
                )
            }
        }

        item {
            SettingsCard(stringResource(R.string.settings_section_handwriting), icon = Icons.Outlined.Draw) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_shape_correction),
                    summary = stringResource(R.string.settings_shape_correction_summary),
                    checked = ink.shapeCorrection,
                    onCheckedChange = onShapeCorrectionChanged,
                )
                if (ink.shapeCorrection) {
                    SubHeading(stringResource(R.string.settings_hold_time))
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
                SubHeading(stringResource(R.string.settings_finger))
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
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_scroll_bar),
                    summary = stringResource(R.string.settings_scroll_bar_summary),
                    checked = ink.scrollBar,
                    onCheckedChange = { on -> onInk { it.copy(scrollBar = on) } },
                    modifier = Modifier.testTag(SettingsTestTags.SCROLL_BAR),
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_back_guard),
                    summary = stringResource(R.string.settings_back_guard_summary),
                    checked = ink.backGuard,
                    onCheckedChange = { on -> onInk { it.copy(backGuard = on) } },
                    modifier = Modifier.testTag(SettingsTestTags.BACK_GUARD),
                )
                // Customise the tool bar: which tools it shows (pen and eraser always).
                SubHeading(stringResource(R.string.settings_toolbar))
                com.appname.notes.core.ink.input.EditorTool.entries
                    .filter { it != com.appname.notes.core.ink.input.EditorTool.PEN && it != com.appname.notes.core.ink.input.EditorTool.ERASER }
                    .forEach { tool ->
                        SettingsSwitchRow(
                            title = stringResource(com.appname.notes.feature.ink.toolLabel(tool)),
                            checked = tool !in ink.hiddenTools,
                            onCheckedChange = { on -> onInk { s -> s.copy(hiddenTools = if (on) s.hiddenTools - tool else s.hiddenTools + tool) } },
                            modifier = Modifier.testTag(SettingsTestTags.tool(tool)),
                        )
                    }
            }
        }

        item {
            SettingsCard(stringResource(R.string.settings_section_privacy), icon = Icons.Outlined.Shield) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_document_text),
                    summary = stringResource(R.string.settings_document_text_summary),
                    checked = documentText,
                    onCheckedChange = onDocumentTextChanged,
                    modifier = Modifier.testTag(SettingsTestTags.DOCUMENT_TEXT),
                )
                SettingsInfoRow(
                    title = stringResource(R.string.settings_storage),
                    value = stringResource(R.string.settings_storage_value),
                )
            }
        }

        item { securityCard() }

        item { backupCard() }

        item {
            SettingsCard(stringResource(R.string.settings_section_maintenance), icon = Icons.Outlined.Build) {
                SettingsActionRow(
                    title = stringResource(R.string.settings_rebuild_index),
                    summary = stringResource(R.string.settings_rebuild_index_summary),
                    onClick = onRebuildSearchIndex,
                )
            }
        }

        item {
            SettingsCard(stringResource(R.string.settings_section_about), icon = Icons.Outlined.Info) {
                SettingsInfoRow(title = stringResource(R.string.settings_version), value = BuildConfig.VERSION_NAME)
            }
            Spacer(Modifier.navigationBarsPadding().height(AppTheme.spacing.xxl))
        }
    }

    if (pickingWallpaper) {
        WallpaperDialog(
            current = appearance.wallpaper,
            photo = wallpaperPhoto,
            onPick = { pickingWallpaper = false; actions.onWallpaper(it) },
            onPhoto = { pickingWallpaper = false; actions.onWallpaperPhoto(it) },
            onDismiss = { pickingWallpaper = false },
        )
    }
    if (pickingHue) {
        CustomHueDialog(
            initial = appearance.customHue,
            onPick = { pickingHue = false; actions.onCustomHue(it) },
            onDismiss = { pickingHue = false },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset_appearance)) },
            text = { Text(stringResource(R.string.settings_reset_appearance_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; actions.onReset() }, modifier = Modifier.testTag(SettingsTestTags.RESET_CONFIRM)) {
                    Text(stringResource(R.string.settings_reset))
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SubHeading(text: String) {
    val spacing = AppTheme.spacing
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = spacing.lg, end = spacing.lg, top = spacing.md),
    )
}

/** The accent presets as colour dots, plus a rainbow dot for a custom colour (owner brief §19). */
@Composable
private fun AccentRow(appearance: AppearanceSettings, dimmed: Boolean, onAccent: (Accent) -> Unit, onCustom: () -> Unit) {
    val spacing = AppTheme.spacing
    val dark = LocalDarkTheme.current
    Column(Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.sm)) {
        Text(stringResource(R.string.settings_accent), style = MaterialTheme.typography.bodyLarge)
        // Seven 48 dp targets fit in one line on phones; narrower screens wrap.
        FlowRow(
            Modifier.fillMaxWidth().padding(top = spacing.sm).selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Accent.entries.forEach { a ->
                val name = stringResource(a.labelRes())
                Swatch(
                    fill = SolidColor(Palette.scheme(a.hue, dark).primary),
                    selected = appearance.accent == a && !dimmed,
                    label = name,
                    dimmed = dimmed,
                    onClick = { onAccent(a) },
                    tag = SettingsTestTags.accent(a),
                )
            }
            Swatch(
                fill = Brush.sweepGradient(listOf(Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFE040FB), Color(0xFFFF5252))),
                selected = appearance.accent == null && !dimmed,
                label = stringResource(R.string.settings_accent_custom),
                dimmed = dimmed,
                onClick = onCustom,
                tag = SettingsTestTags.ACCENT_CUSTOM,
            )
        }
    }
}

@Composable
private fun Swatch(fill: Brush, selected: Boolean, label: String, dimmed: Boolean, onClick: () -> Unit, tag: String) {
    Box(
        Modifier
            .size(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(fill, alpha = if (dimmed) 0.4f else 1f)
                .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

/** A hue slider with a live preview of the accent (the full colour picker comes with the drawing tools). */
@Composable
private fun CustomHueDialog(initial: Float, onPick: (Float) -> Unit, onDismiss: () -> Unit) {
    var hue by remember { mutableFloatStateOf(initial) }
    val dark = LocalDarkTheme.current
    val scheme = Palette.scheme(hue, dark)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_accent_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(MaterialTheme.shapes.medium).background(scheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.settings_accent_preview), color = scheme.onPrimary) }
                Box(
                    Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(
                        Brush.horizontalGradient((0..6).map { Color.hsl(it * 60f % 360f, 0.8f, 0.5f) }),
                    ),
                )
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..359f,
                    modifier = Modifier.testTag(SettingsTestTags.HUE_SLIDER),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(hue) }, modifier = Modifier.testTag(SettingsTestTags.HUE_SAVE)) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** None, the built-in wallpapers, or a photo from the photo picker (no storage permission needed). */
@Composable
private fun WallpaperDialog(
    current: WallpaperChoice,
    photo: ImageBitmap?,
    onPick: (WallpaperChoice) -> Unit,
    onPhoto: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onPhoto(uri) }
    val choices: List<WallpaperChoice> = listOf(WallpaperChoice.None) + WallpaperPreset.entries.map { WallpaperChoice.Preset(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(SettingsTestTags.WALLPAPER_DIALOG),
        title = { Text(stringResource(R.string.settings_wallpaper)) },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                choices.forEach { choice ->
                    WallpaperTile(choice.toSpec(photo), stringResource(choice.labelRes()), selected = choice == current, tag = SettingsTestTags.wallpaper(choice)) {
                        onPick(choice)
                    }
                }
                val currentPhoto = current as? WallpaperChoice.Photo
                if (currentPhoto != null && photo != null) {
                    WallpaperTile(WallpaperSpec.Photo(photo), stringResource(R.string.settings_wallpaper_photo), selected = true, tag = "wallpaper_photo_current") {
                        onPick(currentPhoto)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                    Box(
                        Modifier
                            .width(76.dp)
                            .aspectRatio(0.62f)
                            .clip(MaterialTheme.shapes.medium)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                            .selectable(selected = false, role = Role.Button) {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            .testTag(SettingsTestTags.WALLPAPER_PHOTO),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary) }
                    Text(
                        stringResource(R.string.settings_wallpaper_choose_photo),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun WallpaperTile(spec: WallpaperSpec, label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        Box(
            Modifier
                .width(76.dp)
                .aspectRatio(0.62f)
                .clip(MaterialTheme.shapes.medium)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    MaterialTheme.shapes.medium,
                )
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = label }
                .testTag(tag),
            contentAlignment = Alignment.Center,
        ) {
            WallpaperThumbnail(spec, Modifier.fillMaxSize())
            if (spec == WallpaperSpec.None) Icon(Icons.Outlined.Block, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selected) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
    }
}

/** What the wallpaper choice looks like; a photo that couldn't be loaded shows as none. */
fun WallpaperChoice.toSpec(photo: ImageBitmap?): WallpaperSpec = when (this) {
    WallpaperChoice.None -> WallpaperSpec.None
    is WallpaperChoice.Preset -> WallpaperSpec.Preset(preset)
    is WallpaperChoice.Photo -> photo?.let { WallpaperSpec.Photo(it) } ?: WallpaperSpec.None
}

private fun WallpaperChoice.labelRes(): Int = when (this) {
    WallpaperChoice.None -> R.string.settings_wallpaper_none
    is WallpaperChoice.Photo -> R.string.settings_wallpaper_photo
    is WallpaperChoice.Preset -> when (preset) {
        WallpaperPreset.AURORA -> R.string.wallpaper_aurora
        WallpaperPreset.OCEAN -> R.string.wallpaper_ocean
        WallpaperPreset.SUNSET -> R.string.wallpaper_sunset
        WallpaperPreset.MEADOW -> R.string.wallpaper_meadow
        WallpaperPreset.MIST -> R.string.wallpaper_mist
    }
}

private fun Accent.labelRes(): Int = when (this) {
    Accent.BLUE -> R.string.accent_blue
    Accent.VIOLET -> R.string.accent_violet
    Accent.PINK -> R.string.accent_pink
    Accent.ORANGE -> R.string.accent_orange
    Accent.AMBER -> R.string.accent_amber
    Accent.MINT -> R.string.accent_mint
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

private fun ThemeMode.shortLabelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system_short
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

object SettingsTestTags {
    const val BACK = "settings_back"
    const val TEMPLATES = "settings_templates"
    const val DEFAULTS = "settings_note_defaults"
    const val DEFAULT_STYLE = "settings_default_style"
    const val DARK_PAPER = "settings_dark_paper"
    fun blankNote(k: com.appname.notes.core.settings.BlankNoteKind) = "settings_blank_${k.name.lowercase()}"
    fun defaultFormat(f: com.appname.notes.core.ink.template.PageFormat) = "settings_format_${f.name.lowercase()}"
    fun defaultMode(m: com.appname.notes.core.ink.layout.PageMode) = "settings_mode_${m.name.lowercase()}"
    fun defaultAxis(a: com.appname.notes.core.ink.layout.ScrollAxis) = "settings_axis_${a.name.lowercase()}"
    fun defaultPaper(i: Int) = "settings_paper_$i"
    const val SCROLL_BAR = "settings_scroll_bar"
    const val BACK_GUARD = "settings_back_guard"
    fun tool(t: com.appname.notes.core.ink.input.EditorTool) = "settings_tool_${t.name.lowercase()}"
    const val SCREEN = "screen_settings"
    const val LIST = "settings_list"
    const val DOCUMENT_TEXT = "settings_document_text"
    const val GLASS = "settings_glass"
    const val BLUR = "settings_blur"
    const val CORNER = "settings_corner"
    const val WALLPAPER = "settings_wallpaper"
    const val WALLPAPER_DIALOG = "settings_wallpaper_dialog"
    const val WALLPAPER_PHOTO = "settings_wallpaper_photo"
    const val DENSITY_COMPACT = "settings_density_compact"
    const val DENSITY_COMFORTABLE = "settings_density_comfortable"
    const val RESET = "settings_reset"
    const val RESET_CONFIRM = "settings_reset_confirm"
    const val ACCENT_CUSTOM = "settings_accent_custom"
    const val HUE_SLIDER = "settings_hue_slider"
    const val HUE_SAVE = "settings_hue_save"
    fun theme(mode: ThemeMode) = "settings_theme_${mode.name}"
    fun accent(a: Accent) = "settings_accent_${a.name}"
    fun wallpaper(choice: WallpaperChoice) = "settings_wallpaper_${choice.stored}"
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
