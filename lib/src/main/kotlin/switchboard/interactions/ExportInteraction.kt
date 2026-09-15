package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import java.nio.file.Path

class CSVExportInteraction(destination: Path) : ExportInteraction {
    override fun export(files: Map<JobRelativePath, JobFile>) {
        TODO("Concrete Implementation Incomplete.")
    }
}

class StdOutExportInteraction : ExportInteraction {
    override fun export(files: Map<JobRelativePath, JobFile>) {
        println(
            StringExportInteraction().run {
                export(files)
                get()
            }
        )
    }
}

class LogExportInteraction : ExportInteraction {
    override fun export(files: Map<JobRelativePath, JobFile>) {
        Log.trace(
            StringExportInteraction().run {
                export(files)
                get()
            }
        )
    }
}

class StringExportInteraction : ExportInteraction {
    val builder = StringBuilder()

    override fun export(files: Map<JobRelativePath, JobFile>) {
        files.forEach { file ->
            file.value.crops.forEach { (id, crop) ->
                builder.append("${file.key.relative}::$id: ${crop.transcription};;")
            }
        }
    }

    fun get() = builder.toString()
}

interface ExportInteraction : Interaction {
    fun export(files: Map<JobRelativePath, JobFile>)

    override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean =
        file.second.crops.all { it.value.transcription != null }

    companion object : ComposableInteraction<ExportInteraction> {
        override fun compose(vararg children: ExportInteraction): ExportInteraction {
            return object : ExportInteraction {
                override fun export(files: Map<JobRelativePath, JobFile>) {
                    children.forEach { it.export(files) }
                }

                override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean =
                    children.all {
                        it.isEligible(file)
                    }
            }
        }
    }
}
