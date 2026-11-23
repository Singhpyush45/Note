@file:Suppress("unused")

package com.appname.notes.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appname.notes.core.designsystem.theme.LiquidGlassTokens
import com.appname.notes.core.designsystem.theme.LocalDarkTheme

/**
 * Phase 6 — Reusable Neutral Liquid Glass Dropdown Menu.
 * Replaces solid dark Material menus (`image_5ddc19.jpg`, `image_5ddc13.jpg`) with a translucent
 * white/near-black glass surface, fine specular border, and consistent 22.dp corner radius.
 */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 248.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val palette = LiquidGlassTokens.palette(isDark)
    val containerColor = if (isDark) Color(0xFF14161B).copy(alpha = 0.94f) else Color(0xFFFFFFFF).copy(alpha = 0.95f)
    val borderColor = palette.primaryBorder

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(22.dp),
        containerColor = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(0.8.dp, borderColor),
        modifier = modifier.width(width),
        content = content,
    )
}

/**
 * Neutral Liquid Glass menu item with optional leading icon and restrained monochrome selection state.
 */
@Composable
fun GlassMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    selected: Boolean = false,
    destructive: Boolean = false,
) {
    val isDark = LocalDarkTheme.current
    val palette = LiquidGlassTokens.palette(isDark)
    val primaryText = when {
        destructive -> MaterialTheme.colorScheme.error
        else -> palette.primaryText
    }
    val selectedBg = palette.activeSelectionPill

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) selectedBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leadingIcon?.let { icon ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = primaryText,
                modifier = Modifier.size(19.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = primaryText,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
fun GlassMenuDivider() {
    val isDark = LocalDarkTheme.current
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f),
    )
}

/**
 * Compact circular Liquid Glass icon button (used for the compact folder-up back button in Phase 4.2).
 */
@Composable
fun GlassCircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val isDark = LocalDarkTheme.current
    val palette = LiquidGlassTokens.palette(isDark)
    val bg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val border = palette.primaryBorder
    val tint = palette.primaryText

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bg,
        border = BorderStroke(0.75.dp, border),
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
