package au.edu.unimelb.archivetool.gui.ocr

import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.BoxMetadata
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.switchboard.jobstate.RecordSetID
import au.edu.unimelb.archivetool.util.Colour
import au.edu.unimelb.archivetool.util.DateRange
import au.edu.unimelb.archivetool.util.MonoColour
import au.edu.unimelb.archivetool.util.MutableWaitFlow
import au.edu.unimelb.archivetool.util.math.geometry.Rectangle
import au.edu.unimelb.archivetool.util.wrap.CV
import au.edu.unimelb.archivetool.util.wrap.CV.writeImage
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
import io.nacular.doodle.utils.TextAlignment
import java.nio.file.Path
import kotlin.collections.set
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class OCRInterface(
    jobsIn: Map<JobRelativePath, JobFile?>,
    private val returnFlow: MutableWaitFlow<Map<JobRelativePath, JobFile>>?,
    private val display: Display,
    windows: WindowGroup,
    themeManager: ThemeManager,
    theme: Theme,
    imageLoader: ImageLoader,
    private val textMetrics: TextMetrics,
    private val popups: PopupManager,
    fonts: FontLoader,
) : Application {
    // -- Job data ------------------------------------------------------------
    private val jobs: MutableMap<JobRelativePath, JobFile> =
        jobsIn.mapNotNull { (path, file) -> file?.let { path to it } }.toMap(mutableMapOf())

    // Has an entry for every ArchiveBox for all the JobFile and is in file order then box order
    private data class BoxReference(val path: JobRelativePath, val key: String)

    private val boxes: List<BoxReference> = jobs.flatMap { (path, file) ->
        file.vertexes?.keys.orEmpty().map { key -> BoxReference(path, key) }
    }

    private var currentIndex = 0

    private val currentReference: BoxReference?
        get() = boxes.getOrNull(currentIndex)

    private val currentBox: ArchiveBox?
        get() = currentReference?.let { jobs[it.path]?.vertexes?.get(it.key) }

    private val currentJob: BoxMetadata?
        get() = currentBox?.metadata

    // Processed images (cropped, flattened, and greyscale) are stored as temp files
    private val processedImages = mutableMapOf<BoxReference, Path>()
    private var loadJob: Job? = null

    // -- Views ---------------------------------------------------------------
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fileCounter = Label("")
    private val viewport = ImageViewport(imageLoader, appScope)
    private val table = ReviewTable(textMetrics, fonts, appScope)

    private var tableButtons: List<PushButton> = emptyList()
    private var errorDialogOpen = false

    init {
        windows.apply {
            main.apply {
                title = "OCRReview"
                size = Size(1100.0, 1000.0)
            }

            mainWindowCloseBehavior = { runBlocking { close() } }
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
                PushButton("Finish").apply {
                    fired += {
                        if (!errorDialogOpen) {
                            CoroutineScope(Dispatchers.Default).launch { close() }
                        }
                    }
                },
            )

        tableButtons = bottomButtonsTable

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
        if (boxes.isNotEmpty()) {
            displayCurrent()
        }
    }

    private fun moveIndex(index: Int) {
        if (errorDialogOpen) {
            return
        }

        if (index !in boxes.indices || index == currentIndex) {
            return
        }

        currentIndex = index
        displayCurrent()
    }

    private fun displayCurrent() {
        val reference = currentReference ?: return
        fileCounter.text = "%03d/%03d".format(currentIndex + 1, boxes.size)
        displayJobText()

        // Allocates loading only to the most recently requested image
        loadJob?.cancel()
        loadJob = appScope.launch {
            val processed = processedImageFor(reference)

            if (reference == currentReference) {
                viewport.loadImage(processed)
            }
        }
    }

    // Crops to the box segmentation boundary, flattens, and then applies greyscale to the image
    // returns temp file
    private fun processedImageFor(ref: BoxReference): Path {
        // Get temp file
        val cachedImage = processedImages[ref]
        if (cachedImage != null && cachedImage.exists()) {
            return cachedImage
        }

        val box = jobs.getValue(ref.path).vertexes!!.getValue(ref.key)
        val boundaryQuadrilateral = box.boxBoundary.points
        val bounds = boundaryQuadrilateral.bestBoundingBox()

        // The size we are projecting to
        val outRect = Rectangle.create(0, 0, bounds.width, bounds.height)

        val source = CV.readImage(ref.path.absolute)
        val warped =
            CV.PerspectiveTransformMatrix.create(boundaryQuadrilateral, outRect).use {
                CV.perspectiveWarp(source, it)
            }
        val grey = CV.bgr2grayc1(warped)

        val tempFile = createTempFile(prefix = "ocr-box-", suffix = ".png")

        // For if GUI is exited without calling close()
        tempFile.toFile().deleteOnExit()

        grey.writeImage(tempFile)

        source.release()
        warped.release()
        grey.release()

        processedImages[ref] = tempFile
        return tempFile
    }

    // -- Review Table --------------------------------------------------------

    private fun displayJobText() {
        val job = currentJob

        table.cellTexts =
            listOf(formatTitleCell(job), job?.description ?: "", job?.dateRange?.CSVString ?: "")
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

    // Returns null for a blank cell and throws an IllegalArgumentException if the text is invalid
    private fun parseDateCell(text: String): DateRange? {
        if (text.isBlank()) {
            return null
        }

        return DateRange.fromUserInput(text)
    }

    // -- Saving --------------------------------------------------------------

    private fun save() {
        if (errorDialogOpen || boxes.isEmpty()) {
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
        var dateRange: DateRange? = null
        try {
            dateRange = parseDateCell(dateRangeText)
        } catch (e: IllegalArgumentException) {
            errors += "${ReviewTable.HEADERS[2]}: ${e.message}"
        }

        if (errors.isNotEmpty()) {
            showErrors(errors)
            return
        }

        val updatedJob =
            BoxMetadata(
                id = idAndUnit?.first,
                unitNumber = idAndUnit?.second,
                dateRange = dateRange,
                description = descriptionText,
            )

        val reference = currentReference
        val vertexes = reference?.let { jobs[it.path]?.vertexes }

        if (reference == null || vertexes == null) {
            showErrors(
                listOf("This file does not have a corresponding location for save to occur.")
            )
            return
        }

        // Saves changes
        vertexes[reference.key] = vertexes.getValue(reference.key).copy(metadata = updatedJob)

        displayJobText()
    }

    suspend fun close() {
        loadJob?.cancel()
        outputMetadata()

        kotlinx.coroutines.delay(100)

        returnFlow?.close()

        // Deletes the temp files
        processedImages.values.forEach { it.deleteIfExists() }

        processedImages.clear()
        display.children.clear()
    }

    private suspend fun outputMetadata() {
        if (returnFlow == null) {
            println("No return flow")
        } else {
            returnFlow.emit(jobs)
        }
    }

    // Wraps based on text metrics text width
    private fun wrapText(text: String, maxWidth: Double): List<String> {
        val lines = mutableListOf<String>()
        var currentWord = ""

        for (word in text.split(" ")) {
            val candidateWord: String

            if (currentWord.isEmpty()) {
                candidateWord = word
            } else {
                candidateWord = "$currentWord $word"
            }

            if (currentWord.isNotEmpty() && textMetrics.width(candidateWord) > maxWidth) {
                lines += currentWord
                currentWord = word
            } else {
                currentWord = candidateWord
            }
        }

        if (currentWord.isNotEmpty()) {
            lines += currentWord
        }

        return lines
    }

    private fun showErrors(errors: List<String>) {
        val padding = 20.0
        val rowHeight = 22.0
        val rowGap = 4.0
        val okButtonHeight = 36.0

        val messageWidth = 560.0
        val textWidth = messageWidth - 2 * padding

        // Firstly displays title and then each error (which are wrapped)
        // Wrapped lines are indented
        val lineTexts = buildList {
            add("Cannot Save Changes - Please Fix the Following:")
            errors.forEach { error ->
                wrapText("\u2022 $error", textWidth).forEachIndexed { i, line ->
                    add(if (i == 0) line else "    $line")
                }
            }
        }

        val textRows = lineTexts.map { line ->
            Label(line).apply { textAlignment = TextAlignment.Start }
        }

        val okButton = PushButton("OK")
        val allElements = textRows + okButton

        val messageHeight =
            padding +
                textRows.size * rowHeight +
                (textRows.size - 1) * rowGap +
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

                    messageRows.first().top eq padding

                    // Fit all rows in view successively
                    messageRows.zipWithNext().forEach { (above, below) ->
                        below.top eq above.bottom + rowGap
                    }

                    okButtonBounds.top eq messageRows.last().bottom + 16.0
                    okButtonBounds.centerX eq parent.centerX
                    okButtonBounds.width eq 80.0
                    okButtonBounds.height eq okButtonHeight
                }
        }

        okButton.fired += {
            popups.hide(dialog)
            setEditingEnabled(true)
        }

        setEditingEnabled(false)

        popups.show(dialog) {
            it.width eq messageWidth
            it.height eq messageHeight
            it.center eq parent.center
        }
    }

    // For disabling button and input field functionality when error popup is active
    private fun setEditingEnabled(enabled: Boolean) {
        errorDialogOpen = !enabled
        tableButtons.forEach { it.enabled = enabled }
        table.enabled = enabled
    }

    override fun shutdown() {}
}
