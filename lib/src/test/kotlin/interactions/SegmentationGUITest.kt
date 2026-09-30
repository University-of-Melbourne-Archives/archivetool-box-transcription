package interactions

import SemiManual
import au.edu.unimelb.archivetool.switchboard.interactions.ReviewGUIInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.error.expect
import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.logging.LogLevel
import au.edu.unimelb.archivetool.util.logging.consumers.StdoutConsumer
import au.edu.unimelb.archivetool.util.math.geometry.Quad
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.serialization.json.Json

class SegmentationGUITest {
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
        "Manually Interact with the GUI.",
        lastSucceeded = "bfb9ca7fe545ce26860539588340ef886e373927",
    )
    //@Test
    fun `try review interaction`() {
        val files =
            mapOf(
                    Path("PXL_20251127_230150462.jpg") to
                        JobFile(
                            vertexes =
                                mutableMapOf(
                                    "box-1" to
                                        ArchiveBox(
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
                                                )
                                            ),
                                        )
                                )
                        ),
                    Path("PXL_20251127_230207837.jpg") to
                        JobFile(
                            vertexes =
                                mutableMapOf(
                                    "box-1" to
                                        ArchiveBox(
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
                                                )
                                            ),
                                        )
                                )
                        ),
                )
                .mapKeys { JobRelativePath(directory, it.key) }
                .toMutableMap()
        println(ReviewGUIInteraction().review(files).mapValues { (_, v) -> Json.encodeToString(v) })
    }
}
