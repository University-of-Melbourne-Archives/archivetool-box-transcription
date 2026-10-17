package au.edu.unimelb.archivetool.python.interop.converters

import au.edu.unimelb.archivetool.python.interop.PythonToJvmConverter
import au.edu.unimelb.archivetool.switchboard.jobstate.BoxMetadata
import au.edu.unimelb.archivetool.switchboard.jobstate.RecordSetID
import au.edu.unimelb.archivetool.util.DateRange
import org.graalvm.polyglot.Value

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
