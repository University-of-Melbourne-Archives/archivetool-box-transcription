package interactions

import au.edu.unimelb.archivetool.switchboard.interactions.PythonCreatePreviewInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import au.edu.unimelb.archivetool.switchboard.jobstate.memory.MemoryJobStateInteraction
import au.edu.unimelb.archivetool.util.error.expect
import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.logging.LogLevel
import au.edu.unimelb.archivetool.util.logging.consumers.StdoutConsumer
import au.edu.unimelb.archivetool.util.logging.consumers.StringConsumer
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class PythonCreatePreviewInteractionTest {
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
    fun `call create interaction`() = runTest {
        val consumer = StringConsumer(LogLevel.Trace)
        Log.registerConsumer(consumer)

        val job = MemoryJobStateInteraction()
        val createPreview = PythonCreatePreviewInteraction()

        val state: JobState = job.create(directory)

        val inFiles =
            state.validFiles(createPreview, allowNulls = true).also {
                it.takeUnless { it.isEmpty() }.expect("No valid files for this step.")
            }

        val toUpdate =
            createPreview.createPreview(inFiles.keys.expect("Could not load list of valid files."))

        Log.trace(toUpdate.toString())
    }
}
