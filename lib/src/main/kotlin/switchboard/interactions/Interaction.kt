package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import java.nio.file.Path

// Currently just a marker,
// might be useful for implementing common tools like composition later, so is being kept.
sealed interface Interaction {
    fun isEligible(file: Pair<Path, JobFile>): Boolean
}
