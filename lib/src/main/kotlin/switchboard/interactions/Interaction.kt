package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath

sealed interface Interaction {
    fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean
}
