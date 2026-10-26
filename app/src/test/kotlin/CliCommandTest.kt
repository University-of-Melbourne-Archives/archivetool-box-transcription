package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.switchboard.MockSwitchboard
import au.edu.unimelb.archivetool.switchboard.interactions.InitInteraction
import au.edu.unimelb.archivetool.util.logging.LogConsumer
import au.edu.unimelb.archivetool.util.logging.LogLevel
import au.edu.unimelb.archivetool.util.logging.consumers.FileConsumer
import au.edu.unimelb.archivetool.util.logging.consumers.StderrConsumer
import au.edu.unimelb.archivetool.switchboard.interactions.CreatePreviewInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.OCRPreviewInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.SegmentationPreviewInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.StringStatusInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import com.github.ajalt.clikt.testing.test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Checks the CLI commands against the user stories. */
class CliCommandTest {
    @Test
    fun `help lists the main commands`() {
        val result = BaseCommand(MockSwitchboard).test("--help")
        assertEquals(0, result.statusCode)
        listOf("run", "preview", "ocr", "status", "finalise", "info").forEach {
            assertContains(result.output, it)
        }
    }

    @Test
    fun `help explains the important options and subcommands`() {
        val run = BaseCommand(MockSwitchboard).test("run --help")
        assertEquals(0, run.statusCode)
        listOf("--input-directory", "--output", "--no-resume", "--auto-approve-threshold").forEach {
            assertContains(run.output, it)
        }
        listOf("preview", "ocr").forEach { command ->
            val result = BaseCommand(MockSwitchboard).test("$command --help")
            assertEquals(0, result.statusCode)
            assertContains(result.output, "create")
            assertContains(result.output, "review")
        }
    }

    @Test
    fun `run and finalise require an output path`() {
        listOf("run", "finalise").forEach { command ->
            val result = BaseCommand(MockSwitchboard).test(command)
            assertEquals(1, result.statusCode, command)
            assertContains(result.output, "--output")
        }
    }

    @Test
    fun `invalid commands and thresholds report errors`() {
        listOf("unknown-command", "run -o result.csv -A 101", "preview review -A -1", "preview review -A abc").forEach { args ->
            val result = BaseCommand(MockSwitchboard).test(args)
            assertEquals(1, result.statusCode, args)
            assertTrue(result.output.isNotBlank(), args)
        }
    }

    @Test
    fun `a missing input directory is rejected`() {
        val result = BaseCommand(MockSwitchboard).test("run -i __missing_archive_directory__ -o result.csv")
        assertEquals(1, result.statusCode)
    }

    @Test
    fun `status uses the selected Switchboard`() {
        val status = StringStatusInteraction()
        val switchboard = MockSwitchboard.with(status)
        val directory = Files.createTempDirectory("cli-status-")
        try {
            val result = BaseCommand(switchboard).test("status", "-i", directory.toString())
            assertEquals(0, result.statusCode, result.output)
            assertContains(status.result, "no active job")
        } finally {
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `preview create can be called separately`() = withSampleImage { directory, _ ->
        var calls = 0
        val preview = object : CreatePreviewInteraction {
            override fun createPreview(
                files: Set<JobRelativePath>,
                weights: Path?,
                rejectConfidenceThreshold: Double,
            ): Map<JobRelativePath, JobFile> {
                calls++
                assertEquals(1, files.size)
                return emptyMap()
            }
        }
        val switchboard = MockSwitchboard.with(preview)
        val result = BaseCommand(switchboard).test("preview", "create", "-i", directory.toString())
        assertEquals(0, result.statusCode, result.output)
        assertEquals(1, calls)
    }

    @Test
    fun `preview review passes the threshold to Switchboard`() = withSampleImage { directory, image ->
        var threshold: Double? = null
        val review = object : SegmentationPreviewInteraction {
            override fun review(
                files: Map<JobRelativePath, JobFile?>,
                autoApproveThreshold: Double,
            ): Map<JobRelativePath, JobFile> {
                threshold = autoApproveThreshold
                return emptyMap()
            }
        }
        val switchboard = MockSwitchboard.with(review)
        val state = requireNotNull(switchboard.job.create(directory))
        state.update(mapOf(JobRelativePath(directory, image.fileName) to JobFile(vertexes = mutableMapOf())))

        val result = BaseCommand(switchboard).test("preview", "review", "-i", directory.toString(), "-A", "25")
        assertEquals(0, result.statusCode, result.output)
        assertEquals(0.25, threshold)
    }

    @Test
    fun `OCR review can be called separately`() = withSampleImage { directory, image ->
        var calls = 0
        val review = object : OCRPreviewInteraction {
            override fun review(
                files: Map<JobRelativePath, JobFile?>,
                autoApproveThreshold: Double,
            ): Map<JobRelativePath, JobFile> {
                calls++
                return emptyMap()
            }
        }
        val switchboard = MockSwitchboard.with(review)
        val state = requireNotNull(switchboard.job.create(directory))
        state.update(mapOf(JobRelativePath(directory, image.fileName) to JobFile(vertexes = mutableMapOf())))

        val result = BaseCommand(switchboard).test("ocr", "review", "-i", directory.toString())
        assertEquals(0, result.statusCode, result.output)
        assertEquals(1, calls)
    }

    @Test
    fun `finalise writes a CSV from an existing job`() = withSampleImage { directory, image ->
        val output = directory.resolve("result.csv")
        try {
            val switchboard = MockSwitchboard.with()
            val state = requireNotNull(switchboard.job.create(directory))
            state.update(mapOf(JobRelativePath(directory, image.fileName) to JobFile(vertexes = mutableMapOf())))
            val result = BaseCommand(switchboard).test("finalise", "-i", directory.toString(), "-o", output.toString())
            assertEquals(0, result.statusCode, result.output)
            assertTrue(Files.exists(output))
            assertTrue(Files.size(output) > 0)
        } finally {
            Files.deleteIfExists(output)
        }
    }

    @Test
    fun `log file and stderr use separate levels`() {
        val consumers = mutableSetOf<LogConsumer>()
        val init = object : InitInteraction {
            override fun init(logConsumers: Set<LogConsumer>) {
                consumers.clear()
                consumers.addAll(logConsumers)
            }

            override fun isEligible(file: Pair<JobRelativePath, JobFile>) = false
        }
        val switchboard = MockSwitchboard.with(init)
        val directory = Files.createTempDirectory("cli-logging-")
        val logFile = directory.resolve("archivetool.log")
        try {
            val result =
                BaseCommand(switchboard)
                    .test("-vv", "-L", logFile.toString(), "-Q", "status", "-i", directory.toString())
            assertEquals(0, result.statusCode, result.output)
            assertEquals(LogLevel.Trace, consumers.filterIsInstance<StderrConsumer>().single().maxLogLevel)
            val fileConsumer = consumers.filterIsInstance<FileConsumer>().single()
            assertEquals(LogLevel.Warn, fileConsumer.maxLogLevel)

            // FileConsumer is already part of the logging library.
            fileConsumer.recordLogEntry("Example log entry\n")
            assertContains(Files.readString(logFile), "Example log entry")
        } finally {
            Files.deleteIfExists(logFile)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `file logging can stay enabled when stderr is quiet`() {
        val consumers = mutableSetOf<LogConsumer>()
        val init = object : InitInteraction {
            override fun init(logConsumers: Set<LogConsumer>) {
                consumers.clear()
                consumers.addAll(logConsumers)
            }

            override fun isEligible(file: Pair<JobRelativePath, JobFile>) = false
        }
        val directory = Files.createTempDirectory("cli-quiet-logging-")
        try {
            val result = BaseCommand(MockSwitchboard.with(init)).test(
                "-qqq", "-L", directory.resolve("run.log").toString(), "-V",
                "status", "-i", directory.toString(),
            )
            assertEquals(0, result.statusCode, result.output)
            assertTrue(consumers.none { it is StderrConsumer })
            assertEquals(LogLevel.Debug, consumers.filterIsInstance<FileConsumer>().single().maxLogLevel)

            val missingFile = BaseCommand(MockSwitchboard).test("-V", "status", "-i", directory.toString())
            assertEquals(1, missingFile.statusCode)
            assertContains(missingFile.output, "--log-file")
        } finally {
            Files.deleteIfExists(directory)
        }
    }

    // A single image is enough to exercise the mock processing commands.
    private fun withSampleImage(block: (Path, Path) -> Unit) {
        val directory = Files.createTempDirectory("cli-test-")
        val image = directory.resolve("sample.jpg")
        try {
            Files.write(image, byteArrayOf(0x01, 0x02, 0x03))
            block(directory, image)
        } finally {
            Files.deleteIfExists(image)
            Files.deleteIfExists(directory)
        }
    }
}
