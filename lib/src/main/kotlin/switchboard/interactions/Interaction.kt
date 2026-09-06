package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import java.nio.file.Path

sealed interface Interaction {
    fun isEligible(file: Pair<Path, JobFile>): Boolean
}
