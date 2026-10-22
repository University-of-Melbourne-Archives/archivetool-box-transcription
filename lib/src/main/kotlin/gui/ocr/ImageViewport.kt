package au.edu.unimelb.archivetool.gui.ocr

import au.edu.unimelb.archivetool.gui.segmentation.Coord.ViewportSpaceCoord
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.Colour
import au.edu.unimelb.archivetool.util.math.geometry.Rectangle
import io.nacular.doodle.core.View
import io.nacular.doodle.drawing.Canvas
import io.nacular.doodle.event.PointerEvent
import io.nacular.doodle.event.PointerListener
import io.nacular.doodle.event.PointerMotionListener
import io.nacular.doodle.image.Image
import io.nacular.doodle.image.ImageLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.encoding.Base64.Default as Base64
import kotlin.io.path.readBytes
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ImageViewport(
    private val imageLoader: ImageLoader,
    private val appScope: CoroutineScope,
    private val onImageLoaded: (width: Double, height: Double) -> Unit = { _, _ -> },
) : View() {
    companion object {
        private const val MIN_ZOOM = 0.02
        private const val MAX_ZOOM = 40.0
        private const val ZOOM_INTERVAL = 1.25
    }

    // -- Image display -------------------------------------------------------
    private var image: Image? = null

    var imageWidth = 0.0
        private set

    var imageHeight = 0.0
        private set

    // Pixels on screen for each pixel of the image
    var zoom: Double = 1.0

    // Position of image pixel (0,0) relative to this viewport
    private var origin = ViewportSpaceCoord(0, 0)

    // Marks if scaleToFit() should be called on startup
    private var didInitialFit = false

    private var selectionPoint: ViewportSpaceCoord? = null

    init {
        clipCanvasToBounds = true

        pointerChanged +=
            object : PointerListener {
                override fun pressed(event: PointerEvent) {
                    selectionPoint =
                        ViewportSpaceCoord(
                            event.location.x.roundToInt(),
                            event.location.y.roundToInt(),
                        )
                }

                override fun released(event: PointerEvent) {
                    selectionPoint = null
                }

                override fun exited(event: PointerEvent) {
                    selectionPoint = null
                }
            }

        pointerMotionChanged +=
            object : PointerMotionListener {
                override fun dragged(event: PointerEvent) {

                    val previousViewport = selectionPoint ?: return
                    val current =
                        ViewportSpaceCoord(
                            event.location.x.roundToInt(),
                            event.location.y.roundToInt(),
                        )

                    val delta = current.point - previousViewport.point
                    origin = ViewportSpaceCoord(origin.point + delta)
                    selectionPoint = current

                    rerender()
                }
            }
    }

    // -- Image loading -------------------------------------------------------

    fun loadImage(path: JobRelativePath) {
        image = null

        imageWidth = 0.0
        imageHeight = 0.0

        didInitialFit = false

        rerender()

        appScope.launch {
            val loaded = imageLoader.load(path.absolute.toDataUri()) ?: return@launch

            image = loaded
            imageWidth = loaded.size.width
            imageHeight = loaded.size.height
            onImageLoaded(imageWidth, imageHeight)

            rerender()
        }
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

    // -- Helper Converters ---------------------------------------------------

    private fun Path.toDataUri(): String {
        val bytes = readBytes()
        val mime = Files.probeContentType(this) ?: "image/jpeg"
        val encoded = Base64.encode(bytes)
        return "data:$mime;base64,$encoded"
    }

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
    }
}
