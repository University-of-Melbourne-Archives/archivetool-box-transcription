package au.edu.unimelb.archivetool.app

//
import au.edu.unimelb.archivetool.gui.segmentation.SegmentationInterface
import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.LogLevel
import au.edu.unimelb.archivetool.logging.consumers.StdoutConsumer
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.util.geometry.Rectangle
import io.nacular.doodle.application.Modules.Companion.ImageModule
import io.nacular.doodle.application.Modules.Companion.PointerModule
import io.nacular.doodle.application.application
import io.nacular.doodle.theme.basic.BasicTheme.Companion.basicButtonBehavior
import java.nio.file.Path
import org.kodein.di.instance

fun main() {
    Log.registerConsumer(StdoutConsumer())

    Log.error("test")
    Log.warn("test")
    Log.log(LogLevel.Info, "test")
    Log.debug("test")
    Log.trace("test")

    val jobs: Map<Path, JobFile?> =
        mapOf(
            Path.of("PXL_20251127_230150462") to
                JobFile(
                    crops =
                        mutableMapOf(
                            "b1" to
                                JobFile.BoxCrop(
                                    points =
                                        Rectangle.create(
                                            230 to 10,
                                            2350 to 130,
                                            2170 to 500,
                                            260 to 360,
                                        ),
                                    confidence = 0.08,
                                ),
                            "l1" to
                                JobFile.LabelCrop(
                                    points =
                                        Rectangle.create(
                                            1700 to 150,
                                            2180 to 250,
                                            2140 to 310,
                                            1700 to 300,
                                        ),
                                    confidence = 0.90,
                                ),
                        )
                ),
            Path.of("PXL_20251127_230207837") to
                JobFile(
                    crops =
                        mutableMapOf(
                            "b1" to
                                JobFile.BoxCrop(
                                    points =
                                        Rectangle.create(
                                            260 to 400,
                                            2120 to 510,
                                            2060 to 870,
                                            300 to 750,
                                        ),
                                    confidence = 1.0,
                                ),
                            "l1" to
                                JobFile.LabelCrop(
                                    points =
                                        Rectangle.create(
                                            1630 to 630,
                                            2150 to 170,
                                            2030 to 800,
                                            1600 to 760,
                                        ),
                                    confidence = 0.88,
                                ),
                        )
                ),
        )

    application(modules = listOf(basicButtonBehavior(), PointerModule, ImageModule)) {
        SegmentationInterface(
            jobs = jobs,
            display = instance(),
            windows = instance(),
            themeManager = instance(),
            theme = instance(),
            imageLoader = instance(),
        )
    }
}
