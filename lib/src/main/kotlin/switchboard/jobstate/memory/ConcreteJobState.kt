package au.edu.unimelb.archivetool.switchboard.jobstate.memory

import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import au.edu.unimelb.archivetool.switchboard.jobstate.JobStateVariant
import java.nio.file.Path

// TODO make stuff to automate creating partial valid states.

class ConcreteJobState(
    val directory: Path,
    initial: Boolean = false,
    starterState: MutableMap<Path, JobFile?> = mutableMapOf(),
) : JobState(starterState) {
    init {
        scan(directory, initial)
    }

    override val variant = JobStateVariant.Memory
}
