import au.edu.unimelb.archivetool.switchboard.Switchboard
import au.edu.unimelb.archivetool.switchboard.interactions.export.CSVStringExportInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.GENERATED_PATH
import au.edu.unimelb.archivetool.util.error.expect
import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.logging.LogLevel
import au.edu.unimelb.archivetool.util.logging.consumers.StderrConsumer
import au.edu.unimelb.archivetool.util.logging.consumers.StdoutConsumer
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class SwitchboardEndToEnd {
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

    @OptIn(ExperimentalPathApi::class)
    @SemiManual(
        "Manually Interact with the GUI.",
        lastSucceeded = "bfb9ca7fe545ce26860539588340ef886e373927",
    )
    //@Test
    fun `try run end2end`() {
        directory.resolve(GENERATED_PATH).deleteRecursively()

        val csv = CSVStringExportInteraction()
        val sw = Switchboard.with(csv)

        sw.init(setOf(StderrConsumer()))
        sw.createPreview(directory)
        sw.reviewPreview(directory)
        sw.manipulateImages(directory)
        sw.createOCR(directory)
        // TODO: Port In OCR Review
        sw.generateExport(directory)

        Log.info(csv.string)
    }
}