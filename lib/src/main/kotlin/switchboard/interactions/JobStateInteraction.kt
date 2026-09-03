package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import java.nio.file.Path

// This interaction is not called from the Switchboard like the others,
// it is injected at call-time into other switchboard components.
// It also requires some extra work to allow for migration seamlessly from one format to another.
// The migration is enabled simply by detecting any existing formats and delegating an initial read
// to them.
interface JobStateInteraction : Interaction {
    fun create(folder: Path): JobState?

    fun read(folder: Path): JobState?

    fun write(folder: Path)
}

interface JobStateInteractionCompanion {
    // A rule to detect if a Job state exists for a given folder. Default Implementation just
    // returns false.
    fun present(folder: Path): Boolean {
        return false
    }
}
