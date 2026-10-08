package au.edu.unimelb.archivetool.switchboard.interactions.export

import au.edu.unimelb.archivetool.switchboard.interactions.ExportInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.export.CSV.Field.Companion.computedRow
import au.edu.unimelb.archivetool.switchboard.interactions.export.CSV.Field.Companion.headRow
import au.edu.unimelb.archivetool.switchboard.interactions.export.CSV.toCSV
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.extension.with4IgnoredParameters
import com.jsoizo.kotlincsv.writer.CsvWriter
import com.jsoizo.kotlincsv.writer.writeToFile
import java.nio.file.Path

@Deprecated(
    "Renamed to disambiguate with CSVStringExportInteraction.",
    ReplaceWith("CSVFileExportInteraction"),
)
typealias CSVExportInteraction = CSVFileExportInteraction

class CSVFileExportInteraction(val destination: Path) : ExportInteraction {
    override fun export(files: Map<JobRelativePath, JobFile>) {
        val csv = files.toCSV()
        CSV.writer.writeToFile(csv, destination.toFile())
    }
}

class CSVStringExportInteraction : ExportInteraction {
    private var sequence: Sequence<Char> = sequenceOf()

    val string
        get() = sequence.joinToString("")

    override fun export(files: Map<JobRelativePath, JobFile>) {
        val csv = files.toCSV()
        sequence = CSV.writer.write(csv)
    }
}

object CSV {
    // The fields required for import into our Collection Management System.
    // Node ID     - Internal database key required to import the entry; cannot be inferred from
    // images of a box.
    //               It might be possible to have some additional input that maps "Node Title" to
    // this field.
    //               Would be a nice to have, but too complex for our timeframe, given the number of
    // label variants.
    // Node Type   - Always "Unit"
    // Node Title  - The canonical name of the box within the CMS.
    //                  This is handled by RecordSetID.
    // Description – The handwritten annotations on the box. '\n' should be replaced by ';'. This is
    // done internally.
    //                  The CSV library automatically quotes strings with commas.
    // Date Range  – This is a nice to have. The details of this are handled in util/DateRange.
    val CMSFields =
        Field.builder(
            "Node ID" to { "?????" }.with4IgnoredParameters(),
            "Node Type" to { "Item" }.with4IgnoredParameters(),
            "Node Title" to
                { _, _, _, box ->
                    box.metadata?.id?.CSVString(box.metadata.unitNumber ?: 0)
                },
            "Description" to
                { _, _, _, box ->
                    // TODO: Also transcribe any non-primary labels.
                    box.metadata?.description ?: ""
                },
            "Date Range" to { _, _, _, box -> box.metadata?.dateRange?.CSVString ?: "" },
        )
    // Additional Fields, anything not required for the CMS, but useful for manual review.
    val additionalFields = Field.builder()

    val fields = CMSFields + additionalFields
    val writer by lazy { CsvWriter() }

    fun generateCSV(files: Map<JobRelativePath, JobFile>): Sequence<List<String>> {
        val paramSets =
            mutableSetOf<Pair<Pair<JobRelativePath, JobFile>, Pair<String, ArchiveBox>>>()
        files.forEach { (path, file) ->
            file.vertexes?.forEach { (string, box) ->
                paramSets.add((path to file) to (string to box))
            }
        }
        return sequence {
            yield(fields.headRow)
            // TODO: Disambiguate and deduplicate on ID
            paramSets.forEach {
                val row = fields.computedRow(it)
                if (row != null) yield(row)
            }
        }
    }

    fun String.CSVSafe(): String = replace('\n', ';')

    fun Map<JobRelativePath, JobFile>.toCSV(): Sequence<List<String>> = generateCSV(this)

    // Path to file, File Internal Descriptor, vertex set ID.
    // Row is ignored if null.
    private typealias FieldComputeFun =
        (path: JobRelativePath, file: JobFile, boxId: String, box: ArchiveBox) -> String?

    data class Field(val name: String, val compute: FieldComputeFun) {
        constructor(pair: Pair<String, FieldComputeFun>) : this(pair.first, pair.second)

        companion object {
            fun builder(vararg fields: Pair<String, FieldComputeFun>): List<Field> =
                fields.map(::Field)

            val List<Field>.headRow: List<String>
                get() = map { it.name }

            fun List<Field>.computedRow(
                value: Pair<Pair<JobRelativePath, JobFile>, Pair<String, ArchiveBox>>
            ): List<String>? =
                map {
                        it.compute(
                            value.first.first,
                            value.first.second,
                            value.second.first,
                            value.second.second,
                        )
                    }
                    .takeUnless { l -> l.any { it == null } }
                    ?.map { it!!.CSVSafe() }
        }
    }
}
