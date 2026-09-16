package interactions

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.Log.expect
import au.edu.unimelb.archivetool.logging.LogLevel
import au.edu.unimelb.archivetool.logging.consumers.StdoutConsumer
import au.edu.unimelb.archivetool.switchboard.interactions.imagemanip.FlattenInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.geometry.Quad
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class FlattenInteractionTest {
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

    @Test
    fun `try flatten interaction`() {
        val files =
            mapOf(Path("PXL_20251127_231212408.jpg") to JobFile())
                .mapKeys { JobRelativePath(directory, it.key) }
                .mapValues {
                    it.value.run {
                        JobFile(
                            approved = true,
                            vertexes =
                                mutableMapOf(
                                    "crop-1" to
                                            ArchiveBox(
                                                /*
                                                Rectangle.fromCVRect(
                                                    selectROI("test", CV.readImage(it.key.absolute))
                                                ),
                                                 */
                                                Boundary(
                                                    Quad.create(
                                                        1144 to 1000,
                                                        1776 to 984,
                                                        1900 to 2355,
                                                        1271 to 2384,
                                                    ),
                                                    confidence = 1.0,
                                                )
                                            )
                                ),
                        )
                    }
                }
                .toMutableMap()
        FlattenInteraction().manipulate(files)
        // TODO: Find a better method to verify correctness than manually checking
        // lib/build/resources/test/SwitchboardTest/
    }
}
