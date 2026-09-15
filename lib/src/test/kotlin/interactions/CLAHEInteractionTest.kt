package interactions

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.Log.expect
import au.edu.unimelb.archivetool.logging.LogLevel
import au.edu.unimelb.archivetool.logging.consumers.StdoutConsumer
import au.edu.unimelb.archivetool.switchboard.interactions.imagemanip.CLAHEInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.imagemanip.CropInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.GENERATED_PATH
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import au.edu.unimelb.archivetool.util.geometry.Quad
import au.edu.unimelb.archivetool.util.geometry.Rectangle
import baseFiles
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.deleteRecursively
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

    @Test
    fun `try clahe interaction with no crop`() {
        //@OptIn(ExperimentalPathApi::class)
        //directory.resolve(GENERATED_PATH).deleteRecursively()

        val files =
            baseFiles
                .mapKeys { JobRelativePath(directory, it.key) }
                .mapValues {
                    it.value.run {
                        JobFile(
                            approved = true,
                            crops =
                                mutableMapOf(
                                    "crop-1" to
                                        JobFile.BoxCrop(
                                            Quad.create(0 to 0, 1 to 0, 1 to 1, 0 to 1),
                                            confidence = 1.0,
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

    @Test
    fun `try clahe interaction with crops`() {
        val files =
            baseFiles
                .mapKeys { JobRelativePath(directory, it.key) }
                .mapValues {
                    it.value.run {
                        JobFile(
                            approved = true,
                            crops =
                                mutableMapOf(
                                    "crop-1" to
                                            JobFile.BoxCrop(
                                                Rectangle.create(
                                                    0 to 0,
                                                    1200 to 0,
                                                    1200 to 1000,
                                                    0 to 1000,
                                                ),
                                                confidence = 1.0,
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
