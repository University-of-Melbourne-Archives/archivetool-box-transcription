package interactions

import SemiManual
import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.Log.expect
import au.edu.unimelb.archivetool.logging.LogLevel
import au.edu.unimelb.archivetool.logging.consumers.StdoutConsumer
import au.edu.unimelb.archivetool.switchboard.interactions.imagemanip.CropInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.geometry.Rectangle
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

// TODO: Test cropping multiple sections.
class CropInteractionTest {
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
        "Check that the crop is generated for the correct bounds.",
        lastSucceeded = "16c9faa6b6529c966a75bf2ae455e34c33accde5",
    )
    @Test
    fun `try crop interaction`() {
        val files =
            mapOf(Path("PXL_20251127_230317928.jpg") to JobFile())
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
                                                Rectangle.create(
                                                    695 to 1276,
                                                    1340 to 1276,
                                                    695 to 2396,
                                                    1340 to 2396,
                                                ),
                                                confidence = 1.0,
                                            )
                                        )
                                ),
                        )
                    }
                }
                .toMutableMap()
        CropInteraction().manipulate(files)
        // TODO: Find a better method to verify correctness than manually checking
        // lib/build/resources/test/SwitchboardTest/
    }
}
