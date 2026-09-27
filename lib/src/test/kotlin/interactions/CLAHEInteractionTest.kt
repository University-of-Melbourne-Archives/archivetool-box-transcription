package interactions

import SemiManual
import au.edu.unimelb.archivetool.switchboard.interactions.imagemanip.CLAHEInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.imagemanip.CropInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.error.expect
import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.logging.LogLevel
import au.edu.unimelb.archivetool.util.logging.consumers.StdoutConsumer
import au.edu.unimelb.archivetool.util.math.geometry.Rectangle
import baseFiles
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class CLAHEInteractionTest {
    val testConsumer = StdoutConsumer(LogLevel.Trace)

    @BeforeTest
    fun init() {
        Log.registerConsumer(testConsumer)
    }

    @AfterTest
    fun destroy() {
        Log.revokeConsumer(testConsumer)
    }

    val directory by lazy {
        Path(
            {}.javaClass
                .getResource("/SwitchboardTest")
                .expect("Test couldn't find test folder")
                .path
        )
    }

    @SemiManual(
        "Check that the directory has properly thresholded images.",
        lastSucceeded = "16c9faa6b6529c966a75bf2ae455e34c33accde5",
    )
    @Test
    fun `try clahe interaction with no crop`() {
        // @OptIn(ExperimentalPathApi::class)
        // directory.resolve(GENERATED_PATH).deleteRecursively()

        val files =
            baseFiles
                .mapKeys { JobRelativePath(directory, it.key) }
                .mapValues {
                    it.value.run {
                        JobFile(
                            approved = true,
                            vertexes =
                                mutableMapOf(
                                    "crop-1" to
                                        ArchiveBox(
                                            Boundary(
                                                Rectangle.create(0 to 0, 1 to 0, 1 to 1, 0 to 1),
                                                confidence = 1.0,
                                            )
                                        )
                                ),
                        )
                    }
                }
                .toMutableMap()
        CLAHEInteraction().manipulate(files)
        // TODO: Find a better method to verify correctness than manually checking
        // lib/build/resources/test/SwitchboardTest/
    }

    @SemiManual(
        "Check that the .archivetool_generated directory has images with the threshold and the right bounds.",
        lastSucceeded = "16c9faa6b6529c966a75bf2ae455e34c33accde5",
    )
    @Test
    fun `try clahe interaction with crops`() {
        val files =
            baseFiles
                .mapKeys { JobRelativePath(directory, it.key) }
                .mapValues {
                    it.value.run {
                        JobFile(
                            approved = true,
                            vertexes =
                                mutableMapOf(
                                    "crop-1" to
                                        ArchiveBox(
                                            boxBoundary =
                                                Boundary(
                                                    Rectangle.create(
                                                        0 to 0,
                                                        1200 to 0,
                                                        1200 to 1000,
                                                        0 to 1000,
                                                    ),
                                                    confidence = 1.0,
                                                ),
                                            labelBoundaries = listOf(),
                                        )
                                ),
                        )
                    }
                }
                .toMutableMap()
        val cropped = CropInteraction().manipulate(files)

        CLAHEInteraction().manipulate(cropped)
    }
}
