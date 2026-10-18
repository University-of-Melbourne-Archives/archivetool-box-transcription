package au.edu.unimelb.archivetool.switchboard.interactions.export

import au.edu.unimelb.archivetool.switchboard.interactions.ExportInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.export.CSV.Companion.Field.Companion.computedRow
import au.edu.unimelb.archivetool.switchboard.interactions.export.CSV.Companion.Field.Companion.headRow
import au.edu.unimelb.archivetool.switchboard.interactions.export.CSV.Companion.toCSV
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

class CSVFileExportInteraction(val destination: Path, val impl: CSV = UoMCSV) : ExportInteraction {
    override fun export(files: Map<JobRelativePath, JobFile>) {
        val csv = files.toCSV(impl)
        CSV.writer.writeToFile(csv, destination.toFile())
    }
}

class CSVStringExportInteraction(val impl: CSV = UoMCSV) : ExportInteraction {
    private var sequence: Sequence<Char> = sequenceOf()

    val string
        get() = sequence.joinToString("")

    override fun export(files: Map<JobRelativePath, JobFile>) {
        val csv = files.toCSV(impl)
        sequence = CSV.writer.write(csv)
    }
}

interface CSV {
    val fields: List<Field>

    companion object {
        val writer by lazy { CsvWriter() }

        fun generateCSV(files: Map<JobRelativePath, JobFile>, impl: CSV): Sequence<List<String>> {
            val paramSets =
                mutableSetOf<Pair<Pair<JobRelativePath, JobFile>, Pair<String, ArchiveBox>>>()
            files.forEach { (path, file) ->
                file.vertexes?.forEach { (string, box) ->
                    paramSets.add((path to file) to (string to box))
                }
            }
            return sequence {
                yield(impl.fields.headRow)
                // TODO: Disambiguate and deduplicate on ID
                paramSets.forEach {
                    val row = impl.fields.computedRow(it)
                    if (row != null) yield(row)
                }
            }
        }

        fun String.CSVSafe(): String = replace('\n', ';')

        fun Map<JobRelativePath, JobFile>.toCSV(impl: CSV): Sequence<List<String>> = generateCSV(this, impl)

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
}
