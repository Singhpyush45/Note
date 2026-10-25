package com.appname.notes.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.appname.notes.R
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.feature.ink.InkPaints
import com.appname.notes.feature.ink.label

/**
 * "Choose template" from the + menu: the page styles a handwritten note can start with, each shown as a small
 * page. (Phase 6c replaces this with the full template screen and categories.)
 */
@Composable
fun TemplatePickerDialog(onPick: (PageTemplate) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(LibraryTestTags.TEMPLATE_PICKER),
        title = { Text(stringResource(R.string.create_choose_template)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PageTemplate.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { t ->
                            TemplateTile(t, onClick = { onDismiss(); onPick(t) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun TemplateTile(template: PageTemplate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .testTag(LibraryTestTags.template(template))
            .padding(4.dp),
    ) {
        val rule = Color(InkPaints.RULE)
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        ) { drawPreview(template, rule) }
        Text(
            stringResource(template.label()),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** A page in miniature: paper colour is fixed (ink is drawn on "paper"), patterns as in the editor. */
private fun DrawScope.drawPreview(template: PageTemplate, rule: Color) {
    drawRect(Color(InkPaints.PAPER))
    val s = size.width / 9f
    when (template) {
        PageTemplate.BLANK -> Unit
        PageTemplate.LINED -> {
            var y = s * 2
            while (y < size.height - s / 2) { drawLine(rule, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()); y += s }
            drawLine(Color(InkPaints.MARGIN), Offset(s * 1.5f, 0f), Offset(s * 1.5f, size.height), 1.dp.toPx())
        }
        PageTemplate.GRID -> {
            var x = s
            while (x < size.width) { drawLine(rule, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx()); x += s }
            var y = s
            while (y < size.height) { drawLine(rule, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()); y += s }
        }
        PageTemplate.DOTTED -> {
            var y = s
            while (y < size.height) {
                var x = s
                while (x < size.width) { drawCircle(Color(InkPaints.DOT), 1.4.dp.toPx(), Offset(x, y)); x += s }
                y += s
            }
        }
    }
}
