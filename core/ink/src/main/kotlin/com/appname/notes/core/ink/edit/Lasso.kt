package com.appname.notes.core.ink.edit

import com.appname.notes.core.ink.model.TextItem
import com.appname.notes.core.ink.model.ImageItem
import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.geometry.bounds
import com.appname.notes.core.ink.geometry.pointInPolygon
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.StrokeItem

/**
 * Lasso selection and what can be done with a selection: move, copy, delete, recolour (SRS 6.2).
 * All actions return an [InkEdit], so each is one undo step.
 */
object Lasso {

    /** Share of an item's points that must be inside the lasso for it to be selected. */
    const val MIN_INSIDE = 0.6

    /** Items mostly inside the closed [lasso] polygon (page units). */
    fun select(items: List<InkItem>, lasso: List<Vec>, minInside: Double = MIN_INSIDE): List<InkItem> {
        if (lasso.size < 3) return emptyList()
        val area = bounds(lasso)
        return items.filter { item ->
            if (!item.bounds().intersects(area)) return@filter false
            val pts = samplePoints(item)
            pts.count { area.contains(it) && pointInPolygon(it, lasso) } >= minInside * pts.size
        }
    }

    private fun samplePoints(item: InkItem): List<Vec> = when (item) {
        is StrokeItem -> {
            val n = item.points.size
            val stride = maxOf(1, n / 128) // cap the work for very long strokes
            (0 until n step stride).map { item.points.point(it) }
        }
        is ShapeItem -> item.geometry.outline()
        is ImageItem -> item.place.outline()
        is TextItem -> item.place.outline()
        is com.appname.notes.core.ink.model.AudioItem -> item.place.outline()
    }

    fun move(selection: List<InkItem>, dx: Double, dy: Double): InkEdit = transform(selection, Similarity.translate(dx, dy))

    fun transform(selection: List<InkItem>, t: Similarity): InkEdit =
        InkEdit(selection, selection.map { it.transformed(t) })

    fun delete(selection: List<InkItem>): InkEdit = InkEdit.remove(selection)

    fun recolor(selection: List<InkItem>, colorArgb: Int): InkEdit = InkEdit(
        selection,
        selection.map { item ->
            val brush: InkBrush = item.brush.copy(colorArgb = colorArgb)
            when (item) {
                is StrokeItem -> item.copy(brush = brush)
                is ShapeItem -> item.copy(brush = brush)
                is ImageItem, is com.appname.notes.core.ink.model.AudioItem -> item
                is TextItem -> item.copy(brush = item.brush.copy(colorArgb = colorArgb or (0xFF shl 24)))
            }
        },
    )

    /**
     * Copies of the selection, moved by ([dx], [dy]), with new ids, stacked on top of the page
     * starting at [firstZ] and keeping their relative order.
     */
    fun duplicate(
        selection: List<InkItem>,
        dx: Double,
        dy: Double,
        firstZ: Long,
        newId: () -> String,
    ): InkEdit {
        val t = Similarity.translate(dx, dy)
        val copies = selection.sortedWith(com.appname.notes.core.ink.model.PaintOrder).mapIndexed { i, item ->
            val moved = item.transformed(t).withZ(firstZ + i)
            when (moved) {
                is StrokeItem -> moved.copy(id = newId())
                is ShapeItem -> moved.copy(id = newId())
                is ImageItem -> moved.copy(id = newId())
                is TextItem -> moved.copy(id = newId())
                is com.appname.notes.core.ink.model.AudioItem -> moved.copy(id = newId())
            }
        }
        return InkEdit(emptyList(), copies)
    }
}
