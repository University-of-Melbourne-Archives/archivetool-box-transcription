package au.edu.unimelb.archivetool.gui.segmentation

import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import io.nacular.doodle.core.View
import io.nacular.doodle.drawing.Canvas
import io.nacular.doodle.drawing.Color
import io.nacular.doodle.drawing.paint
import io.nacular.doodle.geometry.Rectangle
import io.nacular.doodle.image.Image
import io.nacular.doodle.image.ImageLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ImageViewport(
    private val jobs: Map<Path, JobFile?>,
    private val imageLoader: ImageLoader,
    appScope: CoroutineScope,
) : View() {
    companion object {
        private const val MIN_ZOOM       = 0.02
        private const val MAX_ZOOM       = 40.0
        private const val ZOOM_INTERVAL      = 1.25
    }

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

    init {
        clipCanvasToBounds = true

        val path = jobs.keys.first()

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

    fun zoomIn()  = zoomBy(ZOOM_INTERVAL)
    fun zoomOut() = zoomBy(1.0 / ZOOM_INTERVAL)

    private fun zoomBy(factor: Double) {
        if (image == null) return

        val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val centerX = width  / 2
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

    override fun render(canvas: Canvas) {
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
    }

    // THIS IS TEMPORARY AND AM JUST READING ENTIRE JPEG INTO MEMORY
    private fun Path.toDataUri(): String {
        val bytes = Files.readAllBytes(this)
        val mime = Files.probeContentType(this) ?: "image/jpeg"
        val encoded = Base64.getEncoder().encodeToString(bytes)
        return "data:$mime;base64,$encoded"
    }
}
