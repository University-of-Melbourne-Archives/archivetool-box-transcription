package au.edu.unimelb.archivetool.switchboard.jobstate.memory

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.switchboard.interactions.JobStateInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.JobStateInteractionCompanion
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import au.edu.unimelb.archivetool.switchboard.jobstate.JobStateCache.Companion.getCache
import au.edu.unimelb.archivetool.switchboard.jobstate.JobStateVariant
import java.nio.file.Path
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking

class MemoryJobStateInteraction : JobStateInteraction {
    val variant = JobStateVariant.Memory

    override fun create(folder: Path): JobState = runBlocking {
        getCache<JobStateVariant.Memory, ConcreteJobState>(variant).write(Clock.System.now()).use {
            it.value.put(folder, ConcreteJobState(folder, initial = true))
        }
    }

    // TODO: Some sort of check for a file hash or something to detect user file changes that miss
    // cache.
    override fun read(folder: Path): JobState = runBlocking {
        val state =
            getCache<JobStateVariant.Memory, ConcreteJobState>(variant)
                .read(Clock.System.now())
                .use { it.value.get(folder) }

        if (state != null) return@runBlocking state

        val new = ConcreteJobState(folder)

        return@runBlocking getCache<JobStateVariant.Memory, ConcreteJobState>(variant)
            .write(Clock.System.now())
            .use { it.value.put(folder, new) }
    }

    override fun write(folder: Path) {
        Log.debug("Noop writing in-memory job state.")
    }

    override val companion: JobStateInteractionCompanion
        get() = Companion

    companion object : JobStateInteractionCompanion {
        override fun present(folder: Path): Boolean = false
    }
}
