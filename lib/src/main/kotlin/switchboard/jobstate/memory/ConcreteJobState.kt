package au.edu.unimelb.archivetool.switchboard.jobstate.memory

import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import java.nio.file.Path

// TODO make stuff to automate creating partial valid states.

class ConcreteJobState(val directory: Path) : JobState() {
    init {
        scan(directory, initial = true)
    }
}
