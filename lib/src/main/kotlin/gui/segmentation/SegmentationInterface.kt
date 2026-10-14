package au.edu.unimelb.archivetool.gui.segmentation

import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.MonoColour
import au.edu.unimelb.archivetool.util.MutableWaitFlow
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SegmentationInterface(
    jobs: Map<JobRelativePath, JobFile?>,
    returnFlow: MutableWaitFlow<Map<JobRelativePath, JobFile>>?,
    display: Display,
    windows: WindowGroup,
    themeManager: ThemeManager,
    theme: Theme,
    imageLoader: ImageLoader,
) : Application {
    // For asynchronous image loading
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fileCounter = Label("")
    private val viewport =
        ImageViewport(
            jobs,
            returnFlow,
            imageLoader,
            appScope,
            onImageChanged = { index, total ->
                fileCounter.text = "%03d/%03d".format(index + 1, total)
            },
            kill = {display.children.clear()}
        )

    init {
        windows.apply {
            main.apply {
                title = "SegmentationReview"
                size = Size(1100.0, 1000.0)
            }

            mainWindowCloseBehavior = {runBlocking { viewport.close() }}
        }

        themeManager.selected = theme

        // Above viewport
        val zoomInButton = PushButton("+").apply { fired += { viewport.zoomIn() } }
        val zoomOutButton = PushButton("-").apply { fired += { viewport.zoomOut() } }
        val fitButton = PushButton("Fit").apply { fired += { viewport.scaleToFit() } }

        // Below viewport
        val bottomButtons =
            listOf(
                PushButton("Previous").apply { fired += { viewport.previous() } },
                PushButton("Next").apply { fired += { viewport.next() } },
                PushButton("Confirm").apply { fired += { viewport.confirm() } },
                PushButton("Create").apply { fired += { viewport.create() } },
                PushButton("Delete").apply { fired += { viewport.delete() } },
                PushButton("Finish").apply {
                    fired += { windows.main.close() }
                },
            )

        val buttonWidth = 90.0
        val buttonHeight = 32.0
        val buttonGap = 20.0
        val bottomRowWidth = bottomButtons.size * buttonWidth + (bottomButtons.size - 1) * buttonGap

        val buttonRow = container {
            bottomButtons.forEach { this += it }

            layout =
                constrain(
                    bottomButtons[0],
                    bottomButtons[1],
                    bottomButtons[2],
                    bottomButtons[3],
                    bottomButtons[4],
                    bottomButtons[5],
                ) { buttons ->
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

        fileCounter.text = "%03d/%03d".format(viewport.currentIndex + 1, viewport.totalImages)
        // val fileCounter = Label("000/" + String.format("%03d", jobs.size))

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
    }

    override fun shutdown() {
        runBlocking { viewport.close() }
    }
}
