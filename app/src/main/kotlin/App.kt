package au.edu.unimelb.archivetool.app

import au.edu.unimelb.archivetool.gui.segmentation.SegmentationInterface
import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.LogLevel
import au.edu.unimelb.archivetool.logging.consumers.StdoutConsumer
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.util.geometry.Quad
import io.nacular.doodle.application.Modules.Companion.ImageModule
import io.nacular.doodle.application.Modules.Companion.PointerModule
import io.nacular.doodle.application.application
import io.nacular.doodle.theme.basic.BasicTheme.Companion.basicButtonBehavior
import java.nio.file.Path
import org.kodein.di.instance
import io.nacular.doodle.theme.basic.BasicTheme.Companion.basicLabelBehavior

fun main() {
    Log.registerConsumer(StdoutConsumer())

    Log.error("test")
    Log.warn("test")
    Log.log(LogLevel.Info, "test")
    Log.debug("test")
    Log.trace("test")

    val jobs: Map<Path, JobFile?> =
        mapOf(
            Path.of("lib/src/test/resources/SwitchboardTest/PXL_20251127_230150462.jpg") to
                JobFile(
                    vertexes =
                        mutableMapOf(
                            "box-1" to ArchiveBox(
                                Boundary(
                                    points =
                                        Quad.create(
                                            230 to 10,
                                            2350 to 130,
                                            2170 to 500,
                                            260 to 360,
                                        ),
                                    confidence = 0.08,
                                ),
                                listOf(
                                        Boundary(
                                            points =
                                                Quad.create(
                                                    1700 to 150,
                                                    2180 to 250,
                                                    2140 to 310,
                                                    1700 to 300,
                                                ),
                                            confidence = 0.90,
                                        ),
                                )
                            )
                        )
                ),
            Path.of("lib/src/test/resources/SwitchboardTest/PXL_20251127_230207837.jpg") to
                JobFile(
                    vertexes =
                        mutableMapOf(
                            "box-1" to ArchiveBox(
                                Boundary(
                                    points =
                                        Quad.create(
                                            154 to 860,
                                            1785 to 1113,
                                            1789 to 1600,
                                            488 to 1581,
                                        ),
                                    confidence = 1.0,
                                ),
                                listOf(
                                    Boundary(
                                        points =
                                            Quad.create(
                                                1144 to 1493,
                                                1421 to 1458,
                                                1390 to 1282,
                                                1133 to 1293,
                                            ),
                                        confidence = 0.90,
                                    ),
                                )
                            )
                        )
                ),
        )

    application(
        modules = listOf(basicLabelBehavior(), basicButtonBehavior(), PointerModule, ImageModule)
    ) {
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
