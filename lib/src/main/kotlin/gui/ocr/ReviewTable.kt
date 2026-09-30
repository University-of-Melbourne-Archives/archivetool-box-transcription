package au.edu.unimelb.archivetool.gui.ocr

import io.nacular.doodle.controls.panels.ScrollPanel
import io.nacular.doodle.controls.text.Label
import io.nacular.doodle.controls.text.TextField
import io.nacular.doodle.core.View
import io.nacular.doodle.core.container
import io.nacular.doodle.layout.constraints.constrain

class ReviewTable {
    companion object {
        val HEADERS = listOf("Node Title", "Description", "Date Range")

        private const val COLUMN_GAP = 10.0
        private const val HEADER_HEIGHT = 36.0
        private const val CELL_HEIGHT = 40.0
        private const val HEADER_CELL_GAP = 4.0

        private const val TABLE_HEIGHT = HEADER_HEIGHT + HEADER_CELL_GAP + CELL_HEIGHT
    }

    private fun createCell(): TextField {
        return TextField()
    }

    private val headerLabels = HEADERS.map { Label(it) }
    private val cells = HEADERS.map { createCell() }

    // For access and modification from OCRInterface
    var cellTexts: List<String>
        get() = cells.map { it.text }
        set(value) {
            cells.zip(value).forEach { (cell, text) -> cell.text = text }
        }

    private fun column(header: Label, cell: TextField) = container {
        this += header
        this += cell

        layout =
            constrain(header, cell) { hdr, cel ->
                hdr.top eq 0
                hdr.left eq 0
                hdr.width eq parent.width
                hdr.height eq HEADER_HEIGHT

                cel.top eq hdr.bottom + HEADER_CELL_GAP
                cel.left eq 0
                cel.width eq parent.width
                cel.height eq CELL_HEIGHT
            }
    }

    // Creates list of the headers paired with their text cell
    private val columns = headerLabels.zip(cells).map { (hdr, cel) -> column(hdr, cel) }

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

    val view: View =
        ScrollPanel(tableContents).apply {
            contentWidthConstraints = { it eq parent.width }
            contentHeightConstraints = { it eq TABLE_HEIGHT }
        }
}
