package ocr

import au.edu.unimelb.archivetool.config.OpenAIAPIData
import au.edu.unimelb.archivetool.ocr.OCRService
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "ARCHIVETOOL_API_KEY", matches = ".+")
class OCRLiveExecutionTest {
    private val service = OCRService(OpenAIAPIData())

    // - Each image will be tested based on User Stories 6 and 10.
    // - The `assertContains` tests implicitly covers User Story 6 (Extract text from sticker and spine label),
    //   since passing them means imply executeOCR() has the ability to extract texts from an isolated image.
    // - User Story 8 will not be tested since OCR output isn't deterministic, and what constitutes as "irrelevant"
    //   may differ from one person to another.

    // Disabled on CI because CI doesn't have an API key set-up.
    @DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS", matches = "true")
    @Test
    fun `extract info from test image 01`() {
        val image =
            JobRelativePath(
                Path(".").toAbsolutePath().parent,
                Path("src/test/resources/OCRTest/TestImages/clahe_test_01.png"),
            )

        val raw = service.executeOCR(image)
        val output = service.processOutputText(raw)

        assertContains(output.dateRanges.orEmpty(), "1957-01-01:1959-12-31")
        assertEquals(setOf("1974.0029", "1974.0030", "1997.0138"), output.idStrings)
        assertTrue(
            output.unitNumber == null ||
                    output.unitNumber.contains("Unit 1102", ignoreCase = true)
        )
        assertContains(output.description, "Australian Pulp and Paper Mills Ltd", ignoreCase = true)
        assertContains(output.description, "Materials - paper", ignoreCase = true)
        assertContains(output.description, "to Organization", ignoreCase = true)
        assertContains(output.description, "direct interests", ignoreCase = true)
    }


    // Disabled on CI because CI doesn't have an API key set-up.
    @DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS", matches = "true")
    @Test
    fun `extract info from test image 02`() {
        val image =
            JobRelativePath(
                Path(".").toAbsolutePath().parent,
                Path("src/test/resources/OCRTest/TestImages/clahe_test_02.png"),
            )

        val raw = service.executeOCR(image)
        val output = service.processOutputText(raw)

        assertContains(output.dateRanges.orEmpty(), "1966-01-01:1971-12-31")
        assertContains(output.dateRanges.orEmpty(), "1966-01-01:1972-12-31")
        assertTrue(
            listOf("1971-01-01:1971-12-31", "1971-01-01:").any { it in output.dateRanges.orEmpty() }
        )
        assertTrue(
            listOf("1969-01-01:1969-12-31", "1969-01-01:").any { it in output.dateRanges.orEmpty() }
        )
        assertContains(output.dateRanges.orEmpty(), "1967-01-01:1975-12-31")
        assertContains(output.dateRanges.orEmpty(), "1966-01-01:1975-12-31")
        assertEquals(setOf("UMA-AC-000000056"), output.idStrings)
        assertTrue(
            output.unitNumber == null ||
                    output.unitNumber.contains("Unit 0244", ignoreCase = true)
        )
        assertContains(output.description, "Press cuttings", ignoreCase = true)
        assertContains(output.description, Regex("1966[\\s]?-[\\s]?71"))
        assertContains(output.description, Regex("1966[\\s]?-[\\s]?72"))
        assertContains(output.description, "1971")
        assertContains(output.description, "1969")
        assertContains(output.description, Regex("1967[\\s]?-[\\s]?75"))
        assertContains(output.description, Regex("1966[\\s]?-[\\s]?75"))
    }

    // Disabled on CI because CI doesn't have an API key set-up.
    @DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS", matches = "true")
    @Test
    fun `extract info from test image 03`() {
        val image =
            JobRelativePath(
                Path(".").toAbsolutePath().parent,
                Path("src/test/resources/OCRTest/TestImages/clahe_test_03.png"),
            )

        val raw = service.executeOCR(image)
        val output = service.processOutputText(raw)

        assertContains(output.dateRanges.orEmpty(), "1957-01-01:1958-12-31")
        assertEquals(setOf("1974.0029", "1974.0030", "1997.0138"), output.idStrings)
        assertTrue(
            output.unitNumber == null ||
                    output.unitNumber.contains("Unit 1106", ignoreCase = true)
        )
        assertContains(output.description, "Ballarat Paper Mills P/L", ignoreCase = true)
        assertContains(output.description, "Queensland", ignoreCase = true)
        assertContains(output.description, "Merchants", ignoreCase = true)
    }

    // Disabled on CI because CI doesn't have an API key set-up.
    @DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS", matches = "true")
    @Test
    fun `extract info from test image 04`() {
        val image =
            JobRelativePath(
                Path(".").toAbsolutePath().parent,
                Path("src/test/resources/OCRTest/TestImages/clahe_test_04.png"),
            )

        val raw = service.executeOCR(image)
        val output = service.processOutputText(raw)

        assertContains(output.dateRanges.orEmpty(), "1957-01-01:1959-12-31")
        assertEquals(setOf("1974.0029", "1974.0030", "1997.0138"), output.idStrings)
        assertTrue(
            output.unitNumber == null ||
                    output.unitNumber.contains("Unit 1108", ignoreCase = true)
        )
        assertContains(output.description, "Ballarat Paper Mills Pty Ltd", ignoreCase = true)
        assertContains(output.description, Regex("1.[\\s]?Buildings[\\s]?-[\\s]?General"))
    }

    // Disabled on CI because CI doesn't have an API key set-up.
    @DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS", matches = "true")
    @Test
    fun `extract info from test image 05`() {
        val image =
            JobRelativePath(
                Path(".").toAbsolutePath().parent,
                Path("src/test/resources/OCRTest/TestImages/clahe_test_05.png"),
            )

        val raw = service.executeOCR(image)
        val output = service.processOutputText(raw)

        assertTrue(
            listOf("1974-01-01:1974-12-31", "1974-01-01:").any { it in output.dateRanges.orEmpty() }
        )
        assertTrue(
            listOf("1997-01-01:1997-12-31", "1997-01-01:").any { it in output.dateRanges.orEmpty() }
        )
        assertEquals(setOf("1974.0029", "1974.0030", "1997.0138"), output.idStrings)
        assertTrue(
            output.unitNumber == null ||
                    output.unitNumber.contains("Unit 1101", ignoreCase = true)
        )
        assertContains(output.description, "Ballarat Paper Mills Pty Ltd", ignoreCase = true)
        assertContains(output.description, "Shipping Documents", ignoreCase = true)
    }

}