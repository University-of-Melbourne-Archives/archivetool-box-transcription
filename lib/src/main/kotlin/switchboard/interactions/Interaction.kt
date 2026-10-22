package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.file.Image

sealed interface Interaction {
    fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean

    fun mayRequireConversion() = false

    fun supported() = Image.FileType.supportsAll
}
