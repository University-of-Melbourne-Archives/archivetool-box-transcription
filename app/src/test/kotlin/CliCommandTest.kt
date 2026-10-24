package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.normalizedAbsolute
import au.edu.unimelb.archivetool.util.logging.LogConsumer
import com.github.ajalt.clikt.testing.test
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** CLI tests for the archivist's usable interface user story. */
class CliCommandTest {

    @Test
    fun `help shows the main commands`() {
        val result = BaseCommand().test("--help")
        assertEquals(0, result.statusCode)
        listOf("run", "preview", "ocr", "status", "finalise").forEach {
            assertContains(result.output, it)
        }
    }

    @Test
    fun `help explains the available stages and important options`() {
        val preview = BaseCommand().test("preview --help")
        val ocr = BaseCommand().test("ocr --help")
        assertEquals(0, preview.statusCode)
        assertEquals(0, ocr.statusCode)
        listOf("create", "review").forEach {
            assertContains(preview.output, it)
            assertContains(ocr.output, it)
        }

        val run = BaseCommand().test("run --help")
        assertEquals(0, run.statusCode)
        listOf("--input-directory", "--output", "--no-resume", "--auto-approve-threshold").forEach {
            assertContains(run.output, it)
        }

        val finalise = BaseCommand().test("finalise --help")
        assertEquals(0, finalise.statusCode)
        assertContains(finalise.output, "--input-directory")
        assertContains(finalise.output, "--output")
        assertContains(finalise.output, "CSV")
    }

    @Test
    fun `run and finalise require an output path`() {
        listOf("run", "finalise").forEach { command ->
            val backend = RecordingBackend()
            val result = BaseCommand(backend).test(command)
            assertEquals(1, result.statusCode, command)
            assertContains(result.output, "--output")
            assertTrue(backend.calls.isEmpty())
        }
    }

    @Test
    fun `a missing input folder is rejected before processing`() {
        val backend = RecordingBackend()
        val result =
            BaseCommand(backend).test("run -i __missing_archivetool_folder__ -o result.csv")
        assertEquals(1, result.statusCode)
        assertTrue(result.output.isNotBlank())
        assertTrue(backend.calls.isEmpty())
    }

    @Test
    fun `invalid approval thresholds are rejected`() {
        listOf(
                "run -o result.csv -A -1",
                "run -o result.csv -A 101",
                "run -o result.csv -A abc",
                "preview review -A -1",
                "preview review -A 101",
                "preview review -A abc",
            )
            .forEach { command ->
                val backend = RecordingBackend()
                val result = BaseCommand(backend).test(command)
                assertEquals(1, result.statusCode, command)
                assertTrue(backend.calls.isEmpty(), "Processing started for $command")
            }
    }

    @Test
    fun `unknown commands show an error without starting processing`() {
        val backend = RecordingBackend()
        val result = BaseCommand(backend).test("unknown-command")
        assertEquals(1, result.statusCode)
        assertTrue(result.output.isNotBlank())
        assertTrue(backend.calls.isEmpty())
    }

    @Test
    fun `run requests the complete workflow in order`() {
        val backend = RecordingBackend()
        val result = BaseCommand(backend).test("run -o result.csv")
        assertEquals(0, result.statusCode)
        assertEquals(
            listOf(
                "createPreview",
                "reviewPreview",
                "prepareImages",
                "createOCR",
                "reviewOCR",
                "export",
            ),
            backend.calls.map { it.name },
        )
    }

    @Test
    fun `run passes selected input and output paths to Switchboard`() {
        val backend = RecordingBackend()
        val result = BaseCommand(backend).test("run -i .. -o result.csv")
        assertEquals(0, result.statusCode)
        assertTrue(backend.calls.all { it.directory == Path("..").normalizedAbsolute() })
        assertEquals(Path("result.csv").normalizedAbsolute(), backend.calls.last().output)
    }

    @Test
    fun `run and preview create resume jobs unless disabled`() {
        listOf("run -o result.csv", "preview create").forEach { command ->
            val backend = RecordingBackend()
            assertEquals(0, BaseCommand(backend).test(command).statusCode)
            assertEquals(true, backend.calls.first().permitExisting, command)
        }
        listOf("run -o result.csv --no-resume", "preview create --no-resume").forEach { command ->
            val backend = RecordingBackend()
            assertEquals(0, BaseCommand(backend).test(command).statusCode)
            assertEquals(false, backend.calls.first().permitExisting, command)
        }
    }

    @Test
    fun `review thresholds use fractions when passed to Switchboard`() {
        val defaultBackend = RecordingBackend()
        assertEquals(0, BaseCommand(defaultBackend).test("preview review").statusCode)
        assertEquals(1.0, defaultBackend.calls.single().threshold)

        val reviewBackend = RecordingBackend()
        assertEquals(0, BaseCommand(reviewBackend).test("preview review -A 25").statusCode)
        assertEquals(0.25, reviewBackend.calls.single().threshold)

        val runBackend = RecordingBackend()
        assertEquals(0, BaseCommand(runBackend).test("run -o result.csv -A 75").statusCode)
        assertEquals(0.75, runBackend.calls[1].threshold)

        listOf(0 to 0.0, 100 to 1.0).forEach { (percent, fraction) ->
            val backend = RecordingBackend()
            assertEquals(0, BaseCommand(backend).test("preview review -A $percent").statusCode)
            assertEquals(fraction, backend.calls.single().threshold)
        }
    }

    @Test
    fun `preview commands call the matching stages`() {
        val create = RecordingBackend()
        assertEquals(0, BaseCommand(create).test("preview create").statusCode)
        assertEquals(listOf("createPreview"), create.calls.map { it.name })

        val review = RecordingBackend()
        assertEquals(0, BaseCommand(review).test("preview review").statusCode)
        assertEquals(listOf("reviewPreview"), review.calls.map { it.name })
    }

    @Test
    fun `OCR commands create results and then allow review`() {
        val create = RecordingBackend()
        assertEquals(0, BaseCommand(create).test("ocr create").statusCode)
        assertEquals(listOf("prepareImages", "createOCR"), create.calls.map { it.name })

        val review = RecordingBackend()
        assertEquals(0, BaseCommand(review).test("ocr review").statusCode)
        assertEquals(listOf("reviewOCR"), review.calls.map { it.name })
    }

    @Test
    fun `status asks Switchboard for the job progress`() {
        val backend = RecordingBackend()
        assertEquals(0, BaseCommand(backend).test("status -i ..").statusCode)
        assertEquals(listOf("status"), backend.calls.map { it.name })
        assertEquals(Path("..").normalizedAbsolute(), backend.calls.single().directory)
    }

    @Test
    fun `finalise only exports existing results to the selected CSV`() {
        val backend = RecordingBackend()
        assertEquals(0, BaseCommand(backend).test("finalise -i .. -o result.csv").statusCode)
        assertEquals(listOf("export"), backend.calls.map { it.name })
        assertEquals(Path("..").normalizedAbsolute(), backend.calls.single().directory)
        assertEquals(Path("result.csv").normalizedAbsolute(), backend.calls.single().output)
    }

    @Test
    fun `every individual processing command accepts an input directory`() {
        listOf(
                "preview create -i ..",
                "preview review -i ..",
                "ocr create -i ..",
                "ocr review -i ..",
                "status -i ..",
                "finalise -i .. -o result.csv",
            )
            .forEach { command ->
                val backend = RecordingBackend()
                val result = BaseCommand(backend).test(command)
                assertEquals(0, result.statusCode, command)
                assertTrue(backend.calls.isNotEmpty(), command)
                assertTrue(
                    backend.calls.all { it.directory == Path("..").normalizedAbsolute() },
                    command,
                )
            }
    }

    private data class Call(
        val name: String,
        val directory: Path,
        val permitExisting: Boolean? = null,
        val threshold: Double? = null,
        val output: Path? = null,
    )

    // Records calls instead of running OCR, GUI windows or file exports.
    private class RecordingBackend : CliBackend {
        val calls = mutableListOf<Call>()

        override fun init(consumers: Set<LogConsumer>) = Unit

        override fun createPreview(directory: Path, permitExisting: Boolean) {
            calls += Call("createPreview", directory, permitExisting = permitExisting)
        }

        override fun reviewPreview(directory: Path, threshold: Double) {
            calls += Call("reviewPreview", directory, threshold = threshold)
        }

        override fun prepareImages(directory: Path) {
            calls += Call("prepareImages", directory)
        }

        override fun createOCR(directory: Path) {
            calls += Call("createOCR", directory)
        }

        override fun reviewOCR(directory: Path) {
            calls += Call("reviewOCR", directory)
        }

        override fun status(directory: Path) {
            calls += Call("status", directory)
        }

        override fun export(directory: Path, output: Path) {
            calls += Call("export", directory, output = output)
        }
    }
}
