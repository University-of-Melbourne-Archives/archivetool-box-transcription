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

    // Original mouse position when dragging
    private var lastPointerX: Double? = null
    private var lastPointerY: Double? = null

    init {
        clipCanvasToBounds = true
        loadImage(paths[currentIndex])

        pointerChanged +=
            object : PointerListener {
                override fun pressed(event: PointerEvent) {
                    lastPointerX = event.location.x
                    lastPointerY = event.location.y
                }

                override fun released(event: PointerEvent) {
                    lastPointerX = null
                    lastPointerY = null
                }

                override fun exited(event: PointerEvent) {
                    lastPointerX = null
                    lastPointerY = null
                }
            }

        pointerMotionChanged +=
            object : PointerMotionListener {
                override fun dragged(event: PointerEvent) {
                    val localX = lastPointerX
                    val localY = lastPointerY

                    if (localX != null && localY != null) {
                        val currX = event.location.x
                        val currY = event.location.y

                        originX += (currX - localX)
                        originY += (currY - localY)

                        lastPointerX = currX
                        lastPointerY = currY

                        rerender()
                    }
                }
            }
    }

    fun zoomIn() = zoomBy(ZOOM_INTERVAL)

    fun zoomOut() = zoomBy(1.0 / ZOOM_INTERVAL)

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

    fun previous() {
        if (currentIndex <= 0) {
            return
        }

        currentIndex--
        loadImage(paths[currentIndex])
        onImageChanged(currentIndex, totalImages)
    }

    fun next() {
        if (currentIndex >= paths.size - 1) {
            return
        }

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

    fun confirm() {
        print("test")
    }

    fun create() {
        print("test")
    }

    fun delete() {
        print("test")
    }

    // -- Selection -----------------------------------------------------------

    private fun handlePressed(event: PointerEvent) {
        val sx = event.location.x
        val sy = event.location.y

        // this is NOT normalised with the zoom yet
        val imgPt = VertexDouble(sx, sy)

        // DOESN'T handle deselect drag transition yet
        val found = findCropAtPoint(imgPt)
        dragMode =
            if (found != null) {
                selectPair(found)
                DragMode.AreaDragging
            } else {
                DragMode.Panning
            }
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

    // Takes in singular point of crop and scales and returns it as Doodle Point object
    private fun toScreen(v: au.edu.unimelb.archivetool.util.geometry.Point): Point {
        return Point(originX + v.first * zoom, originY + v.second * zoom)
    }

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

        // Runs for each pair of crops
        val job = currentJob
        job.crops
            .filterKeys { it.startsWith("b") }
            .forEach { (key, crop) ->
                val i = key.drop(1)

                val box = crop as? JobFile.BoxCrop ?: return@forEach
                val label = job.crops["l$i"] as? JobFile.LabelCrop ?: return@forEach

                val boxStrokeColour = indexColour(i.toInt())

                drawPair(box, label, boxStrokeColour, canvas)
            }
    }

    private fun drawPair(
        box: JobFile.BoxCrop,
        label: JobFile.LabelCrop,
        boxStrokeColour: Color,
        canvas: Canvas,
    ) {
        val boxPts = box.points.inner.map { toScreen(it) }
        val labelPts = label.points.inner.map { toScreen(it) }

        val boxStroke = (boxStrokeColour).paint.stroke(thickness = LINE_THICKNESS)
        val labelStroke = (Color.Blue).paint.stroke(thickness = LINE_THICKNESS)

        // Connecting lines
        for (i in boxPts.indices) {
            val point1 = boxPts[i]
            val point2 = boxPts[(i + 1) % boxPts.size]

            canvas.line(point1, point2, stroke = boxStroke)
        }
        for (i in labelPts.indices) {
            val point1 = labelPts[i]
            val point2 = labelPts[(i + 1) % labelPts.size]

            canvas.line(point1, point2, stroke = labelStroke)
        }

        // Vertices for when selected
        boxPts.forEach { point ->
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
        labelPts.forEach { point ->
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
