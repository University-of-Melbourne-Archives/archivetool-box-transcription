package au.edu.unimelb.archivetool.switchboard.jobstate

import au.edu.unimelb.archivetool.util.DateRange
import au.edu.unimelb.archivetool.util.DateRange.Companion.tightestBounds
import au.edu.unimelb.archivetool.util.extension.counted
import kotlinx.serialization.Serializable

@Serializable
data class BoxMetadata(
    val id: RecordSetID?,
    val unitNumber: Int?,
    val dateRange: DateRange?,
    val description: String,
) {
    companion object {
        fun bestGuess(boxBoundary: Boundary?, labelBoundaries: List<Boundary>): BoxMetadata {
            val boxMeta = boxBoundary?.rawMetadata
            val labelMeta = labelBoundaries.map { it.rawMetadata }
            val allMeta = listOf(boxMeta, *labelMeta.toTypedArray())
            val ids = labelMeta.mapNotNull { it?.id }
            val id = RecordSetID.bestGuess(ids)
            val relevantLabels = labelMeta.filter { it?.id == id }
            val unitNumber =
                relevantLabels
                    .mapNotNull { it?.unitNumber }
                    .counted()
                    .maxByOrNull { it.first }
                    ?.second
            val dateRange = allMeta.mapNotNull { it?.dateRange }.tightestBounds()
            val description = allMeta.joinToString(";") { it?.description ?: "" }

            return BoxMetadata(id, unitNumber, dateRange, description)
        }
    }
}

fun HashMap<String, String>.toBoxMetadata(): BoxMetadata = TODO()
