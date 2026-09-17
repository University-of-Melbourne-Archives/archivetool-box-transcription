package au.edu.unimelb.archivetool.switchboard.jobstate.json

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.Log.expect
import au.edu.unimelb.archivetool.switchboard.interactions.JobStateInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.JobStateInteractionCompanion
import au.edu.unimelb.archivetool.switchboard.jobstate.GENERATED_PATH
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import au.edu.unimelb.archivetool.switchboard.jobstate.JobStateCache.Companion.getCache
import au.edu.unimelb.archivetool.switchboard.jobstate.JobStateVariant
import au.edu.unimelb.archivetool.switchboard.jobstate.json.JSONString.Companion.toStrictString
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions.asFileAttribute
import java.nio.file.attribute.PosixFilePermissions.fromString
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class JSONJobStateInteraction : JobStateInteraction {
    val variant = JobStateVariant.JSON

    override fun create(folder: Path): JobState {
        folder
            .resolve(GENERATED_PATH)
            .resolve("job.json")
            .takeIf { it.notExists() && it.parent.isWritable() }
            .expect("Could not create new Job State.")
            .createFile(asFileAttribute(fromString("rw-rw-r--")))

        return runBlocking {
            getCache<JobStateVariant.JSON, JSONJobState>(variant).write(Clock.System.now()).use {
                it.value.put(folder, JSONJobState(folder))
            }
        }
    }

    // TODO: Some sort of check for a file hash or something to detect user file changes that miss
    // cache.
    override fun read(folder: Path): JobState? = runBlocking {
        val state =
            getCache<JobStateVariant.JSON, JSONJobState>(variant).read(Clock.System.now()).use {
                it.value.get(folder)
            }

        if (state != null) return@runBlocking state

        val _try =
            try {
                val path =
                    folder.resolve(GENERATED_PATH).resolve("job.json").takeIf {
                        it.exists() && it.isReadable() && it.isWritable()
                    }
                JSONJobState(folder, Json.parseToJsonElement(path!!.readText()), ::write)
            } catch (e: Exception) {
                Log.error("Failed to load Job State: ${e.message ?: ""}")
                null
            }

        return@runBlocking _try?.also { try_ ->
            getCache<JobStateVariant.JSON, JSONJobState>(variant).write(Clock.System.now()).use {
                it.value.put(folder, try_)
            }
        }
    }

    override fun write(folder: Path) {
        runBlocking {
            getCache<JobStateVariant.JSON, JSONJobState>(variant).write(Clock.System.now()).use {
                lock ->
                val s =
                    lock.value
                        .get(folder)
                        .expect("Tried to write non-existent JSON State.")
                        .toJsonElement()
                        .toStrictString()
                // We stay locked here because we don't want other file writes at the same time.
                folder
                    .resolve(GENERATED_PATH)
                    .resolve("job.json")
                    .takeIf { it.isWritable() }
                    .expect("Could not write state to file: $this")
                    .writeText(
                        s,
                        Charsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                    )
            }
        }
    }

    override val companion: JobStateInteractionCompanion
        get() = Companion

    companion object : JobStateInteractionCompanion {
        override fun present(folder: Path): Boolean =
            folder.resolve(GENERATED_PATH).resolve("job.json").any {
                it.exists() && it.isReadable() && it.isWritable()
            }
    }
}
