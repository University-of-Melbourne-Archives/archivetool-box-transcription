package au.edu.unimelb.archivetool.switchboard.jobstate.memory

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.switchboard.interactions.JobStateInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.JobStateInteractionCompanion
import au.edu.unimelb.archivetool.switchboard.jobstate.GENERATED_PATH
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import au.edu.unimelb.archivetool.switchboard.jobstate.JobStateCache.Companion.getCache
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable

class MemoryJobStateInteraction : JobStateInteraction {
    override fun create(folder: Path): JobState {
        return getCache<ConcreteJobState>().put(folder, ConcreteJobState(folder))
    }

    // TODO: Some sort of check for a file hash or something to detect user file changes that miss
    // cache.
    override fun read(folder: Path): JobState? =
        getCache<ConcreteJobState>().getOrTry(folder) { ConcreteJobState(folder) }

    override fun write(folder: Path) {
        Log.debug("Noop writing in-memory job state.")
    }

    override val companion: JobStateInteractionCompanion
        get() = Companion

    companion object : JobStateInteractionCompanion {
        override fun present(folder: Path): Boolean =
            folder.resolve(GENERATED_PATH).resolve("job.json").first().takeIf {
                it.exists() && it.isReadable() && it.isWritable()
            } == null
    }
}
