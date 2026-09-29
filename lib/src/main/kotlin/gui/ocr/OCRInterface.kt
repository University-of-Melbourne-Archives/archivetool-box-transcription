package au.edu.unimelb.archivetool.gui.ocr

import au.edu.unimelb.archivetool.switchboard.jobstate.BoxMetadata
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.MonoColour
import io.nacular.doodle.application.Application
import io.nacular.doodle.controls.buttons.PushButton
import io.nacular.doodle.controls.text.Label
import io.nacular.doodle.core.Display
import io.nacular.doodle.core.WindowGroup
import io.nacular.doodle.core.container
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
    private val jobs: Map<JobRelativePath, BoxMetadata?>,
    display: Display,
    windows: WindowGroup,
    themeManager: ThemeManager,
    theme: Theme,
    imageLoader: ImageLoader,
) : Application {
    // -- Job data ------------------------------------------------------------
    private val paths: List<JobRelativePath> = jobs.keys.toList()

    private var currentIndex = 0

    private val currentPath: JobRelativePath
        get() = paths[currentIndex]

    // To pass later for the table!!!!!!!!!!!
    private val currentJob: BoxMetadata?
        get() = jobs.getValue(currentPath)

    // -- Views ---------------------------------------------------------------
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fileCounter = Label("")
    private val viewport = ImageViewport(imageLoader, appScope)

    init {
        windows.main.apply {
            title = "OCRReview"
            size = Size(1600.0, 900.0)
        }

        themeManager.selected = theme

        // Below viewport
        val zoomInButton = PushButton("+").apply { fired += { viewport.zoomIn() } }
        val zoomOutButton = PushButton("-").apply { fired += { viewport.zoomOut() } }
        val fitButton = PushButton("Fit").apply { fired += { viewport.scaleToFit() } }

        // Below table
        val bottomButtons =
            listOf(
                PushButton("Previous").apply { fired += { moveIndex(currentIndex - 1) } },
                PushButton("Next").apply { fired += { moveIndex(currentIndex + 1) } },
                PushButton("Save").apply { fired += {} },
                PushButton("Finish").apply { fired += {} },
            )

        val buttonWidth = 90.0
        val buttonHeight = 32.0
        val buttonGap = 20.0
        val bottomRowWidth = bottomButtons.size * buttonWidth + (bottomButtons.size - 1) * buttonGap

        val buttonRow = container {
            bottomButtons.forEach { this += it }

            layout =
                constrain(bottomButtons[0], bottomButtons[1], bottomButtons[2]) { buttons ->
                    buttons.forEach {
                        it.top eq 0
                        it.width eq buttonWidth
                        it.height eq buttonHeight
                    }

                    var prev = buttons.first()
                    prev.left eq 0
                    buttons.listIterator(1).forEachRemaining {
                        it.left eq prev.right + buttonGap
                        prev = it
                    }
                }
        }

        val bottomBar = container {
            this += buttonRow
            this += fileCounter

            layout =
                constrain(buttonRow, fileCounter) { row, txt ->
                    row.top eq 0
                    row.centerX eq parent.centerX
                    row.width eq bottomRowWidth
                    row.height eq buttonHeight

                    txt.top eq row.bottom + 10
                    txt.right eq parent.right
                    txt.width eq fileCounter.idealSize.width
                    txt.height eq fileCounter.idealSize.height
                }
        }

        display += listOf(zoomOutButton, zoomInButton, fitButton, viewport, bottomBar)
        display.fill(MonoColour(0xECu).toDoodle().toRGB().paint)

        display.layout =
            constrain(zoomInButton, zoomOutButton, fitButton, viewport, bottomBar) {
                zIn,
                zOut,
                fit,
                vp,
                btm ->
                vp.top eq 60
                vp.bottom eq parent.height - 80
                vp.centerX eq parent.centerX
                vp.height lessEq parent.height - vp.top - 30
                vp.width lessEq parent.width - 40
                vp.width eq vp.height * (4.0 / 3.0)
                vp.width eq 5000 strength Medium

                fit.top eq 14
                fit.right eq vp.right
                fit.width eq 32
                fit.height eq 32

                zOut.top eq fit.top
                zOut.right eq fit.left - 8
                zOut.width eq 32
                zOut.height eq 32

                zIn.top eq fit.top
                zIn.right eq zOut.left - 8
                zIn.width eq 32
                zIn.height eq 32

                btm.top eq vp.bottom + 10
                btm.centerX eq parent.centerX
                btm.width eq parent.width - 40
                btm.bottom eq parent.bottom - 10
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

        // table stuff here
    }

    override fun shutdown() {}
}
