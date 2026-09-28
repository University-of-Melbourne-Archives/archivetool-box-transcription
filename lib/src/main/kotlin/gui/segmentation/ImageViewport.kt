package au.edu.unimelb.archivetool.gui.segmentation

import au.edu.unimelb.archivetool.gui.segmentation.Coord.ViewportSpaceCoord
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.Colour
import au.edu.unimelb.archivetool.util.HSLColour
import au.edu.unimelb.archivetool.util.MutableWaitFlow
import au.edu.unimelb.archivetool.util.extension.PairExt.any
import au.edu.unimelb.archivetool.util.math.geometry.Point
import au.edu.unimelb.archivetool.util.math.geometry.Polygon
import au.edu.unimelb.archivetool.util.math.geometry.Quad
import au.edu.unimelb.archivetool.util.math.geometry.Rectangle
import au.edu.unimelb.archivetool.util.wrap.Doodle
import io.nacular.doodle.core.View
import io.nacular.doodle.drawing.Canvas
import io.nacular.doodle.drawing.stroke
import io.nacular.doodle.event.PointerEvent
import io.nacular.doodle.event.PointerListener
import io.nacular.doodle.event.PointerMotionListener
import io.nacular.doodle.geometry.ConvexPolygon
import io.nacular.doodle.image.Image
import io.nacular.doodle.image.ImageLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.encoding.Base64.Default as Base64
import kotlin.io.path.readBytes
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.properties.Delegates.observable
import kotlin.random.Random
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed interface Coord {
    fun imageSpace(origin: ViewportSpaceCoord, zoom: Double): ImageSpaceCoord

    fun viewportSpace(origin: ViewportSpaceCoord, zoom: Double): ViewportSpaceCoord

    fun toDoodlePoint(origin: ViewportSpaceCoord, zoom: Double): Doodle.Point =
        viewportSpace(origin, zoom).point.toDoodle()

    data class ImageSpaceCoord(val point: Point) : Coord {
        constructor(x: Int, y: Int) : this(Point.create(x, y))

        val x by point::x
        val y by point::y

        override fun imageSpace(origin: ViewportSpaceCoord, zoom: Double) = this

        override fun viewportSpace(origin: ViewportSpaceCoord, zoom: Double): ViewportSpaceCoord =
            ViewportSpaceCoord(origin.point + (point * zoom))

        companion object {
            fun construct(pair: Pair<Int, Int>) = ImageSpaceCoord(Point.fromPair(pair))
        }
    }

    data class ViewportSpaceCoord(val point: Point) : Coord {
        constructor(x: Int, y: Int) : this(Point.create(x, y))

        val x by point::x
        val y by point::y

        override fun imageSpace(origin: ViewportSpaceCoord, zoom: Double): ImageSpaceCoord =
            ImageSpaceCoord((point - origin.point) / zoom)

        override fun viewportSpace(origin: ViewportSpaceCoord, zoom: Double): ViewportSpaceCoord =
            this

        companion object {
            fun construct(pair: Pair<Int, Int>) = ViewportSpaceCoord(Point.fromPair(pair))
        }
    }
}

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
    private val jobsIn: Map<JobRelativePath, JobFile?>,
    private val returnFlow: MutableWaitFlow<Map<JobRelativePath, JobFile>>?,
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
        private val SELECTION_FILL = Colour.CYAN.withAlpha(38u)
    }

    // -- Job data ------------------------------------------------------------
    // need to maintain insertion order for create()
    private val jobs: LinkedHashMap<JobRelativePath, JobFile> =
        LinkedHashMap<JobRelativePath, JobFile>().apply {
            jobsIn.forEach { (path, job) -> put(path, job ?: JobFile()) }
        }
    private val paths: List<JobRelativePath> = jobs.keys.toList()

    var currentIndex: Int = 0
        private set(value) {
            field = value
            loadImage(paths[value])
        }

    val totalImages: Int by paths::size

    private val currentJob: JobFile
        get() = jobs[paths[currentIndex]]!!

    private val changed: MutableMap<JobRelativePath, JobFile> = mutableMapOf()

    // -- Image display -------------------------------------------------------
    private var image: Image? by
        observable(null) { _, _, new -> new?.let { onImageChanged(currentIndex, totalImages) } }
    private val imageWidth
        get() = image?.size?.width ?: 0.0

    private val imageHeight
        get() = image?.size?.height ?: 0.0

    // Pixels on screen for each pixel of the image
    var zoom: Double = 1.0

    // Position of image pixel (0,0) relative to this viewport
    private var origin = ViewportSpaceCoord(0, 0)

    // Marks if scaleToFit() should be called on startup
    private var didInitialFit = false

    // Guarantees each box crop retains its initial colouring
    private val pairColours = mutableMapOf<String, Colour>()

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
    private var previousViewport = ViewportSpaceCoord(0, 0)

    init {
        clipCanvasToBounds = true

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

        currentIndex = currentIndex
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
        val center = ViewportSpaceCoord((width / 2).roundToInt(), (height / 2).roundToInt())

        // Retain image point at viewpoint's centre
        val imagePoint = center.imageSpace(origin, zoom)

        zoom = newZoom

        origin = ViewportSpaceCoord((center.point - imagePoint.point * zoom))

        rerender()
    }

    fun scaleToFit() {
        if (image == null || minOf(imageWidth, imageHeight, width, height) <= 0) {
            return
        }

        val scaleX = width / imageWidth
        val scaleY = height / imageHeight

        zoom = min(scaleX, scaleY)

        val renderedWidth = imageWidth * zoom
        val renderedHeight = imageHeight * zoom

        origin =
            ViewportSpaceCoord(
                ((width - renderedWidth) / 2).roundToInt(),
                ((height - renderedHeight) / 2).roundToInt(),
            )

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

        changed[paths[currentIndex]] = JobFile(vertexes = currentJob.vertexes, approved = false)
    }

    fun next() {
        if (currentIndex >= totalImages - 1) {
            return
        }

        discardUnconfirmedSelection()

        changed[paths[currentIndex]] = JobFile(vertexes = currentJob.vertexes, approved = true)

        currentIndex++
    }

    private fun loadImage(path: JobRelativePath) {
        image = null
        didInitialFit = false

        appScope.launch {
            val loaded = imageLoader.load(path.absolute.toDataUri()) ?: return@launch

            image = loaded

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
                            oldLabel?.copy(points = Quad.upcast(selection.labelPoints))
                                ?: Boundary(
                                    points = Quad.upcast(selection.labelPoints),
                                    confidence = 1.0,
                                )
                        ),
                )
                    ?: ArchiveBox(
                        Boundary(points = Quad.upcast(selection.boxPoints), confidence = 1.0),
                        listOf(
                            Boundary(points = Quad.upcast(selection.labelPoints), confidence = 1.0)
                        ),
                    )

            selection.isUnconfirmedCreation = false

            changed[paths[currentIndex]] = currentJob
        }

        CoroutineScope(Dispatchers.Default).launch { outputCrops() }

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

        val centre =
            Coord.ImageSpaceCoord((imageWidth / 2).roundToInt(), (imageHeight / 2).roundToInt())
        val boxWidth = 600
        val boxHeight = 1100
        val boxTop = (centre.y - boxHeight / 2)
        val boxLeft = (centre.x - boxWidth / 2)

        val box = Rectangle.create(boxLeft, boxTop, boxWidth, boxHeight)

        val labelWidth = 500
        val labelHeight = 170
        val labelTop = boxTop + 35
        val labelLeft = (centre.x - labelWidth / 2)
        val label = Rectangle.create(labelLeft, labelTop, labelWidth, labelHeight)

        val key = Uuid.random().toHexDashString()

        // Adds created crop to current job at full confidence
        currentJob.vertexes!![key] =
            ArchiveBox(
                boxBoundary = Boundary(box, 1.0),
                labelBoundaries = listOf(Boundary(label, 1.0)),
            )

        selection = SelectionProposal(key, box, label, isUnconfirmedCreation = true)

        changed[paths[currentIndex]] = currentJob

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

    suspend fun close() {
        changed[paths[currentIndex]] = JobFile(vertexes = currentJob.vertexes, approved = true)
        outputCrops()
        returnFlow?.close()
    }

    private suspend fun outputCrops() {
        if (returnFlow == null) {
            return printCrops()
        } else {
            returnFlow.emit(changed)
        }
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

        changed[paths[currentIndex]] = currentJob
    }

    // -- Selection -----------------------------------------------------------

    private fun handlePressed(event: PointerEvent) {
        // Current mouse position in viewport coordinates
        val selectionPoint =
            ViewportSpaceCoord(event.location.x.roundToInt(), event.location.y.roundToInt())

        previousViewport = selectionPoint

        pendingDeselectOnClick = false

        // Convert mouse position to image coordinates
        val imgPoint = selectionPoint.imageSpace(origin, zoom)

        selection?.let { selection ->
            selectedVertex(selection, selectionPoint)?.let { (isBox, index) ->
                dragMode = DragMode.VertexDragging(isBox, index)
                return
            }

            if (selection.labelPoints.containsPoint(imgPoint.point)) {
                dragMode = DragMode.LabelDragging
                return
            }

            if (selection.boxPoints.containsPoint(imgPoint.point)) {
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
        val selectionPoint =
            ViewportSpaceCoord(event.location.x.roundToInt(), event.location.y.roundToInt())

        when (val mode = dragMode) {
            is DragMode.Panning -> {
                val delta = selectionPoint.point - previousViewport.point

                if (delta.inner.any { it == 0 }) {
                    pendingDeselectOnClick = false
                }

                origin = ViewportSpaceCoord(origin.point + delta)
            }

            is DragMode.VertexDragging -> {
                selection?.let { s ->
                    val imgPoint = selectionPoint.imageSpace(origin, zoom).point
                    val clampedToViewport =
                        imgPoint.clamp(0 to 0, imageWidth.roundToInt() to imageHeight.roundToInt())

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
                    val image = selectionPoint.imageSpace(previousViewport, zoom)

                    // Group the crop pair and restrict to viewport bounds
                    val all =
                        Polygon.create(
                            *(s.boxPoints.vertices.toList() + s.labelPoints.vertices)
                                .map { it.inner }
                                .toTypedArray()
                        )

                    val clampedMovement =
                        image.point.clamp(all.xMin to all.yMin, all.xMax to all.yMax)

                    // Move all vertexes
                    val box = s.boxPoints.offsetBy(clampedMovement)
                    val label = s.labelPoints.offsetBy(clampedMovement)

                    selection = s.copy(boxPoints = box, labelPoints = label)
                }
            }

            is DragMode.LabelDragging -> {
                selection?.let { s ->
                    val image = selectionPoint.imageSpace(previousViewport, zoom)

                    val clampedMovement =
                        image.point.clamp(
                            s.boxPoints.xMin - s.labelPoints.xMin to
                                s.boxPoints.yMin - s.labelPoints.yMin,
                            s.boxPoints.xMax - s.labelPoints.xMax to
                                s.boxPoints.yMax - s.labelPoints.yMax,
                        )

                    selection = s.copy(labelPoints = s.labelPoints.offsetBy(clampedMovement))
                }
            }

            null -> {}
        }

        // On drag release
        previousViewport = selectionPoint

        rerender()
    }

    private fun findCropAtPoint(pt: Coord.ImageSpaceCoord): String? {
        val job = currentJob
        // checks all crop pairs that image contains
        val keys = job.vertexes!!.keys

        for (k in keys) {
            val container = job.vertexes[k]!!
            val box = container.boxBoundary
            // TODO: We need to handle multiple labels
            val label = container.labelBoundaries.firstOrNull()

            if (box.points.containsPoint(pt.point)) {
                return k
            }
            if (label != null && label.points.containsPoint(pt.point)) {
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
        selectionPoint: ViewportSpaceCoord,
    ): Pair<Boolean, Int>? =
        selection.boxPoints.vertices
            .map { Coord.ImageSpaceCoord(it).viewportSpace(origin, zoom) }
            .mapIndexed { i, c -> i to c }
            .firstOrNull { (_, vertex) ->
                vertex.point.distanceTo(selectionPoint.point) <= VERTEX_DETECTION_RADIUS
            }
            ?.let { true to it.first }
            ?: selection.labelPoints.vertices
                .map { Coord.ImageSpaceCoord(it).viewportSpace(origin, zoom) }
                .mapIndexed { i, c -> i to c }
                .firstOrNull { (_, vertex) ->
                    vertex.point.distanceTo(selectionPoint.point) <= VERTEX_DETECTION_RADIUS
                }
                ?.let { false to it.first }

    // -- Helper Converters ---------------------------------------------------

    private fun Path.toDataUri(): String {
        val bytes = readBytes()
        val mime = Files.probeContentType(this) ?: "image/jpeg"
        val encoded = Base64.encode(bytes)
        return "data:$mime;base64,$encoded"
    }

    private fun indexColour(index: String): Colour =
        pairColours.getOrPut(index) { HSLColour(Random.nextDouble(360.0), 255u, 127u) }

    // -- Rendering -----------------------------------------------------------

    override fun render(canvas: Canvas) {
        // Fits image to viewport if image finished loading before view had been formatted
        if (!didInitialFit && image != null && width > 0 && height > 0) {
            scaleToFit()
        }

        canvas.rect(bounds.atOrigin, fill = Colour.LIGHT_GREY.toDoodle().paint)

        image?.let { img ->
            canvas.image(
                img,
                destination =
                    Rectangle.create(
                            x = origin.x,
                            y = origin.y,
                            w = (imageWidth * zoom).roundToInt(),
                            h = (imageHeight * zoom).roundToInt(),
                        )
                        .toDoodle(),
            )
        }

        val job = currentJob
        val selectedKey = selection?.key

        // Draw outlines for every crop
        job.vertexes!!
            .entries
            .filter { (k, v) -> k != selectedKey }
            .forEach { (k, v) ->
                val boxStrokeColour = indexColour(k)
                canvas.drawOutline(v.boxBoundary.points, boxStrokeColour)
                v.labelBoundaries.firstOrNull()?.let { canvas.drawOutline(it.points, Colour.BLUE) }
            }

        // Draw vertexes for crop pair if selected
        selection?.let { sel ->
            val boxStrokeColour = indexColour(sel.key)
            canvas.drawFilledSelection(sel.boxPoints, boxStrokeColour)
            canvas.drawFilledSelection(sel.labelPoints, Colour.BLUE)
            canvas.drawVertexes(sel.boxPoints)
            canvas.drawVertexes(sel.labelPoints)
        }
    }

    private fun Canvas.drawOutline(poly: Quad, colour: Colour) {
        val points =
            poly.toClockwise().vertices.map {
                Coord.ImageSpaceCoord(it).toDoodlePoint(origin, zoom)
            }
        val stroke = colour.toDoodle().paint.stroke(thickness = LINE_THICKNESS)
        for (i in points.indices) {
            line(points[i], points[(i + 1) % points.size], stroke = stroke)
        }
    }

    private fun Canvas.drawFilledSelection(poly: Quad, outlineColor: Colour) {
        val screenPoly =
            poly.toClockwise().vertices.map {
                Coord.ImageSpaceCoord(it).toDoodlePoint(origin, zoom)
            }

        val vertexIterator = screenPoly.take(3).iterator()
        val remaining = screenPoly.subList(3, poly.vertices.size).toTypedArray()

        poly(
            ConvexPolygon.Companion(
                vertexIterator.next(),
                vertexIterator.next(),
                vertexIterator.next(),
                *remaining,
            ),
            fill = SELECTION_FILL.toDoodle().paint,
        )

        val stroke = outlineColor.toDoodle().paint.stroke(thickness = LINE_THICKNESS)
        for (i in screenPoly.indices) {
            line(screenPoly[i], screenPoly[(i + 1) % screenPoly.size], stroke = stroke)
        }
    }

    private fun Canvas.drawVertexes(poly: Polygon) {
        poly.vertices
            .map { Coord.ImageSpaceCoord(it).toDoodlePoint(origin, zoom) }
            .forEach { v ->
                rect(
                    Doodle.Rectangle(
                        x = v.x - VERTEX_SIZE / 2,
                        y = v.y - VERTEX_SIZE / 2,
                        width = VERTEX_SIZE,
                        height = VERTEX_SIZE,
                    ),
                    fill = Colour.WHITE.toDoodle().paint,
                    stroke = Colour.BLUE.toDoodle().paint.stroke(thickness = 1.0),
                )
            }
    }
}
