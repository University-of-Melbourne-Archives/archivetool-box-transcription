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

    // -- Job data ------------------------------
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

    // -- Image display -------------------------
    private var image: Image? = null
    private var imageWidth = 0.0
    private var imageHeight = 0.0

    // Pixels on screen for each pixel of the image
    var zoom: Double = 1.0

    // Position of image pixel (0,0) relative to this viewport
    private var originX = 0.0
    private var originY = 0.0

    // Original mouse position when dragging
    private var lastPointerX: Double? = null
    private var lastPointerY: Double? = null

    // Marks if scaleToFit() should be called on startup
    private var didInitialFit = false

    // Guarantees each box crop retains its initial colouring
    private val pairColours = mutableMapOf<Int, Color>()

    // -- Selection -----------------------------
    // stuff here

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
        if (image == null || imageWidth <= 0 || imageHeight <= 0) return
        if (width <= 0 || height <= 0) return

        val scaleX = width / imageWidth
        val scaleY = height / imageHeight

        zoom = min(scaleX, scaleY)

        val renderedW = imageWidth * zoom
        val renderedH = imageHeight * zoom

        originX = (width - renderedW) / 2
        originY = (height - renderedH) / 2

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

    private fun Path.toDataUri(): String {
        val bytes = Files.readAllBytes(this)
        val mime = Files.probeContentType(this) ?: "image/jpeg"
        val encoded = Base64.getEncoder().encodeToString(bytes)
        return "data:$mime;base64,$encoded"
    }
}
