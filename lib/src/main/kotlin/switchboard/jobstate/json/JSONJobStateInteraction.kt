package au.edu.unimelb.archivetool.switchboard.jobstate.json

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.Log.expect
import au.edu.unimelb.archivetool.switchboard.interactions.JobStateInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.JobStateInteractionCompanion
import au.edu.unimelb.archivetool.switchboard.jobstate.GENERATED_PATH
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import au.edu.unimelb.archivetool.switchboard.jobstate.JobStateCache.Companion.getCache
import au.edu.unimelb.archivetool.util.validate
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.json.Json

class JSONJobStateInteraction : JobStateInteraction {
    override fun create(folder: Path): JobState {
        folder
            .resolve(GENERATED_PATH)
            .resolve("job.json")
            .validate { it.notExists() && it.parent.isWritable() }
            .expect("Could not create new Job State.")

        return getCache<JSONJobState>().put(folder, JSONJobState(folder))
    }

    // TODO: Some sort of check for a file hash or something to detect user file changes that miss cache.
    override fun read(folder: Path): JobState? =
        getCache<JSONJobState>().getOrTry(folder) {
            try {
                val path =
                    folder.resolve(GENERATED_PATH).resolve("job.json").validate {
                        it.exists() && it.isReadable() && it.isWritable()
                    }
                JSONJobState(folder, Json.parseToJsonElement(path!!.readText()))
            } catch (e: Exception) {
                Log.error("Failed to load Job State: ${e.message ?: ""}")
                null
            }
        }

    override fun write(folder: Path) {
        val s =
            getCache<JSONJobState>()
                .get(folder)
                .expect("Tried to write non-existent JSON State.")
                .toJsonElement()
                .toString()
        folder
            .resolve(GENERATED_PATH)
            .resolve("job.json")
            .validate { it.isWritable() }
            .expect("Could not write state to file: $this")
            .writeText(
                s,
                Charsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
    }

    companion object : JobStateInteractionCompanion {
        override fun present(folder: Path): Boolean =
            folder.resolve(GENERATED_PATH).resolve("job.json").first().validate {
                it.exists() && it.isReadable() && it.isWritable()
            } == null
    }
}
