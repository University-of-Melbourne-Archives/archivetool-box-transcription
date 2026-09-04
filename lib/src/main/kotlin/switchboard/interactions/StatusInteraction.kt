package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import java.nio.file.Path

class UnimplementedStatusInteraction : StatusInteraction {
    override fun getStatus() {
        TODO("Not yet implemented")
    }
}

interface StatusInteraction : Interaction {
    fun getStatus()

    // We want every file here.
    override fun isEligible(file: Pair<Path, JobFile>): Boolean = true
}
