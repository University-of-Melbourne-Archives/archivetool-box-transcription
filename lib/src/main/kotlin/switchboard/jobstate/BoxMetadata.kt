package au.edu.unimelb.archivetool.switchboard.jobstate

import au.edu.unimelb.archivetool.pythoninterop.PythonToJvmConverter
import au.edu.unimelb.archivetool.pythoninterop.converters.SetConverter
import au.edu.unimelb.archivetool.util.DateRange
import au.edu.unimelb.archivetool.util.DateRange.Companion.tightestBounds
import au.edu.unimelb.archivetool.util.extension.counted
import kotlinx.serialization.Serializable
import org.graalvm.polyglot.Value

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

object BoxMetadataConverter : PythonToJvmConverter<BoxMetadata> {
    // TODO: this should fail safer.
    override fun toJvmValue(polyglotValue: Value): BoxMetadata {
        assert(polyglotValue.hasMembers())
        val id = id(polyglotValue)
        val description = description(polyglotValue) ?: ""
        val dateRange = dateRange(polyglotValue)
        val unitNumber = unitNumber(polyglotValue)
        return BoxMetadata(id, unitNumber, dateRange, description)
    }

    private fun id(polyglotValue: Value): RecordSetID? =
        if (polyglotValue.hasMember("ID strings")) {
            RecordSetID.fromOCROutput(
                SetConverter.toJvmValue(
                        polyglotValue.takeIf { it.hasMember("ID strings") }?.getMember("ID strings")
                            ?: return null
                    )
                    .map { it.asString() }
            )
        } else {
            null
        }

    private fun description(polyglotValue: Value) =
        polyglotValue.getMember("Description")?.asString()

    private fun unitNumber(polyglotValue: Value) =
        if (polyglotValue.hasMember("Unit ID")) {
            "\\d{1,4}".toRegex().find(polyglotValue.getMember("Unit ID").asString())?.value?.toInt()
        } else {
            null
        }

    private fun dateRange(polyglotValue: Value) =
        if (polyglotValue.hasMember("Date ranges")) {
            DateRange.fromOCRoutput(polyglotValue.getMember("Date ranges").asString())
        } else {
            null
        }
}
