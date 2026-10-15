package au.edu.unimelb.archivetool.gui.ocr

import au.edu.unimelb.archivetool.util.Colour
import io.nacular.doodle.controls.panels.ScrollPanel
import io.nacular.doodle.controls.text.Label
import io.nacular.doodle.controls.text.TextField
import io.nacular.doodle.core.View
import io.nacular.doodle.core.container
import io.nacular.doodle.drawing.Canvas
import io.nacular.doodle.drawing.FontLoader
import io.nacular.doodle.drawing.TextMetrics
import io.nacular.doodle.drawing.paint
import io.nacular.doodle.drawing.text
import io.nacular.doodle.geometry.Point
import io.nacular.doodle.layout.constraints.constrain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Text that is read only, wraps at spaces, and breaks words that are wider than the current view.
 */
private class WrappedText(private val textMetrics: TextMetrics) : View() {
    companion object {
        const val LINE_HEIGHT = 20.0
        const val MIN_HEIGHT = 20.0

        // To prevent rendering issue when character space fits line exactly
        const val WRAP_MARGIN = 2.0
    }

    var content: String = ""
        set(value) {
            field = value
            rerender()
        }

    // Wraps text at the width of the current view
    override fun render(canvas: Canvas) {
        wrap(content, width - WRAP_MARGIN).forEachIndexed { i, line ->
            canvas.text(line, Point(0.0, i * LINE_HEIGHT), color = Colour.BLACK.toDoodle().toRGB())
        }
    }

    fun heightForWidth(width: Double): Double =
        maxOf(MIN_HEIGHT, wrap(content, width - WRAP_MARGIN).size * LINE_HEIGHT)

    private fun wrap(text: String, maxWidth: Double): List<String> {
        if (maxWidth <= 0.0) {
            return listOf(text)
        }

        val out = mutableListOf<String>()

        for (paragraph in text.split('\n')) {
            var line = ""

            for (word in paragraph.split(' ')) {
                val candidate: String

                if (line.isEmpty()) {
                    candidate = word
                } else {
                    candidate = "$line $word"
                }

                if (textMetrics.width(candidate) <= maxWidth) {
                    line = candidate
                    continue
                }

                if (line.isNotEmpty()) {
                    out += line
                }

                // Since the singular word is wider than a line in the current view, break it by a
                // character
                var rest = word
                while (rest.length > 1 && textMetrics.width(rest) > maxWidth) {
                    var n = 1
                    while (
                        n < rest.length && textMetrics.width(rest.substring(0, n + 1)) <= maxWidth
                    ) {
                        n++
                    }

                    out += rest.substring(0, n)
                    rest = rest.substring(n)
                }

                line = rest
            }

            out += line
        }

        return out
    }
}

/** ScrollPanel that allows table to request layout (not protected) */
private class ReviewScrollPanel(content: View) : ScrollPanel(content) {
    fun refresh() = relayout()
}

class ReviewTable(textMetrics: TextMetrics, private val fonts: FontLoader, scope: CoroutineScope) {
    companion object {
        val HEADERS = listOf("Node Title", "Description", "Date Range")

        private const val COLUMN_GAP = 10.0
        private const val HEADER_HEIGHT = 36.0
        private const val CELL_HEIGHT = 40.0
        private const val HEADER_CELL_GAP = 4.0

        // Converts line breaks and trailing spaces into a singular space
        fun singleLine(text: String): String = text.replace(LINE_BREAKS, " ")

        private val LINE_BREAKS = Regex("\\s*[\\r\\n\\u2028\\u2029]+\\s*")
    }

    private val headerLabels = HEADERS.map { Label(it) }
    private val cells = HEADERS.map { TextField() }
    private val previews = HEADERS.map { WrappedText(textMetrics) }

    // Current maximum height of preview text
    private var previewHeight = WrappedText.MIN_HEIGHT
    private var appliedBarWidth = -1.0

    // For access and modification from OCRInterface
    var cellTexts: List<String>
        get() = cells.map { it.text }
        set(value) {
            // Changing text fires textChanged, which updates the previews
            cells.zip(value).forEach { (cell, text) -> cell.text = singleLine(text) }
        }

    private val columns =
        HEADERS.indices.map { i ->
            container {
                this += headerLabels[i]
                this += cells[i]
                this += previews[i]
            }
        }

    private val tableContents = container {
        columns.forEach { this += it }

        layout =
            constrain(columns[0], columns[1], columns[2]) { ndr, des, dtr ->
                ndr.top eq 0
                ndr.height eq parent.height
                ndr.left eq 0
                ndr.width eq (parent.width - 2 * COLUMN_GAP) / 3.0

                des.top eq 0
                des.height eq parent.height
                des.left eq ndr.right + COLUMN_GAP
                des.width eq ndr.width

                dtr.top eq 0
                dtr.height eq parent.height
                dtr.left eq des.right + COLUMN_GAP
                dtr.width eq ndr.width
            }
    }

    private val scroll = ReviewScrollPanel(tableContents)
    // Container for scroll with outline
    private val roundedBackground = container {
        render = { rect(bounds.atOrigin, 12.0, fill = Colour.WHITE.toDoodle().toRGB().paint) }

        this += scroll

        layout =
            constrain(scroll) { s ->
                s.top eq 12
                s.left eq 12
                s.right eq parent.right - 12
                s.bottom eq parent.bottom - 12
            }
    }

    val view: View = roundedBackground

    init {
        // Load font for consistent input spacing
        scope.launch {
            val font = fonts {
                family = "Courier New"
                size = 14
            }
            font?.let { f -> cells.forEach { it.font = f } }
        }

        cells.forEachIndexed { i, cell ->
            cell.textChanged += { _, _, new ->
                val clean = singleLine(new)

                if (clean != new) {
                    // Since pasted text had line breaks, call textChanged again to keep preview
                    // consistent
                    cell.text = clean
                } else {
                    previews[i].content = new
                    refreshLayouts()
                }
            }
        }

        // On window resize update height
        scroll.boundsChanged += { _, old, new ->
            if (old.width != new.width) {
                refreshLayouts()
            }
        }

        // Make scroll bar appearing or disappearing change the preview columns width
        scroll.scrollBarDimensionsChanged += {
            applyContentWidth()
            refreshLayouts()
        }
        applyContentWidth()
        applyLayouts()
    }

    private fun applyContentWidth() {
        val bar = scroll.verticalScrollBarWidth

        if (bar == appliedBarWidth) {
            return
        }

        appliedBarWidth = bar
        scroll.contentWidthConstraints = { it eq parent.width - bar }
    }

    // Split column width
    private fun columnWidth(): Double =
        (scroll.width - scroll.verticalScrollBarWidth - 2 * COLUMN_GAP) / 3.0

    private fun refreshLayouts() {
        val width = columnWidth()
        if (width <= 0.0) return

        val newHeight = previews.maxOf { it.heightForWidth(width) }
        if (newHeight == previewHeight) {
            return
        }

        previewHeight = newHeight
        applyLayouts()
    }

    private fun applyLayouts() {
        val height = previewHeight

        columns.forEachIndexed { i, column ->
            column.layout =
                constrain(headerLabels[i], cells[i], previews[i]) { hdr, cel, prv ->
                    hdr.top eq 0
                    hdr.left eq 0
                    hdr.width eq parent.width
                    hdr.height eq HEADER_HEIGHT

                    cel.top eq hdr.bottom + HEADER_CELL_GAP
                    cel.left eq 0
                    cel.width eq parent.width
                    cel.height eq CELL_HEIGHT

                    prv.top eq cel.bottom + HEADER_CELL_GAP
                    prv.left eq 0
                    prv.width eq parent.width
                    prv.height eq height
                }
        }

        val combinedHeight =
            HEADER_HEIGHT + HEADER_CELL_GAP + CELL_HEIGHT + HEADER_CELL_GAP + height
        scroll.contentHeightConstraints = { it eq combinedHeight }

        // ALL the constraints of table don't render until the scroll panel is laid out here
        scroll.refresh()
    }
}
