package au.edu.unimelb.archivetool.gui.segmentation

import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import io.nacular.doodle.core.View
import io.nacular.doodle.drawing.Canvas
import io.nacular.doodle.drawing.Color
import io.nacular.doodle.drawing.paint
import io.nacular.doodle.drawing.stroke
import io.nacular.doodle.event.PointerEvent
import io.nacular.doodle.event.PointerListener
import io.nacular.doodle.event.PointerMotionListener
import io.nacular.doodle.geometry.Point
import io.nacular.doodle.geometry.Rectangle
import io.nacular.doodle.image.Image
import io.nacular.doodle.image.ImageLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Represents a vertex in image-pixel space and only for proposed selection state. */
data class VertexDouble(val x: Double, val y: Double)

/**
 * The in-progress and unconfirmed edit state for the currently selected crop pair. "boxPoints" and
 * "labelPoints" are initially copied from the input job state. Is changed by the user dragging
 * selection. Saved to jobs only when "confirm()" is called.
 */
private class SelectionProposal(
    val number: Int,
    val boxPoints: MutableList<VertexDouble>,
    val labelPoints: MutableList<VertexDouble>,

    // Only true when the selection is a Create() so it can be deleted if never confirmed
    var isUnconfirmedCreation: Boolean,
)

class ImageViewport(
    private val jobsIn: Map<Path, JobFile?>,
    private val imageLoader: ImageLoader,
    private val appScope: CoroutineScope,
    private val onImageChanged: (index: Int, total: Int) -> Unit = { _, _ -> },
) : View() {
    companion object {
        private const val MIN_ZOOM = 0.02
        private const val MAX_ZOOM = 40.0
        private const val ZOOM_INTERVAL = 1.25
        private const val VERTEX_SIZE = 8.0
        private const val LINE_THICKNESS = 2.0
        private const val VERTEX_DETECTION_RADIUS = 10.0
    }

    // -- Job data ------------------------------------------------------------
    // need to maintain insertion order for create()
    private val jobs: LinkedHashMap<Path, JobFile> =
        LinkedHashMap<Path, JobFile>().apply {
            jobsIn.forEach { (path, job) -> put(path, job ?: JobFile()) }
        }
    private val paths: List<Path> = jobs.keys.toList()

    var currentIndex: Int = 0
        private set

    val totalImages: Int
        get() = paths.size

    private val currentJob: JobFile
        get() = jobs.getValue(paths[currentIndex])

    // -- Image display -------------------------------------------------------
    private var image: Image? = null
    private var imageWidth = 0.0
    private var imageHeight = 0.0

    // Pixels on screen for each pixel of the image
    var zoom: Double = 1.0

    // Position of image pixel (0,0) relative to this viewport
    private var originX = 0.0
    private var originY = 0.0

    // Marks if scaleToFit() should be called on startup
    private var didInitialFit = false

    // Guarantees each box crop retains its initial colouring
    private val pairColours = mutableMapOf<Int, Color>()

    // -- Selection -----------------------------------------------------------
    private var selection: SelectionProposal? = null

    // defines type of user drag input
    private sealed class DragMode {
        data object Panning : DragMode()

        data class VertexDragging(val isBox: Boolean, val index: Int) : DragMode()

        data object AreaDragging : DragMode()
    }

    private var dragMode: DragMode? = null

    // Original viewport position where mouse press started
    private var previousViewportX = 0.0
    private var previousViewportY = 0.0

    init {
        clipCanvasToBounds = true
        loadImage(paths[currentIndex])

        pointerChanged +=
            object : PointerListener {
                override fun pressed(event: PointerEvent) {
                    handlePressed(event)
                }

                override fun released(event: PointerEvent) {
                    dragMode = null
                }

                override fun exited(event: PointerEvent) {
                    dragMode = null
                }
            }

        pointerMotionChanged +=
            object : PointerMotionListener {
                override fun dragged(event: PointerEvent) {
                    handleDragged(event)
                }
            }

        loadImage(paths[currentIndex])
        onImageChanged(currentIndex, totalImages)
    }

    // -- Zoom ----------------------------------------------------------------

    fun zoomIn() {
        zoomBy(ZOOM_INTERVAL)
    }

    fun zoomOut() {
        zoomBy(1.0 / ZOOM_INTERVAL)
    }

    private fun zoomBy(factor: Double) {
        if (image == null) return

        val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val centerX = width / 2
        val centerY = height / 2

        // Retain image point at viewpoint's centre
        val imagePointX = (centerX - originX) / zoom
        val imagePointY = (centerY - originY) / zoom

        zoom = newZoom

        originX = centerX - imagePointX * zoom
        originY = centerY - imagePointY * zoom

        rerender()
    }

    fun scaleToFit() {
        if (image == null || imageWidth <= 0 || imageHeight <= 0) {
            return
        }

        if (width <= 0 || height <= 0) {
            return
        }

        val scaleX = width / imageWidth
        val scaleY = height / imageHeight

        zoom = min(scaleX, scaleY)

        val renderedWidth = imageWidth * zoom
        val renderedHeight = imageHeight * zoom

        originX = (width - renderedWidth) / 2
        originY = (height - renderedHeight) / 2

        didInitialFit = true

        rerender()
    }

    // -- Image Loading -------------------------------------------------------

    fun previous() {
        if (currentIndex <= 0) {
            return
        }

        discardUnconfirmedSelection()
        currentIndex--
        loadImage(paths[currentIndex])
        onImageChanged(currentIndex, totalImages)
    }

    fun next() {
        if (currentIndex >= paths.size - 1) {
            return
        }

        discardUnconfirmedSelection()
        currentIndex++
        loadImage(paths[currentIndex])
        onImageChanged(currentIndex, totalImages)
    }

    private fun loadImage(path: Path) {
        image = null
        didInitialFit = false

        appScope.launch {
            val loaded = imageLoader.load(path.toDataUri()) ?: return@launch
            image = loaded
            imageWidth = loaded.size.width
            imageHeight = loaded.size.height

            if (width > 0 && height > 0) {
                scaleToFit()
            }

            rerender()
        }
    }

    // -- Review --------------------------------------------------------------

    fun confirm() {
        selection?.let { selection ->
            val crops = currentJob.crops
            val oldBox = crops["b${selection.number}"] as? JobFile.BoxCrop
            val oldLabel = crops["l${selection.number}"] as? JobFile.LabelCrop

            crops["b${selection.number}"] =
                JobFile.BoxCrop(
                    points = selection.boxPoints.toRectangle(),
                    transcription = oldBox?.transcription,
                    confidence = oldBox?.confidence ?: 1.0,
                )
            crops["l${selection.number}"] =
                JobFile.LabelCrop(
                    points = selection.labelPoints.toRectangle(),
                    transcription = oldLabel?.transcription,
                    confidence = oldLabel?.confidence ?: 1.0,
                )

            selection.isUnconfirmedCreation = false
        }

        // Output for testing
        printCrops()

        selection = null
        dragMode = null

        rerender()
    }

    fun create() {
        // Deletes unconfirmed created crop pair if a new one is created
        selection?.let { selection ->
            if (selection.isUnconfirmedCreation) {
                currentJob.crops.remove("b${selection.number}")
                currentJob.crops.remove("l${selection.number}")
            }
        }

        selection = null

        val number = nextAvailableIndex(currentJob)
        val centreX = imageWidth / 2.0
        val centreY = imageHeight / 2.0
        val boxWidth = 600.0
        val boxHeight = 1100.0
        val boxTop = centreY - boxHeight / 2
        val boxLeft = centreX - boxWidth / 2

        val boxPoints =
            mutableListOf(
                VertexDouble(boxLeft, boxTop),
                VertexDouble(boxLeft + boxWidth, boxTop),
                VertexDouble(boxLeft + boxWidth, boxTop + boxHeight),
                VertexDouble(boxLeft, boxTop + boxHeight),
            )

        val labelWidth = 500.0
        val labelHeight = 170.0
        val labelTop = boxTop + 35.0
        val labelLeft = centreX - labelWidth / 2
        val labelPoints =
            mutableListOf(
                VertexDouble(labelLeft, labelTop),
                VertexDouble(labelLeft + labelWidth, labelTop),
                VertexDouble(labelLeft + labelWidth, labelTop + labelHeight),
                VertexDouble(labelLeft, labelTop + labelHeight),
            )

        // Adds created crop to current job at full confidence
        currentJob.crops["b$number"] =
            JobFile.BoxCrop(points = boxPoints.toRectangle(), confidence = 1.0)
        currentJob.crops["l$number"] =
            JobFile.LabelCrop(points = labelPoints.toRectangle(), confidence = 1.0)

        selection = SelectionProposal(number, boxPoints, labelPoints, isUnconfirmedCreation = true)

        rerender()
    }

    fun delete() {
        val s = selection ?: return

        currentJob.crops.remove("b${s.number}")
        currentJob.crops.remove("l${s.number}")

        selection = null
        dragMode = null

        rerender()
    }

    // This function is for testing
    private fun printCrops() {
        println("Crops for ${paths[currentIndex]}:")
        currentJob.crops.entries
            .sortedBy { it.key }
            .forEach { (id, crop) ->
                val pts =
                    crop.points.inner.joinToString(prefix = "[", postfix = "]") {
                        "(${it.first},${it.second})"
                    }
                println("  $id: $pts  confidence = ${crop.confidence}")
            }
    }

    private fun nextAvailableIndex(job: JobFile): Int {
        var n = 1
        while (job.crops.containsKey("b$n") || job.crops.containsKey("l$n")) {
            n++
        }

        return n
    }

    private fun discardUnconfirmedSelection() {
        val s = selection ?: return

        if (s.isUnconfirmedCreation) {
            currentJob.crops.remove("b${s.number}")
            currentJob.crops.remove("l${s.number}")
        }

        selection = null
    }

    // -- Selection -----------------------------------------------------------

    private fun handlePressed(event: PointerEvent) {
        // Current mouse position in viewport coordinates
        val selectionX = event.location.x
        val selectionY = event.location.y

        previousViewportX = selectionX
        previousViewportY = selectionY

        // Convert mouse position to image coordinates
        val imgPoint = toImageSpace(selectionX, selectionY)

        val hadSelection = selection != null

        selection?.let { selection ->
            // Assigns current user drag to moving selected vertex
            selectedVertex(selection, selectionX, selectionY)?.let { (isBox, index) ->
                dragMode = DragMode.VertexDragging(isBox, index)
                return
            }

            // Assigns current user drag to moving crop pair
            if (
                pointInQuadrilateral(imgPoint, selection.boxPoints) ||
                    pointInQuadrilateral(imgPoint, selection.labelPoints)
            ) {
                dragMode = DragMode.AreaDragging
                return
            }

            // Discards selection is user clicks elsewhere
            discardUnconfirmedSelection()
        }

        val foundNumber = findCropAtPoint(imgPoint)
        dragMode =
            // Standard selection
            if (foundNumber != null) {
                selectPair(foundNumber)
                DragMode.AreaDragging
            }
            // Deselect mouse click should not become pan
            else if (hadSelection) {
                null
            } else {
                DragMode.Panning
            }

        rerender()
    }

    // Defines logic for each dragging state
    private fun handleDragged(event: PointerEvent) {
        val selectionX = event.location.x
        val selectionY = event.location.y

        when (val mode = dragMode) {
            is DragMode.Panning -> {
                originX += (selectionX - previousViewportX)
                originY += (selectionY - previousViewportY)
            }

            is DragMode.VertexDragging -> {
                selection?.let { selection ->
                    val imgPoint = toImageSpace(selectionX, selectionY)
                    val clampedToViewport =
                        VertexDouble(
                            imgPoint.x.coerceIn(0.0, imageWidth),
                            imgPoint.y.coerceIn(0.0, imageHeight),
                        )

                    val list =
                        if (mode.isBox) {
                            selection.boxPoints
                        } else {
                            selection.labelPoints
                        }

                    list[mode.index] = clampedToViewport
                }
            }

            is DragMode.AreaDragging -> {
                selection?.let { selection ->
                    val imageX = (selectionX - previousViewportX) / zoom
                    val imageY = (selectionY - previousViewportY) / zoom

                    // Group the crop pair and restrict to viewport bounds
                    val all = selection.boxPoints + selection.labelPoints
                    val minX = all.minOf { it.x }
                    val maxX = all.maxOf { it.x }
                    val minY = all.minOf { it.y }
                    val maxY = all.maxOf { it.y }
                    val clampedMovementX = imageX.coerceIn(-minX, imageWidth - maxX)
                    val clampedMovementY = imageY.coerceIn(-minY, imageHeight - maxY)

                    // Move all vertexes
                    for (i in selection.boxPoints.indices) {
                        selection.boxPoints[i] =
                            VertexDouble(
                                selection.boxPoints[i].x + clampedMovementX,
                                selection.boxPoints[i].y + clampedMovementY,
                            )
                    }
                    for (i in selection.labelPoints.indices) {
                        selection.labelPoints[i] =
                            VertexDouble(
                                selection.labelPoints[i].x + clampedMovementX,
                                selection.labelPoints[i].y + clampedMovementY,
                            )
                    }
                }
            }

            null -> {}
        }

        // On drag release
        previousViewportX = selectionX
        previousViewportY = selectionY
        rerender()
    }

    private fun findCropAtPoint(pt: VertexDouble): Int? {
        val job = currentJob
        // checks all crop pairs that image contains
        val numbers =
            job.crops.keys
                .mapNotNull {
                    Regex("""^[bl](\d+)$""").find(it)?.groupValues?.get(1)?.toIntOrNull()
                }
                .distinct()

        for (n in numbers) {
            val box = job.crops["b$n"] as? JobFile.BoxCrop
            val label = job.crops["l$n"] as? JobFile.LabelCrop

            if (box != null && pointInQuadrilateral(pt, box.points.toVertices())) {
                return n
            }
            if (label != null && pointInQuadrilateral(pt, label.points.toVertices())) {
                return n
            }
        }

        return null
    }

    private fun selectPair(n: Int) {
        val job = currentJob
        val box = (job.crops["b$n"] as? JobFile.BoxCrop)?.points?.toVertices() ?: mutableListOf()
        val label =
            (job.crops["l$n"] as? JobFile.LabelCrop)?.points?.toVertices() ?: mutableListOf()

        selection = SelectionProposal(n, box, label, isUnconfirmedCreation = false)
    }

    // Returns if mouse point is inside crop area
    private fun pointInQuadrilateral(p: VertexDouble, quad: List<VertexDouble>): Boolean {
        var isInside = false
        var j = quad.size - 1

        for (i in quad.indices) {
            val pi = quad[i]
            val pj = quad[j]
            if ((pi.y > p.y) != (pj.y > p.y)) {
                val xIntersect = pj.x + (p.y - pj.y) / (pj.y - pi.y) * (pi.x - pj.x)
                if (p.x < xIntersect) {
                    isInside = !isInside
                }
            }

            j = i
        }

        return isInside
    }

    // returns exact vertex of a crop pair the user interacts with
    private fun selectedVertex(
        selection: SelectionProposal,
        selectionX: Double,
        selectionY: Double,
    ): Pair<Boolean, Int>? {
        selection.boxPoints.forEachIndexed { i, vertex ->
            if (distance(toScreen(vertex), selectionX, selectionY) <= VERTEX_DETECTION_RADIUS) {
                return true to i
            }
        }

        selection.labelPoints.forEachIndexed { i, vertex ->
            if (distance(toScreen(vertex), selectionX, selectionY) <= VERTEX_DETECTION_RADIUS) {
                return false to i
            }
        }

        return null
    }

    private fun distance(p: Point, x: Double, y: Double): Double {
        val dx = p.x - x
        val dy = p.y - y
        return sqrt(dx * dx + dy * dy)
    }

    // -- Helper Converters ---------------------------------------------------

    private fun au.edu.unimelb.archivetool.util.geometry.Rectangle.toVertices():
        MutableList<VertexDouble> {
        return inner.map { VertexDouble(it.first.toDouble(), it.second.toDouble()) }.toMutableList()
    }

    private fun Path.toDataUri(): String {
        val bytes = Files.readAllBytes(this)
        val mime = Files.probeContentType(this) ?: "image/jpeg"
        val encoded = Base64.getEncoder().encodeToString(bytes)
        return "data:$mime;base64,$encoded"
    }

    private fun toImageSpace(screenX: Double, screenY: Double): VertexDouble {
        return VertexDouble((screenX - originX) / zoom, (screenY - originY) / zoom)
    }

    // Takes in singular point of crop and scales and returns it as Doodle Point object
    private fun toScreen(vertexDouble: VertexDouble): Point {
        return Point(originX + vertexDouble.x * zoom, originY + vertexDouble.y * zoom)
    }

    private fun List<VertexDouble>.toRectangle():
        au.edu.unimelb.archivetool.util.geometry.Rectangle =
        au.edu.unimelb.archivetool.util.geometry.Rectangle.create(
            *map { it.x.roundToInt() to it.y.roundToInt() }.toTypedArray()
        )

    private fun indexColour(index: Int): Color {
        return pairColours.getOrPut(index) {
            Color(
                Random.nextInt(256).toUByte(),
                Random.nextInt(256).toUByte(),
                Random.nextInt(256).toUByte(),
            )
        }
    }

    // -- Rendering -----------------------------------------------------------

    override fun render(canvas: Canvas) {
        // Fits image to viewport if image finished loading before view had been formatted
        if (!didInitialFit && image != null && width > 0 && height > 0) {
            scaleToFit()
        }

        canvas.rect(bounds.atOrigin, fill = Color.Lightgray.paint)

        image?.let { img ->
            canvas.image(
                img,
                destination =
                    Rectangle(
                        x = originX,
                        y = originY,
                        width = imageWidth * zoom,
                        height = imageHeight * zoom,
                    ),
            )
        }

        val job = currentJob
        val selectedNumber = selection?.number
        val numbers =
            job.crops.keys
                .mapNotNull {
                    Regex("""^[bl](\d+)$""").find(it)?.groupValues?.get(1)?.toIntOrNull()
                }
                .distinct()

        // Draw outlines for every crop
        for (n in numbers) {
            if (n == selectedNumber) {
                continue
            }

            val boxStrokeColour = indexColour(n)
            (job.crops["b$n"] as? JobFile.BoxCrop)?.let {
                drawOutline(canvas, it.points.toVertices(), boxStrokeColour)
            }
            (job.crops["l$n"] as? JobFile.LabelCrop)?.let {
                drawOutline(canvas, it.points.toVertices(), Color.Blue)
            }
        }

        // Draw vertexes for crop pair if selected
        selection?.let { sel ->
            drawVertexes(canvas, sel.boxPoints)
            drawVertexes(canvas, sel.labelPoints)
        }
    }

    private fun drawOutline(canvas: Canvas, vertexes: List<VertexDouble>, colour: Color) {
        val points = vertexes.map { toScreen(it) }
        val stroke = colour.paint.stroke(thickness = LINE_THICKNESS)
        for (i in points.indices) {
            canvas.line(points[i], points[(i + 1) % points.size], stroke = stroke)
        }
    }

    private fun drawVertexes(canvas: Canvas, vertexes: List<VertexDouble>) {
        vertexes.forEach { v ->
            val point = toScreen(v)
            canvas.rect(
                Rectangle(
                    x = point.x - VERTEX_SIZE / 2,
                    y = point.y - VERTEX_SIZE / 2,
                    width = VERTEX_SIZE,
                    height = VERTEX_SIZE,
                ),
                fill = Color.White.paint,
                stroke = Color.Blue.paint.stroke(thickness = 1.0),
            )
        }
    }
}
