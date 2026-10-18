package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.asApprovalFraction
import au.edu.unimelb.archivetool.app.cli.helpers.currentJobRoot
import au.edu.unimelb.archivetool.app.cli.helpers.normalizedAbsolute
import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.io.path.Path

class CliCommandTest {

    @Test
    fun `help lists the main commands`() {
        val result = BaseCommand().test("--help")

        assertEquals(0, result.statusCode)
        assertContains(result.output, "run")
        assertContains(result.output, "preview")
        assertContains(result.output, "ocr")
        assertContains(result.output, "status")
        assertContains(result.output, "finalise")
    }

    @Test
    fun `run help exposes the current path interface`() {
        val result = BaseCommand().test("run --help")

        assertEquals(0, result.statusCode)
        assertContains(result.output, "--input-directory")
        assertContains(result.output, "--output")
        assertContains(result.output, "--no-resume")
        assertFalse(result.output.contains("--resume"))
        assertContains(result.output, "--auto-approve-threshold")
        assertFalse(result.output.contains("--job-directory"))
    }

    @Test
    fun `run requires an output path`() {
        val result = BaseCommand().test("run")

        assertEquals(1, result.statusCode)
        assertContains(result.output, "--output")
    }

    @Test
    fun `run rejects an auto approve threshold above one hundred`() {
        val result = BaseCommand().test("run -o result.csv -A 101")

        assertEquals(1, result.statusCode)
    }

    @Test
    fun `preview help lists create and review`() {
        val result = BaseCommand().test("preview --help")

        assertEquals(0, result.statusCode)
        assertContains(result.output, "create")
        assertContains(result.output, "review")
    }

    @Test
    fun `preview create defaults input directory to the current working directory`() {
        val result = BaseCommand().test("preview create --help")

        assertEquals(0, result.statusCode)
        assertContains(result.output, "--input-directory")
        assertContains(result.output, "current working directory")
        assertContains(result.output, "--no-resume")
        assertFalse(result.output.contains("--resume"))
    }

    @Test
    fun `run rejects the removed resume option`() {
        val result = BaseCommand().test("run -o result.csv --resume")

        assertEquals(1, result.statusCode)
    }

    @Test
    fun `preview create rejects the removed resume option`() {
        val result = BaseCommand().test("preview create --resume")

        assertEquals(1, result.statusCode)
    }

    @Test
    fun `run accepts no resume before checking required output`() {
        val result = BaseCommand().test("run --no-resume")

        assertEquals(1, result.statusCode)
        assertContains(result.output, "--output")
    }

    @Test
    fun `preview create no longer exposes output or auto approve options`() {
        val result = BaseCommand().test("preview create --help")

        assertEquals(0, result.statusCode)
        assertContains(result.output, "--input-directory")
        assertFalse(result.output.contains("--output"))
        assertFalse(result.output.contains("--auto-approve-threshold"))
    }

    @Test
    fun `preview review exposes auto approve threshold without a job directory`() {
        val result = BaseCommand().test("preview review --help")

        assertEquals(0, result.statusCode)
        assertContains(result.output, "--auto-approve-threshold")
        assertFalse(result.output.contains("Job Directory"))
    }

    @Test
    fun `preview review rejects an auto approve threshold above one hundred`() {
        val result = BaseCommand().test("preview review -A 101")

        assertEquals(1, result.statusCode)
    }

    @Test
    fun `ocr help lists create and review`() {
        val result = BaseCommand().test("ocr --help")

        assertEquals(0, result.statusCode)
        assertContains(result.output, "create")
        assertContains(result.output, "review")
    }

    @Test
    fun `ocr create is available without a job directory argument`() {
        val result = BaseCommand().test("ocr create --help")

        assertEquals(0, result.statusCode)
        assertContains(result.output, "OCR")
        assertFalse(result.output.contains("Job Directory"))
    }

    @Test
    fun `ocr review uses the current working directory`() {
        val result = BaseCommand().test("ocr review --help")

        assertEquals(0, result.statusCode)
        assertContains(result.output, "current working directory")
    }

    @Test
    fun `finalise requires a specific output path`() {
        val result = BaseCommand().test("finalise")

        assertEquals(1, result.statusCode)
        assertContains(result.output, "--output")
    }

    @Test
    fun `finalise no longer takes a job directory`() {
        val result = BaseCommand().test("finalise --help")

        assertEquals(0, result.statusCode)
        assertContains(result.output, "--output")
        assertFalse(result.output.contains("Job Directory"))
    }

    @Test
    fun `status still accepts a job directory`() {
        val result = BaseCommand().test("status --help")

        assertEquals(0, result.statusCode)
        assertContains(result.output, "<job directory>")
        assertContains(result.output, "input-photo directory")
    }

    @Test
    fun `run rejects an auto approve threshold below zero`() {
        val result = BaseCommand().test("run -o result.csv -A -1")
        assertEquals(1, result.statusCode)
    }

    @Test
    fun `run rejects a non numeric threshold`() {
        val result = BaseCommand().test("run -o result.csv -A invalid")
        assertEquals(1, result.statusCode)
    }

    @Test
    fun `run rejects a missing input directory`() {
        val result = BaseCommand().test("run -i __archivetool_missing_input_folder__ -o result.csv")
        assertEquals(1, result.statusCode)
    }

    @Test
    fun `preview review rejects a negative threshold`() {
        val result = BaseCommand().test("preview review -A -1")
        assertEquals(1, result.statusCode)
    }

    @Test
    fun `ocr create does not accept removed job directory flag`() {
        val result = BaseCommand().test("ocr create --job-directory unused")
        assertEquals(1, result.statusCode)
    }

    @Test
    fun `ocr review does not accept removed job directory flag`() {
        val result = BaseCommand().test("ocr review --job-directory unused")
        assertEquals(1, result.statusCode)
    }

    @Test
    fun `finalise does not accept an input directory flag`() {
        val result = BaseCommand().test("finalise -o result.csv --input-directory photos")
        assertEquals(1, result.statusCode)
    }

    @Test
    fun `run help describes the staged workflow`() {
        val result = BaseCommand().test("run --help")
        assertEquals(0, result.statusCode)
        assertContains(result.output, "workflow")
    }

    @Test
    fun `omitted approval threshold uses the existing one hundred percent default`() {
        assertEquals(1.0, (null as Int?).asApprovalFraction())
    }

    @Test
    fun `approval threshold converts percentages to fractions`() {
        assertEquals(0.0, 0.asApprovalFraction())
        assertEquals(0.95, 95.asApprovalFraction())
        assertEquals(1.0, 100.asApprovalFraction())
    }

    @Test
    fun `CLI input paths are normalized to absolute paths`() {
        val input = Path(".", "photos", "..", "photos")
        assertEquals(input.toAbsolutePath().normalize(), input.normalizedAbsolute())
    }

    @Test
    fun `current job root uses the current process directory`() {
        assertEquals(Path(".").toAbsolutePath().normalize(), currentJobRoot())
    }

    @Test
    fun `status help does not promise an implemented backend`() {
        val result = BaseCommand().test("status --help")
        assertEquals(0, result.statusCode)
        assertContains(result.output, "Switchboard")
    }

    @Test
    fun `finalise help describes export without OCR processing`() {
        val result = BaseCommand().test("finalise --help")
        assertEquals(0, result.statusCode)
        assertContains(result.output, "Export")
    }
}
