package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import java.nio.file.Path

class CSVExportInteraction : ExportInteraction {
    override fun export(files: Map<Path, JobFile>) {
        TODO("Concrete Implementation Incomplete.")
    }
}

class StdOutExportInteraction : ExportInteraction {
    override fun export(files: Map<Path, JobFile>) {
        println(
            StringExportInteraction().run {
                export(files)
                get()
            }
        )
    }
}

class StringExportInteraction : ExportInteraction {
    val builder = StringBuilder()

    override fun export(files: Map<Path, JobFile>) {
        files.forEach { file ->
            file.value.crops.forEach { (id, crop) -> builder.append("$id: ${crop.transcription}") }
        }
    }

    fun get() = builder.toString()
}

interface ExportInteraction : Interaction {
    fun export(files: Map<Path, JobFile>)

    override fun isEligible(file: Pair<Path, JobFile>): Boolean =
        file.second.crops.all { it.value.transcription != null }

    companion object : ComposableInteraction<ExportInteraction> {
        override fun compose(vararg children: ExportInteraction): ExportInteraction {
            return object : ExportInteraction {
                override fun export(files: Map<Path, JobFile>) {
                    children.forEach { it.export(files) }
                }

                override fun isEligible(file: Pair<Path, JobFile>): Boolean = children.all {
                    it.isEligible(file)
                }
            }
        }
    }
}
