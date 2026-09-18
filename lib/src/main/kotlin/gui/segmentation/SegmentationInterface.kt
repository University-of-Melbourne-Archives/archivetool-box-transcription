package au.edu.unimelb.archivetool.gui.segmentation

import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import io.nacular.doodle.application.Application
import io.nacular.doodle.controls.buttons.PushButton
import io.nacular.doodle.core.Display
import io.nacular.doodle.core.View
import io.nacular.doodle.core.WindowGroup
import io.nacular.doodle.core.view
import io.nacular.doodle.drawing.Canvas
import io.nacular.doodle.drawing.Color
import io.nacular.doodle.drawing.Color.Companion.Black
import io.nacular.doodle.drawing.paint
import io.nacular.doodle.drawing.text
import io.nacular.doodle.geometry.Rectangle
import io.nacular.doodle.geometry.Size
import io.nacular.doodle.image.ImageLoader
import io.nacular.doodle.layout.constraints.Strength.Companion.Weak
import io.nacular.doodle.layout.constraints.constrain
import io.nacular.doodle.layout.constraints.fill
import io.nacular.doodle.theme.Theme
import io.nacular.doodle.theme.ThemeManager
import org.kodein.di.instance
import java.nio.file.Path

class SegmentationInterface(jobs: Map<Path, JobFile?>, display: Display, windows: WindowGroup, themeManager: ThemeManager, theme: Theme, imageLoader: ImageLoader): Application {
    private val viewport = ImageViewport(jobs, imageLoader)

    init {
        windows.main.apply {
            title = "SegmentationReview"
            size  = Size(1200.0, 1000.0)
        }

        themeManager.selected = theme

        val zoomInButton  = PushButton("+")
        val zoomOutButton = PushButton("-")
        val fitButton     = PushButton("Fit")
        val previousButton  = PushButton("Previous")
        val nextButton = PushButton("Next")
        val confirmButton     = PushButton("Confirm")
        val createButton  = PushButton("Create")
        val deleteButton = PushButton("Delete")

        display += listOf(zoomOutButton, zoomInButton, fitButton, viewport)

        display.fill(Color(0xECECECu).paint)

        display.layout = constrain(zoomInButton, zoomOutButton, fitButton, viewport) { zIn, zOut, fit, vp ->
            vp.top     eq 60
            vp.centerX eq parent.centerX

            fit.top    eq 14
            fit.right  eq vp.right
            fit.width  eq 32
            fit.height eq 32

            zOut.top    eq fit.top
            zOut.right  eq fit.left - 8
            zOut.width  eq 32
            zOut.height eq 32

            zIn.top     eq fit.top
            zIn.right   eq zOut.left - 8
            zIn.width   eq 32
            zIn.height  eq 32
        }
    }


    override fun shutdown() {}
}