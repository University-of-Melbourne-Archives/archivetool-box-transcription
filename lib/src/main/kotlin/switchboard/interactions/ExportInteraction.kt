package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath

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
        files.forEach { (path, file) ->
            file.vertexes?.forEach { (boxId, box) ->
                builder.append("${path.relative}::$boxId: ${box.metadata};;")
            }
        }
    }

    fun get() = builder.toString()
}

interface ExportInteraction : Interaction {
    fun export(files: Map<JobRelativePath, JobFile>)

    override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean =
        file.second.vertexes?.values?.all { box -> box.metadata != null } ?: false

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
