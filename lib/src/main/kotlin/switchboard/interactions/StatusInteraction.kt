package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.switchboard.jobstate.memory.MemoryJobStateInteraction
import au.edu.unimelb.archivetool.util.extension.isNotNull
import au.edu.unimelb.archivetool.util.extension.isNull
import java.nio.file.Path

class StdoutStatusInteraction : StatusInteraction {
    override fun getStatus(directory: Path, files: Map<JobRelativePath, JobFile>?) =
        println(StringStatusInteraction().apply { getStatus(directory, files) }.result)
}

class StringStatusInteraction : StatusInteraction {
    var result: String = ""

    override fun getStatus(directory: Path, files: Map<JobRelativePath, JobFile>?) {
        if (files.isNull()) {
            if (MemoryJobStateInteraction().companion.present(directory)) {
                result = "There is an active job but there are no valid files in $directory."
            } else {
                result = "There is no active job in $directory."
            }
            return
        }

        val untouched = files?.count { (_, it) -> it.vertexes.isNullOrEmpty() && !it.approved }
        val segReview = files?.count { (_, it) -> it.vertexes.isNotNull() && !it.approved }
        val canOcr = files?.count { (_, jobFile) ->
            jobFile.vertexes?.values?.run {
                isNotEmpty() &&
                    any { box ->
                        box.metadata == null ||
                            box.keyedBoundaries.values.any { it.rawMetadata == null }
                    }
            } ?: false && jobFile.approved
        }
        val canExport = files?.count { (_, it) ->
            it.vertexes?.values?.all(ArchiveBox::isNotNull) ?: false
        }

        fun StringBuilder.appendLineIfPositive(count: Int?, line: (Int) -> String) =
            count?.takeIf { it > 0 }?.let { appendLine(line(it)) }

        result = buildString {
            appendLine("There are ${files?.size} valid files in directory $directory.")
            appendLine("Of these files:")
            appendLineIfPositive(untouched) { "- $it files have not been processed" }
            appendLineIfPositive(segReview) {
                "- $it files have been segmented into boxes and are ready for Manual Review"
            }
            appendLineIfPositive(canOcr) {
                "- $it files have been segmented and are ready to be transcribed"
            }
            // TODO: Maybe we should label reviewed files?
            appendLineIfPositive(canExport) {
                "- $it files have been transcribed, and are ready to review or export"
            }
        }
    }
}

interface StatusInteraction : Interaction {
    fun getStatus(directory: Path, files: Map<JobRelativePath, JobFile>?)

    // We want every file here.
    override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean = true

    companion object : ComposableInteraction<StatusInteraction> {
        override fun compose(vararg children: StatusInteraction): StatusInteraction {
            return object : StatusInteraction {
                override fun getStatus(directory: Path, files: Map<JobRelativePath, JobFile>?) {
                    children.forEach { it.getStatus(directory, files) }
                }

                override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean =
                    children.all {
                        it.isEligible(file)
                    }
            }
        }
    }
}
