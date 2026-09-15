package au.edu.unimelb.archivetool.switchboard.jobstate

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.switchboard.interactions.Interaction
import au.edu.unimelb.archivetool.switchboard.jobstate.json.JSONJobState
import au.edu.unimelb.archivetool.switchboard.jobstate.memory.ConcreteJobState
import au.edu.unimelb.archivetool.util.map
import au.edu.unimelb.archivetool.util.noop
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.isReadable
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

const val GENERATED_PATH = ".archivetool_generated/"
val GENERATED_PATH_REL_PAT: Regex = "^${Regex.escape(GENERATED_PATH)}".toRegex()

// Expects File Names relative to root dir.
abstract class JobState(
    val rootDir: Path,
    protected var files: MutableMap<Path, JobFile?> = mutableMapOf(),
) {
    abstract val variant: JobStateVariant<*>

    fun validFiles(type: Interaction, allowNulls: Boolean = false): Map<JobRelativePath, JobFile> {
        Log.trace("Complete file list: ${files.keys}")
        val valid =
            files
                .mapKeys { JobRelativePath(rootDir, it.key) }
                .filter { p ->
                    p.toPair().takeIf {
                        (allowNulls || it.second != null) &&
                            (it.second == null || type.isEligible(it.map(::noop, { it!! })))
                    } != null
                }
                .mapValues { it.value ?: JobFile() }
        Log.trace("Valid files: ${valid.keys}")
        return valid
    }

    // TODO maybe check if state transitions are valid?
    fun update(partial: Map<JobRelativePath, JobFile>) {
        val toUpdate =
            partial
                .mapKeys { it.key.relative }
                .filterKeys { files.keys.contains(it) }
                .mapValues {
                    if (files[it.key] == null) {
                        Log.warn("Tried to update invalid file")
                        null
                    } else {
                        it.value
                    }
                }
        toUpdate.forEach { (path, body) -> files[path] = body }
    }

    fun scan(directory: Path, initial: Boolean = false) {
        val currentFiles = directory.walk().map { it.relativeTo(directory) }.filter { ignore(it) }

        val missingFiles = currentFiles.filter { !files.keys.contains(it) }
        if (!initial) {
            val deletedFiles = files.keys.filter { !currentFiles.contains(it) }

            // TODO Allow ignoring missing, removing deleted or suppressing warning or something.
            Log.warn(
                "Folder $directory has ${missingFiles.count()} files yet to be indexed. Indexing..."
            )
            missingFiles.forEach {
                Log.info("\t$it")
                index(it)
            }
            Log.info("Done Indexing.")
            Log.warn("${deletedFiles.count()} files have been deleted since indexing.")
        } else {
            Log.info(
                "Folder $directory has ${missingFiles.count()} files to be indexed. Indexing..."
            )
            missingFiles.forEach {
                Log.debug("\t$it")
                index(it)
            }
            Log.info("Done Indexing.")
        }
    }

    private fun index(file: Path) {
        files[file] =
            file
                .takeIf {
                    // Don't bother indexing non-images or files we can't read.
                    // Though we still need to store them as
                    it.isReadable() && it.isImage()
                }
                ?.let { JobFile() }
    }

    // TODO Better detection than just filename checks.
    private fun Path.isImage(): Boolean {
        return setOf("jpg", "png").contains(extension)
    }

    // Expects File Names relative to root dir.
    internal fun defaultIgnore(file: Path): Boolean {
        return !file.pathString.matches(GENERATED_PATH_REL_PAT)
    }

    internal open fun ignore(file: Path): Boolean = defaultIgnore(file)

    companion object {
        fun generatedCropPaths(file: JobRelativePath, data: JobFile): Set<JobRelativePath> {
            if (data.crops.isEmpty()) return setOf()
            return data.crops.keys
                // TODO: Remove extension from the middle resolution here.
                .map {
                    JobRelativePath(file.rootDirectory, Path(GENERATED_PATH).resolve(file.relative.pathString).resolve(it + file.relative.extension).relativeTo(file.rootDirectory))
                }
                .toSet()
        }
    }
}

sealed interface JobStateVariant<T : JobState> {
    object JSON : JobStateVariant<JSONJobState>

    object Memory : JobStateVariant<ConcreteJobState>
}
