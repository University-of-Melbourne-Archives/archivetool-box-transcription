package au.edu.unimelb.archivetool.gui.ocr

import au.edu.unimelb.archivetool.switchboard.jobstate.BoxMetadata
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.switchboard.jobstate.RecordSetID
import au.edu.unimelb.archivetool.util.Colour
import au.edu.unimelb.archivetool.util.MonoColour
import io.nacular.doodle.application.Application
import io.nacular.doodle.controls.PopupManager
import io.nacular.doodle.controls.buttons.PushButton
import io.nacular.doodle.controls.text.Label
import io.nacular.doodle.core.Display
import io.nacular.doodle.core.WindowGroup
import io.nacular.doodle.core.container
import io.nacular.doodle.drawing.FontLoader
import io.nacular.doodle.drawing.TextMetrics
import io.nacular.doodle.drawing.paint
import io.nacular.doodle.geometry.Size
import io.nacular.doodle.image.ImageLoader
import io.nacular.doodle.layout.constraints.Strength.Companion.Medium
import io.nacular.doodle.layout.constraints.constrain
import io.nacular.doodle.theme.Theme
import io.nacular.doodle.theme.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class OCRInterface(
    jobsIn: Map<JobRelativePath, BoxMetadata?>,
    display: Display,
    windows: WindowGroup,
    themeManager: ThemeManager,
    theme: Theme,
    imageLoader: ImageLoader,
    textMetrics: TextMetrics,
    private val popups: PopupManager,
    fonts: FontLoader,
) : Application {
    // -- Job data ------------------------------------------------------------
    private val jobs: MutableMap<JobRelativePath, BoxMetadata?> = jobsIn.toMutableMap()

    private val paths: List<JobRelativePath> = jobs.keys.toList()

    private var currentIndex = 0

    private val currentPath: JobRelativePath
        get() = paths[currentIndex]

    private val currentJob: BoxMetadata?
        get() = jobs.getValue(currentPath)

    // -- Views ---------------------------------------------------------------
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fileCounter = Label("")
    private val viewport = ImageViewport(imageLoader, appScope)
    private val table = ReviewTable(textMetrics, fonts, appScope)

    init {
        windows.main.apply {
            title = "OCRReview"
            size = Size(1200.0, 675.0)
        }

        themeManager.selected = theme

        // -- Below Viewport --------------------------------------------------
        val bottomButtonsViewport =
            listOf(
                PushButton("+").apply { fired += { viewport.zoomIn() } },
                PushButton("-").apply { fired += { viewport.zoomOut() } },
                PushButton("Fit").apply { fired += { viewport.scaleToFit() } },
            )

        val buttonWidthViewport = 40.0
        val buttonHeightViewport = 40.0
        val buttonGapViewport = 15.0
        val bottomRowWidthViewport =
            bottomButtonsViewport.size * buttonWidthViewport +
                (bottomButtonsViewport.size - 1) * buttonGapViewport

        val buttonRowViewport = container {
            bottomButtonsViewport.forEach { this += it }

            layout =
                constrain(
                    bottomButtonsViewport[0],
                    bottomButtonsViewport[1],
                    bottomButtonsViewport[2],
                ) { buttons ->
                    buttons.forEach {
                        it.top eq 0
                        it.width eq buttonWidthViewport
                        it.height eq buttonHeightViewport
                    }

                    var prev = buttons.first()
                    prev.left eq 0
                    buttons.listIterator(1).forEachRemaining {
                        it.left eq prev.right + buttonGapViewport
                        prev = it
                    }
                }
        }

        val bottomBarViewport = container {
            this += buttonRowViewport

            layout =
                constrain(buttonRowViewport) { row ->
                    row.top eq 0
                    row.centerX eq parent.centerX
                    row.width eq bottomRowWidthViewport
                    row.height eq buttonHeightViewport
                }
        }

        // -- Below Table -----------------------------------------------------
        val bottomButtonsTable =
            listOf(
                PushButton("Previous").apply { fired += { moveIndex(currentIndex - 1) } },
                PushButton("Next").apply { fired += { moveIndex(currentIndex + 1) } },
                PushButton("Save").apply { fired += { save() } },
                PushButton("Finish").apply { fired += {} },
            )

        val buttonWidthTable = 108.0
        val buttonHeightTable = 40.0
        val buttonGapTable = 20.0
        val bottomRowWidthTable =
            bottomButtonsTable.size * buttonWidthTable +
                (bottomButtonsTable.size - 1) * buttonGapTable

        val buttonRowTable = container {
            bottomButtonsTable.forEach { this += it }

            layout =
                constrain(
                    bottomButtonsTable[0],
                    bottomButtonsTable[1],
                    bottomButtonsTable[2],
                    bottomButtonsTable[3],
                ) { buttons ->
                    buttons.forEach {
                        it.top eq 0
                        it.width eq buttonWidthTable
                        it.height eq buttonHeightTable
                    }

                    var prev = buttons.first()
                    prev.left eq 0
                    buttons.listIterator(1).forEachRemaining {
                        it.left eq prev.right + buttonGapTable
                        prev = it
                    }
                }
        }

        val bottomBarTable = container {
            this += buttonRowTable
            this += fileCounter

            layout =
                constrain(buttonRowTable, fileCounter) { row, txt ->
                    row.top eq 0
                    row.centerX eq parent.centerX
                    row.width eq bottomRowWidthTable
                    row.height eq buttonHeightTable

                    txt.top eq row.bottom + 10
                    txt.right eq parent.right
                    txt.width eq fileCounter.idealSize.width
                    txt.height eq fileCounter.idealSize.height
                }
        }

        display += listOf(bottomBarViewport, viewport, bottomBarTable, table.view)
        display.fill(MonoColour(0xECu).toDoodle().toRGB().paint)

        display.layout =
            constrain(bottomBarViewport, viewport, bottomBarTable, table.view) { bbv, vp, bbt, tb ->
                vp.top eq 35
                vp.bottom eq parent.height - 80
                vp.centerX eq vp.width / 1.75
                vp.height lessEq parent.height - vp.top - 30
                vp.width lessEq parent.width / 3
                vp.width eq vp.height * (3.0 / 4.0)
                vp.width eq 5000 strength Medium

                tb.top eq vp.top
                tb.bottom eq vp.bottom
                tb.left eq vp.right + 20
                tb.right eq parent.width - 20
                tb.height eq vp.height

                bbv.top eq vp.bottom + 10
                bbv.left eq vp.left
                bbv.width eq vp.width
                bbv.bottom eq parent.bottom - 10

                bbt.top eq tb.bottom + 10
                bbt.left eq tb.left
                bbt.width eq tb.width
                bbt.bottom eq parent.bottom - 10
            }

        // Initial state
        if (paths.isNotEmpty()) {
            displayCurrent()
        }
    }

    private fun moveIndex(index: Int) {
        if (index !in paths.indices || index == currentIndex) {
            return
        }

        currentIndex = index
        displayCurrent()
    }

    private fun displayCurrent() {
        viewport.loadImage(currentPath)
        fileCounter.text = "%03d/%03d".format(currentIndex + 1, paths.size)
        displayJobText()
    }

    // -- Review Table --------------------------------------------------------

    private fun displayJobText() {
        val job = currentJob

        table.cellTexts = listOf(formatTitleCell(job), job?.description ?: "", "TO IMPLEMENT")
    }

    // Column - Node Title: "<Record Set ID> Unit <%04d Unit Number>"
    private fun formatTitleCell(job: BoxMetadata?): String {
        val id = job?.id
        val unit = job?.unitNumber
        return when {
            id != null && unit != null -> id.CSVString(unit)

            // For incomplete input data to make partial string
            id != null -> "${id.id} Unit "
            unit != null -> "Unit ${"%04d".format(unit)}"
            else -> ""
        }
    }

    // Returns null for an empty cell and throws an IllegalArgumentException if the string is
    // invalid
    private fun parseNameCell(text: String): Pair<RecordSetID, Int>? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        // Checks if string matches
        val match =
            Regex("^(.+?)\\s+Unit\\s+(\\d{1,4})$").matchEntire(trimmed)
                ?: throw IllegalArgumentException(
                    "Expected \"<Record Set ID> Unit <number>\", e.g. \"1974.0029 Unit 0319\"."
                )

        // Extract the record set id and box number
        val (idText, boxNumberText) = match.destructured

        return (RecordSetID.fromUserInput(idText) to boxNumberText.toInt())
    }

    // -- Saving --------------------------------------------------------------

    private fun save() {
        if (paths.isEmpty()) {
            return
        }

        val (nodeTitleText, descriptionText, dateRangeText) = table.cellTexts

        val errors = mutableListOf<String>()

        // Node Title column output
        var idAndUnit: Pair<RecordSetID, Int>? = null
        try {
            idAndUnit = parseNameCell(nodeTitleText)
        } catch (e: IllegalArgumentException) {
            errors += "${ReviewTable.HEADERS[0]}: ${e.message}"
        }

        // Date Range column output
        // to-do

        if (errors.isNotEmpty()) {
            showErrors(errors)
            return
        }

        val updatedJob =
            BoxMetadata(
                id = idAndUnit?.first,
                unitNumber = idAndUnit?.second,
                dateRange = null,
                description = descriptionText,
            )

        jobs[currentPath] = updatedJob

        displayJobText()
    }

    private fun showErrors(errors: List<String>) {
        val title = Label("Cannot Save Changes - Please Fix the Following:")
        // • Header: error message
        val messageLines = errors.map { Label("\u2022 $it") }
        val okButton = PushButton("OK")

        val allElements = listOf(title) + messageLines + okButton

        val padding = 20.0
        val rowHeight = 24.0
        val rowGap = 6.0
        val okButtonHeight = 36.0

        val messageWidth = 460.0
        val messageHeight =
            padding +
                (allElements.size - 1) * rowHeight +
                (allElements.size - 2) * rowGap +
                16.0 +
                okButtonHeight +
                padding

        val dialog = container {
            render = {
                rect(bounds.atOrigin, 10.0, fill = Colour.PALE_GREY.toDoodle().toRGB().paint)
            }

            allElements.forEach { this += it }

            layout =
                constrain(allElements[0], allElements[1], *allElements.drop(2).toTypedArray()) { b
                    ->
                    val messageRows = b.dropLast(1)
                    val okButtonBounds = b.last()

                    messageRows.forEach {
                        it.left eq padding
                        it.right eq parent.right - padding
                        it.height eq rowHeight
                    }

                    // Vertically spaces all messages
                    messageRows.first().top eq padding
                    messageRows.zipWithNext().forEach { (above, below) ->
                        below.top eq above.bottom + rowGap
                    }

                    okButtonBounds.top eq messageRows.last().bottom + 16.0
                    okButtonBounds.centerX eq parent.centerX
                    okButtonBounds.width eq 80.0
                    okButtonBounds.height eq okButtonHeight
                }
        }

        okButton.fired += { popups.hide(dialog) }

        popups.show(dialog) {
            it.width eq messageWidth
            it.height eq messageHeight
            it.center eq parent.center
        }
    }

    override fun shutdown() {}
}
