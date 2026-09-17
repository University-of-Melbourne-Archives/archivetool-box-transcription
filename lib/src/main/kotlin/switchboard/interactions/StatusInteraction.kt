package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import java.nio.file.Path

class StdoutStatusInteraction : StatusInteraction {
    override fun getStatus() {
        TODO("Not yet implemented")
    }
}

class StringStatusInteraction : StatusInteraction {
    override fun getStatus() {
        TODO("Not yet implemented")
    }
}

interface StatusInteraction : Interaction {
    fun getStatus()

    // We want every file here.
    override fun isEligible(file: Pair<Path, JobFile>): Boolean = true

    companion object : ComposableInteraction<StatusInteraction> {
        override fun compose(vararg children: StatusInteraction): StatusInteraction {
            return object : StatusInteraction {
                override fun getStatus() {
                    children.forEach { it.getStatus() }
                }

                override fun isEligible(file: Pair<Path, JobFile>): Boolean = children.all {
                    it.isEligible(file)
                }
            }
        }
    }
}
