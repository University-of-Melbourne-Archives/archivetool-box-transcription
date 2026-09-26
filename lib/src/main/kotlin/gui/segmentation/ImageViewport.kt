package au.edu.unimelb.archivetool.gui.segmentation

import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.util.geometry.Point
import au.edu.unimelb.archivetool.util.geometry.Point.Companion.toPoint
import au.edu.unimelb.archivetool.util.geometry.Polygon
import au.edu.unimelb.archivetool.util.geometry.Quad
import au.edu.unimelb.archivetool.util.geometry.Rectangle
import io.nacular.doodle.core.View
import io.nacular.doodle.drawing.Canvas
import io.nacular.doodle.drawing.Color
import io.nacular.doodle.drawing.HslColor
import io.nacular.doodle.drawing.opacity
import io.nacular.doodle.drawing.paint
import io.nacular.doodle.drawing.stroke
import io.nacular.doodle.drawing.toRgb
import io.nacular.doodle.event.PointerEvent
import io.nacular.doodle.event.PointerListener
import io.nacular.doodle.event.PointerMotionListener
import io.nacular.doodle.geometry.ConvexPolygon
import io.nacular.doodle.geometry.Point as DoodlePoint
import io.nacular.doodle.geometry.Rectangle as DoodleRectangle
import io.nacular.doodle.image.Image
import io.nacular.doodle.image.ImageLoader
import io.nacular.measured.units.Angle.Companion.degrees
import io.nacular.measured.units.times
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.math.min
import kotlin.random.Random
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Represents a vertex in image-pixel space and only for proposed selection state. */
data class VertexDouble(val x: Double, val y: Double)

/**
 * The in-progress and unconfirmed edit state for the currently selected crop pair. "boxPoints" and
 * "labelPoints" are initially copied from the input job state. Is changed by the user dragging
 * selection. Saved to jobs only when "confirm()" is called.
 */
private data class SelectionProposal(
    val key: String,
    val boxPoints: Quad,
    val labelPoints: Quad,

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
        private val SELECTION_FILL = Color.Cyan.opacity(0.15f)
        private val HSL_BLUE = HslColor(240 * degrees, 1f, 0.50f)
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
        get() = jobs[paths[currentIndex]]!!

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
    private val pairColours = mutableMapOf<String, HslColor>()

    // -- Selection -----------------------------------------------------------
    private var selection: SelectionProposal? = null

    // defines type of user drag input
    private sealed class DragMode {
        data object Panning : DragMode()

        data class VertexDragging(val isBox: Boolean, val index: Int) : DragMode()

        data object AreaDragging : DragMode()

        data object LabelDragging : DragMode()
    }

    private var dragMode: DragMode? = null

    // For distinguishing panning from deselection
    private var pendingDeselectOnClick = false

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
                    if (pendingDeselectOnClick) {
                        discardUnconfirmedSelection()
                        rerender()
                    }

                    pendingDeselectOnClick = false
                    dragMode = null
                }

                override fun exited(event: PointerEvent) {
                    pendingDeselectOnClick = false
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
        if (image == null || imageWidth <= 0 || imageHeight <= 0 || width <= 0 || height <= 0) {
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
            val crops = currentJob.vertexes!!
            val oldContainer = crops[selection.key]
            val oldBox = oldContainer?.boxBoundary
            // TODO Allow multiple labels
            val oldLabel = oldContainer?.labelBoundaries?.first()

            currentJob.vertexes!![selection.key] =
                oldContainer?.copy(
                    boxBoundary =
                        oldBox?.copy(points = Quad.upcast(selection.boxPoints))
                            ?: Boundary(
                                points = Quad.upcast(selection.boxPoints),
                                confidence = 1.0,
                            ),
                    labelBoundaries =
                        listOf(
                            oldLabel?.copy(points = Quad.upcast(selection.boxPoints))
                                ?: Boundary(
                                    points = Quad.upcast(selection.boxPoints),
                                    confidence = 1.0,
                                )
                        ),
                )
                    ?: ArchiveBox(
                        Boundary(points = Quad.upcast(selection.boxPoints), confidence = 1.0),
                        listOf(
                            Boundary(points = Quad.upcast(selection.boxPoints), confidence = 1.0)
                        ),
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
                currentJob.vertexes!!.remove(selection.key)
            }
        }

        val centreX = imageWidth / 2
        val centreY = imageHeight / 2
        val boxWidth = 600
        val boxHeight = 1100
        val boxTop = (centreY - boxHeight / 2).toInt()
        val boxLeft = (centreX - boxWidth / 2).toInt()

        val box = Rectangle.create(boxLeft, boxTop, boxWidth, boxHeight)

        val labelWidth = 500
        val labelHeight = 170
        val labelTop = boxTop + 35
        val labelLeft = (centreX - labelWidth / 2).toInt()
        val label = Rectangle.create(labelLeft, labelTop, labelWidth, labelHeight)

        val key = Uuid.random().toHexDashString()

        // Adds created crop to current job at full confidence
        currentJob.vertexes!![key] =
            ArchiveBox(
                boxBoundary = Boundary(box, 1.0),
                labelBoundaries = listOf(Boundary(label, 1.0)),
            )

        selection = SelectionProposal(key, box, label, isUnconfirmedCreation = true)

        rerender()
    }

    fun delete() {
        selection =
            null.also {
                selection?.let { s ->
                    currentJob.vertexes!!.remove(s.key)
                    dragMode = null
                }
            }

        rerender()
    }

    // This function is for testing
    private fun printCrops() {
        println("Crops for ${paths[currentIndex]}:")

        currentJob.vertexes!!
            .entries
            .sortedBy { it.key }
            .forEach { (id, crops) ->
                val pts =
                    crops.keyedBoundaries.entries.joinToString(prefix = "[", postfix = "]") { (k, v)
                        ->
                        "$k: (${v.points.vertices.joinToString {(x, y) -> 
                            "($x, $y)" 
                        }}), confidence = ${v.confidence}"
                    }

                println("  $id: $pts")
            }
    }

    private fun discardUnconfirmedSelection() {
        selection =
            null.also {
                selection?.let { s ->
                    if (s.isUnconfirmedCreation) {
                        currentJob.vertexes!!.remove(s.key)
                    }
                }
            }
    }

    // -- Selection -----------------------------------------------------------

    private fun handlePressed(event: PointerEvent) {
        // Current mouse position in viewport coordinates
        val selectionX = event.location.x
        val selectionY = event.location.y

        previousViewportX = selectionX
        previousViewportY = selectionY

        pendingDeselectOnClick = false

        // Convert mouse position to image coordinates
        val imgPoint = DoodlePoint(selectionX, selectionY).toPoint()

        selection?.let { selection ->
            selectedVertex(selection, selectionX, selectionY)?.let { (isBox, index) ->
                dragMode = DragMode.VertexDragging(isBox, index)
                return
            }

            if (selection.labelPoints.containsPoint(imgPoint)) {
                dragMode = DragMode.LabelDragging
                return
            }

            if (selection.boxPoints.containsPoint(imgPoint)) {
                dragMode = DragMode.AreaDragging
                return
            }
        }

        // All selection interactions
        findCropAtPoint(imgPoint)?.let { key ->
            discardUnconfirmedSelection()
            selectPair(key)
            dragMode = DragMode.AreaDragging
            rerender()
            return
        }

        // Allow pan but a deselect mouse click should not become pan
        pendingDeselectOnClick = (selection != null)
        dragMode = DragMode.Panning
    }

    // Defines logic for each dragging state
    private fun handleDragged(event: PointerEvent) {
        val selectionX = event.location.x
        val selectionY = event.location.y

        when (val mode = dragMode) {
            is DragMode.Panning -> {
                val changeX = selectionX - previousViewportX
                val changeY = selectionY - previousViewportY

                if (changeX != 0.0 || changeY != 0.0) {
                    pendingDeselectOnClick = false
                }

                originX += changeX
                originY += changeY
            }

            is DragMode.VertexDragging -> {
                selection?.let { s ->
                    val imgPoint = toImageSpace(selectionX, selectionY).toPoint()
                    val clampedToViewport =
                        imgPoint.clamp(0 to 0, imageWidth.toInt() to imageHeight.toInt())

                    selection =
                        if (mode.isBox) {
                            s.copy(
                                boxPoints = s.boxPoints.replaceIndex(mode.index, clampedToViewport)
                            )
                        } else {
                            s.copy(
                                labelPoints =
                                    s.labelPoints.replaceIndex(mode.index, clampedToViewport)
                            )
                        }
                }
            }

            is DragMode.AreaDragging -> {
                selection?.let { s ->
                    val imageX = (selectionX - previousViewportX) / zoom
                    val imageY = (selectionY - previousViewportY) / zoom

                    // Group the crop pair and restrict to viewport bounds
                    val all =
                        Polygon.create(
                            *(s.boxPoints.vertices.toList() + s.labelPoints.vertices)
                                .map { it.inner }
                                .toTypedArray()
                        )

                    val clampedMovementX =
                        imageX.coerceInBounds((-all.xMin).toDouble(), imageWidth - all.xMax)
                    val clampedMovementY =
                        imageY.coerceInBounds((-all.yMin).toDouble(), imageHeight - all.yMax)

                    // Move all vertexes
                    val box =
                        s.boxPoints.offsetBy(clampedMovementX.toInt(), clampedMovementY.toInt())
                    val label =
                        s.labelPoints.offsetBy(clampedMovementX.toInt(), clampedMovementY.toInt())

                    selection = s.copy(boxPoints = box, labelPoints = label)
                }
            }

            is DragMode.LabelDragging -> {
                selection?.let { s ->
                    val imageX = (selectionX - previousViewportX) / zoom
                    val imageY = (selectionY - previousViewportY) / zoom

                    val clampedMovementX =
                        imageX
                            .toInt()
                            .coerceIn(
                                s.boxPoints.xMin - s.labelPoints.xMin,
                                s.boxPoints.xMax - s.labelPoints.xMax,
                            )
                    val clampedMovementY =
                        imageY
                            .toInt()
                            .coerceIn(
                                s.boxPoints.yMin - s.labelPoints.yMin,
                                s.boxPoints.yMax - s.labelPoints.yMax,
                            )

                    selection =
                        s.copy(
                            labelPoints = s.labelPoints.offsetBy(clampedMovementX, clampedMovementY)
                        )
                }
            }

            null -> {}
        }

        // On drag release
        previousViewportX = selectionX
        previousViewportY = selectionY
        rerender()
    }

    private fun findCropAtPoint(pt: Point): String? {
        val job = currentJob
        // checks all crop pairs that image contains
        val keys = job.vertexes!!.keys

        for (k in keys) {
            val container = job.vertexes[k]!!
            val box = container.boxBoundary
            // TODO: We need to handle multiple labels
            val label = container.labelBoundaries.firstOrNull()

            if (box.points.containsPoint(pt)) {
                return k
            }
            if (label != null && label.points.containsPoint(pt)) {
                return k
            }
        }

        return null
    }

    private fun selectPair(k: String) {
        val job = currentJob.vertexes!![k]!!
        val box = job.boxBoundary.points

        val label =
            job.labelBoundaries.firstOrNull()?.points
                ?: Rectangle.create(box.xMin + 35, box.yMin + 35, 500, 170)

        selection = SelectionProposal(k, box, label, isUnconfirmedCreation = false)
    }

    // returns exact vertex of a crop pair the user interacts with
    private fun selectedVertex(
        selection: SelectionProposal,
        selectionX: Double,
        selectionY: Double,
    ): Pair<Boolean, Int>? {
        selection.boxPoints.vertices.forEachIndexed { i, vertex ->
            if (
                toScreen(vertex.toDoodlePoint())
                    .toPoint()
                    .distanceTo(selectionX.toInt() to selectionY.toInt()) <= VERTEX_DETECTION_RADIUS
            ) {
                return true to i
            }
        }

        selection.labelPoints.vertices.forEachIndexed { i, vertex ->
            if (
                toScreen(vertex.toDoodlePoint())
                    .toPoint()
                    .distanceTo(selectionX.toInt() to selectionY.toInt()) <= VERTEX_DETECTION_RADIUS
            ) {
                return true to i
            }
        }

        return null
    }

    private fun Double.coerceInBounds(lowPoint: Double, highPoint: Double): Double {
        return if (lowPoint <= highPoint) {
            coerceIn(lowPoint, highPoint)
        } else {
            this
        }
    }

    // -- Helper Converters ---------------------------------------------------

    private fun Path.toDataUri(): String {
        val bytes = Files.readAllBytes(this)
        val mime = Files.probeContentType(this) ?: "image/jpeg"
        val encoded = Base64.getEncoder().encodeToString(bytes)
        return "data:$mime;base64,$encoded"
    }

    private fun toImageSpace(screenX: Double, screenY: Double): DoodlePoint {
        return DoodlePoint((screenX - originX) / zoom, (screenY - originY) / zoom)
    }

    // Takes in singular point of crop and scales and returns it as Doodle Point object
    private fun toScreen(vertexDouble: DoodlePoint): DoodlePoint {
        return DoodlePoint(originX + vertexDouble.x * zoom, originY + vertexDouble.y * zoom)
    }

    private fun indexColour(index: String): HslColor {
        return pairColours.getOrPut(index) { HslColor(Random.nextInt(360) * degrees, 1f, 0.5f) }
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
                    DoodleRectangle(
                        x = originX,
                        y = originY,
                        width = imageWidth * zoom,
                        height = imageHeight * zoom,
                    ),
            )
        }

        val job = currentJob
        val selectedKey = selection?.key

        // Draw outlines for every crop
        job.vertexes!!.entries.forEach { (k, v) ->
            if (k == selectedKey) {
                return@forEach
            }

            val boxStrokeColour = indexColour(k)
            drawOutline(canvas, v.boxBoundary.points, boxStrokeColour)
            v.labelBoundaries.firstOrNull()?.let { drawOutline(canvas, it.points, HSL_BLUE) }
        }

        // Draw vertexes for crop pair if selected
        selection?.let { sel ->
            val boxStrokeColour = indexColour(sel.key)
            drawFilledSelection(canvas, sel.boxPoints, boxStrokeColour)
            drawFilledSelection(canvas, sel.labelPoints, HSL_BLUE)
            drawVertexes(canvas, sel.boxPoints)
            drawVertexes(canvas, sel.labelPoints)
        }
    }

    private fun drawOutline(canvas: Canvas, poly: Polygon, colour: HslColor) {
        val points = poly.vertices.map { toScreen(it.toDoodlePoint()) }
        val stroke = colour.toRgb().paint.stroke(thickness = LINE_THICKNESS)
        for (i in points.indices) {
            canvas.line(points[i], points[(i + 1) % points.size], stroke = stroke)
        }
    }

    private fun drawFilledSelection(canvas: Canvas, poly: Quad, outlineColor: HslColor) {
        val screenPoly = poly.vertices.map { toScreen(it.toDoodlePoint()) }

        val vertexIterator = screenPoly.take(3).iterator()
        val remaining = screenPoly.subList(3, poly.vertices.size).toTypedArray()

        canvas.poly(
            ConvexPolygon.Companion(
                vertexIterator.next(),
                vertexIterator.next(),
                vertexIterator.next(),
                *remaining,
            ),
            fill = SELECTION_FILL.paint,
        )

        val stroke = outlineColor.toRgb().paint.stroke(thickness = LINE_THICKNESS)
        for (i in screenPoly.indices) {
            canvas.line(screenPoly[i], screenPoly[(i + 1) % screenPoly.size], stroke = stroke)
        }
    }

    private fun drawVertexes(canvas: Canvas, poly: Polygon) {
        poly.vertices.forEach { v ->
            val point = toScreen(v.toDoodlePoint())
            canvas.rect(
                DoodleRectangle(
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
