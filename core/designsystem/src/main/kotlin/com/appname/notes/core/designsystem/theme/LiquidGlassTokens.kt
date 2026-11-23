@file:Suppress("unused")

package com.appname.notes.core.designsystem.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Priority 1 & Priority 6 — Consistent Subtle Interaction Feedback System (`NoRippleIndication`).
 * Eliminates ugly, oversized circular Material Ripple overlays across buttons, icons, toolbar tools,
 * toggle switches, and navigation items while preserving restrained, accessible press/focus opacity feedback.
 */
object NoRippleIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        SubtlePressFeedbackNode(interactionSource)

    override fun hashCode(): Int = -109482731
    override fun equals(other: Any?): Boolean = other === this

    private class SubtlePressFeedbackNode(
        private val interactionSource: InteractionSource,
    ) : Modifier.Node(), DrawModifierNode {
        private var isPressed = false
        private var isFocused = false

        override fun onAttach() {
            coroutineScope.launch {
                val activePresses = mutableSetOf<PressInteraction.Press>()
                val activeFocuses = mutableSetOf<FocusInteraction.Focus>()
                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> activePresses.add(interaction)
                        is PressInteraction.Release -> activePresses.remove(interaction.press)
                        is PressInteraction.Cancel -> activePresses.remove(interaction.press)
                        is FocusInteraction.Focus -> activeFocuses.add(interaction)
                        is FocusInteraction.Unfocus -> activeFocuses.remove(interaction.focus)
                        is HoverInteraction.Enter -> Unit
                        is HoverInteraction.Exit -> Unit
                    }
                    val newPressed = activePresses.isNotEmpty()
                    val newFocused = activeFocuses.isNotEmpty()
                    if ((newPressed != isPressed) || (newFocused != isFocused)) {
                        isPressed = newPressed
                        isFocused = newFocused
                        invalidateDraw()
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            // Subtle, uniform opacity veil on press/focus — zero circular expanding ripple splash!
            if (isPressed) {
                drawRoundRect(
                    color = Color.Gray.copy(alpha = 0.08f),
                    cornerRadius = CornerRadius(size.minDimension * 0.25f, size.minDimension * 0.25f),
                )
            } else if (isFocused) {
                drawRoundRect(
                    color = Color.Gray.copy(alpha = 0.05f),
                    cornerRadius = CornerRadius(size.minDimension * 0.25f, size.minDimension * 0.25f),
                )
            }
        }
    }
}

object BrandIdentity {
    const val APP_NAME = "Papriva"
    const val TAGLINE = "Liquid Glass Notes, Ink & Daily Planner"
}

@Immutable
data class NeutralLiquidGlassPalette(
    val canvasBackground: Color,
    val primaryGlassSurface: Color,
    val elevatedModalSurface: Color,
    val floatingPillSurface: Color,
    val primaryBorder: Color,
    val topSpecularHighlight: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val mutedIcon: Color,
    val activeSelectionPill: Color,
    val activeSelectionBorder: Color,
    val primaryActionBackground: Color,
    val primaryActionContent: Color,
)

object LiquidGlassTokens {
    val ToolbarCornerRadius: Dp = 24.dp
    val SheetCornerRadius: Dp = 28.dp
    val CardCornerRadius: Dp = 20.dp
    val PillCornerRadius: Dp = 999.dp
    val HairlineBorderWidth: Dp = 0.85.dp

    val Light = NeutralLiquidGlassPalette(
        canvasBackground = Color(0xFFF8F5EE),
        primaryGlassSurface = Color(0xD9FAF7F2),
        elevatedModalSurface = Color(0xF5FAF7F2),
        floatingPillSurface = Color(0xEBFAF7F2),
        primaryBorder = Color(0x1F2C251E),
        topSpecularHighlight = Color(0xE6FFFFFF),
        primaryText = Color(0xFF1C1A17),
        secondaryText = Color(0xFF6E665C),
        mutedIcon = Color(0xFF4B463F),
        activeSelectionPill = Color(0x1A1E3F32),
        activeSelectionBorder = Color(0x331E3F32),
        primaryActionBackground = Color(0xFF1E3F32),
        primaryActionContent = Color(0xFFFAF7F2),
    )

    val Dark = NeutralLiquidGlassPalette(
        canvasBackground = Color(0xFF0C0D10),
        primaryGlassSurface = Color(0xCC16181D),
        elevatedModalSurface = Color(0xF214161B),
        floatingPillSurface = Color(0xE6181A20),
        primaryBorder = Color(0x24FFFFFF),
        topSpecularHighlight = Color(0x2EFFFFFF),
        primaryText = Color(0xFFF6F3EC),
        secondaryText = Color(0xFFA39E93),
        mutedIcon = Color(0xFFD1CCC0),
        activeSelectionPill = Color(0x24FFFFFF),
        activeSelectionBorder = Color(0x42FFFFFF),
        primaryActionBackground = Color(0xFF8FB99E),
        primaryActionContent = Color(0xFF101215),
    )

    fun palette(darkTheme: Boolean): NeutralLiquidGlassPalette = if (darkTheme) Dark else Light
}
