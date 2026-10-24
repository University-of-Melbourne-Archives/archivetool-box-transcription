package ocr

import au.edu.unimelb.archivetool.config.OpenAIAPIData
import au.edu.unimelb.archivetool.ocr.OCRService
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OCRServiceProcessingTextTest {
    private val service = OCRService(OpenAIAPIData())

    // Tests for User Story 3: Unique Reference Number Reading
    @ParameterizedTest
    @ValueSource(strings = ["1964.0009", "85/7", "UMA-AC-000000056", "UMA-SRE-20160038"])
    fun `recognise each existing ID reference format`(id: String) {
        val result = service.processOutputText("Box label\n$id\nDate ranges: None")
        assertEquals(setOf(id), result.idStrings)
    }

    @Test
    fun `reference numbers are removed from description`() {
        val result = service.processOutputText("Australian Pulp and Paper Mills Ltd\n1974.0029, 1974.0030,\n1997.0138\nSome descriptions")
        assertEquals("Australian Pulp and Paper Mills Ltd; Some descriptions", result.description)
    }

    @Test
    fun `duplicate reference numbers collapse to one`() {
        val result = service.processOutputText("[UMA-AC-00000056] Records of\nthe Office of the Vice-Principal\n(University of Melbourne)\nUMA-AC-00000056")
        assertEquals(setOf("UMA-AC-00000056"), result.idStrings)
    }

    // Tests for User Story 7: Date range identification
    @Test
    fun `date ranges are extracted and removed from description`() {
        val result = service.processOutputText("Ballarat Paper Mills Pty Ltd\n\nDate ranges: 1957-01-01:1958-12-31")
        assertEquals("Ballarat Paper Mills Pty Ltd", result.description)
    }

    @Test
    fun `multiple date ranges stay comma separated`() {
        val result = service.processOutputText("THE UNIVERSITY OF MELBOURNE\n\nDate ranges: 1974-01-01: , 1974-01-01: , 1997-01-01: , 1957-01-01:1959-12-31")
        assertEquals("1974-01-01: , 1974-01-01: , 1997-01-01: , 1957-01-01:1959-12-31", result.dateRanges)
    }

    @Test
    fun `no date gives null`() {
        val result = service.processOutputText("THE UNIVERSITY OF MELBOURNE\n\nDate ranges: ")
        assertNull(result.dateRanges)
    }
}