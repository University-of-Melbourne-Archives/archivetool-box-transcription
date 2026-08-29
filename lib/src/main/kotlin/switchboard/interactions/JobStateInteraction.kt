package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.util.validate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText


abstract class JobState

// This interaction is not called from the Switchboard like the others,
// it is injected at call-time into other switchboard components.
// It also requires some extra work to allow for migration seamlessly from one format to another.
// The migration is enabled simply by detecting any existing formats and delegating an initial read to them.
// TODO: This may actually need to be a Abstract Class, because it will likely need to have the job state members on it.
interface JobStateInteraction : Interaction {
    fun read(folder: Path): JobState?
}
interface JobStateInteractionCompanion {
    // A rule to detect if a Job state exists for a given folder. Default Implementation just returns false.
    fun detectStatePresent(folder: Path): Boolean {
        return false
    }
}

@Serializable
data class JSONJobState(val nothing: Unit): JobState()

class JSONJobStateInteraction() : JobStateInteraction {
    override fun read(folder: Path): JobState? {
        val path = folder.listDirectoryEntries("job.json").first()
        return Json.decodeFromString<JSONJobState>(path.readText())
    }
    companion object : JobStateInteractionCompanion {
        override fun detectStatePresent(folder: Path): Boolean {
            return folder.listDirectoryEntries("job.json").first()
                .validate {
                    it.exists() && it.isReadable() && it.isWritable()
                } == null
        }
    }
}