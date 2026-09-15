package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath

class StdoutStatusInteraction : StatusInteraction {
    override fun getStatus(files: Map<JobRelativePath, JobFile>?) {
        TODO("Not yet implemented")
    }
}

class StringStatusInteraction : StatusInteraction {
    override fun getStatus(files: Map<JobRelativePath, JobFile>?) {
        TODO("Not yet implemented")
    }
}

interface StatusInteraction : Interaction {
    fun getStatus(files: Map<JobRelativePath, JobFile>?)

    // We want every file here.
    override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean = true

    companion object : ComposableInteraction<StatusInteraction> {
        override fun compose(vararg children: StatusInteraction): StatusInteraction {
            return object : StatusInteraction {
                override fun getStatus(files: Map<JobRelativePath, JobFile>?) {
                    children.forEach { it.getStatus(files) }
                }

                override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean =
                    children.all {
                        it.isEligible(file)
                    }
            }
        }
    }
}
