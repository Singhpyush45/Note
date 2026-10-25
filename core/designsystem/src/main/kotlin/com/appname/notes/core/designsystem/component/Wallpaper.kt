package com.appname.notes.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import com.appname.notes.core.designsystem.theme.LocalGlass
import com.appname.notes.core.designsystem.theme.WallpaperPreset

/** What's behind the app's screens (owner brief §19 "Wallpaper / background"). */
sealed interface WallpaperSpec {
    /** Plain background colour. */
    data object None : WallpaperSpec

    data class Preset(val preset: WallpaperPreset) : WallpaperSpec

    /** The user's photo, already decoded at about screen size. */
    data class Photo(val bitmap: ImageBitmap) : WallpaperSpec
}

/**
 * Draws the wallpaper under [content]. Built-in wallpapers are soft colour fields, adapted for dark theme; a photo
 * is blurred by the "Background blur" setting (Android 12+) and veiled so text over glass stays readable.
 * Screens that show it must have a transparent background.
 */
@Composable
fun AppBackground(wallpaper: WallpaperSpec, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    val dark = LocalDarkTheme.current
    Box(modifier.fillMaxSize().background(background)) {
        when (wallpaper) {
            WallpaperSpec.None -> Unit
            is WallpaperSpec.Preset -> Canvas(Modifier.fillMaxSize()) { drawPreset(wallpaper.preset, dark, background) }
            is WallpaperSpec.Photo -> {
                val blur = LocalGlass.current.blurRadius
                Image(
                    wallpaper.bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(if (blur > 0.dp) Modifier.blur(blur) else Modifier),
                )
                Box(Modifier.fillMaxSize().background(background.copy(alpha = if (dark) 0.55f else 0.35f)))
            }
        }
        content()
    }
}

/** A small preview of a wallpaper, for the picker. */
@Composable
fun WallpaperThumbnail(wallpaper: WallpaperSpec, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val dark = LocalDarkTheme.current
    Box(modifier.background(background)) {
        when (wallpaper) {
            WallpaperSpec.None -> Unit
            is WallpaperSpec.Preset -> Canvas(Modifier.fillMaxSize()) { drawPreset(wallpaper.preset, dark, background) }
            is WallpaperSpec.Photo -> Image(wallpaper.bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

private data class Blob(val color: Color, val x: Float, val y: Float, val radius: Float)

private fun blobs(preset: WallpaperPreset): List<Blob> = when (preset) {
    WallpaperPreset.AURORA -> listOf(
        Blob(Color(0xFF9DB8FF), 0.10f, 0.12f, 0.75f),
        Blob(Color(0xFFC6B3FF), 0.95f, 0.45f, 0.70f),
        Blob(Color(0xFFA6E3FF), 0.25f, 0.95f, 0.80f),
    )
    WallpaperPreset.OCEAN -> listOf(
        Blob(Color(0xFF7CD3E0), 0.05f, 0.20f, 0.80f),
        Blob(Color(0xFF8FB5FF), 0.95f, 0.65f, 0.75f),
        Blob(Color(0xFFB4EEE2), 0.40f, 1.00f, 0.70f),
    )
    WallpaperPreset.SUNSET -> listOf(
        Blob(Color(0xFFFFB59A), 0.10f, 0.10f, 0.75f),
        Blob(Color(0xFFFFA3C6), 0.95f, 0.50f, 0.70f),
        Blob(Color(0xFFFFD69A), 0.30f, 0.95f, 0.75f),
    )
    WallpaperPreset.MEADOW -> listOf(
        Blob(Color(0xFFA7E0A0), 0.05f, 0.15f, 0.75f),
        Blob(Color(0xFFDDEFA0), 0.95f, 0.40f, 0.65f),
        Blob(Color(0xFF9FD8C8), 0.35f, 0.95f, 0.80f),
    )
    WallpaperPreset.MIST -> listOf(
        Blob(Color(0xFFC6CEDD), 0.10f, 0.10f, 0.80f),
        Blob(Color(0xFFDCE0EA), 0.90f, 0.55f, 0.70f),
        Blob(Color(0xFFBCC6D8), 0.30f, 0.95f, 0.75f),
    )
}

private fun DrawScope.drawPreset(preset: WallpaperPreset, dark: Boolean, background: Color) {
    val longSide = maxOf(size.width, size.height)
    for (b in blobs(preset)) {
        // Dark theme: the same colours, deep and low-key, so the page stays dark.
        val c = if (dark) lerp(b.color, background, 0.72f) else b.color
        val center = Offset(size.width * b.x, size.height * b.y)
        val radius = longSide * b.radius
        drawCircle(
            Brush.radialGradient(
                0f to c.copy(alpha = if (dark) 0.9f else 0.75f),
                0.6f to c.copy(alpha = if (dark) 0.25f else 0.22f),
                1f to Color.Transparent,
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}
