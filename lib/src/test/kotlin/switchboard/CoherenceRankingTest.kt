package switchboard

import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.util.math.geometry.Quad
import kotlin.test.Test
import kotlin.test.assertEquals

class CoherenceRankingTest {
    fun boundary(confidence: Double) =
        Boundary(Quad.create(0 to 0, 10 to 0, 10 to 10, 0 to 10), confidence)

    fun jobFile(vararg boxes: ArchiveBox) =
        JobFile(boxes.mapIndexed { i, box -> "box-${i + 1}" to box }.toMap().toMutableMap())

    @Test
    fun minConfidenceIsLeastConfidentBoundary() {
        val file =
            jobFile(
                ArchiveBox(boundary(0.9), listOf(boundary(0.8))),
                ArchiveBox(boundary(0.7), listOf(boundary(0.3), boundary(0.95))),
            )
        assertEquals(0.3, file.minConfidence)
        // Files with no detections are treated as the least coherent
        assertEquals(-999.0, JobFile().minConfidence)
    }

    @Test
    fun leastCoherentFilesAreReviewedFirst() {
        val files: Map<String, JobFile?> =
            mapOf(
                "confident.jpg" to jobFile(ArchiveBox(boundary(0.9), listOf(boundary(0.85)))),
                "unsure.jpg" to jobFile(ArchiveBox(boundary(0.9), listOf(boundary(0.4)))),
                "unprocessed.jpg" to null,
                "empty.jpg" to JobFile(),
            )
        // Same ordering as SegmentationGUIInteraction.review
        val order = files.entries.sortedBy { it.value?.minConfidence ?: -999.0 }.map { it.key }
        assertEquals(listOf("unprocessed.jpg", "empty.jpg", "unsure.jpg", "confident.jpg"), order)
    }
}
