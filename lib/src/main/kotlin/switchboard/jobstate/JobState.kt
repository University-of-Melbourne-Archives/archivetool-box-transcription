package au.edu.unimelb.archivetool.switchboard.jobstate

import au.edu.unimelb.archivetool.switchboard.interactions.Interaction
import au.edu.unimelb.archivetool.switchboard.jobstate.json.JSONJobState
import au.edu.unimelb.archivetool.switchboard.jobstate.memory.ConcreteJobState
import au.edu.unimelb.archivetool.util.extension.PairExt.map
import au.edu.unimelb.archivetool.util.extension.isNotNull
import au.edu.unimelb.archivetool.util.extension.noop
import au.edu.unimelb.archivetool.util.file.Image
import au.edu.unimelb.archivetool.util.logging.Log
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.isReadable
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

const val GENERATED_PATH = ".archivetool_generated/"
val GENERATED_PATH_REL_PAT: Regex = "/${Regex.escape(GENERATED_PATH)}".toRegex()

// Expects File Names relative to root dir.
abstract class JobState(
    val rootDir: Path,
    protected var files: MutableMap<Path, JobFile?> = mutableMapOf(),
    open val save: (folder: Path) -> Unit = {},
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
        val needsConversion = valid.filter { (k, v) -> !type.supported().supports(k) }
        Log.debug(
            "${valid.size} valid files, with ${needsConversion.size} files requiring conversion."
        )
        Log.trace("Valid files: ${valid.keys}")
        Log.trace("Needing Conversion: ${needsConversion.keys}")
        val converted =
            valid
                .mapKeys { (k, v) ->
                    if (needsConversion.containsKey(k))
                        return@mapKeys type.supported().trySupport(k)
                    return@mapKeys k
                }
                .filterKeys { it.isNotNull() }
                .mapKeys { (k, v) -> k!! }
        return converted
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
        save(rootDir)
    }

    fun scan(directory: Path, initial: Boolean = false) {
        val currentFiles =
            directory
                .walk()
                .map { it.relativeTo(directory) }
                .filter { ignore(it) }
                .map { JobRelativePath(directory, it) }

        val missingFiles = currentFiles.filter { !files.keys.contains(it.relative) }
        if (!initial && missingFiles.count() > 0) {
            val deletedFiles =
                files.keys.filter { f -> !currentFiles.map { it.relative }.contains(f) }

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

    private fun index(file: JobRelativePath) {
        files[file.relative] =
            file
                .let { file.absolute }
                .takeIf {
                    // Don't bother indexing non-images or files we can't read.
                    // Though we still need to store them as null to prevent rechecking them all the
                    // time
                    it.isReadable() && file.isImage()
                }
                ?.let { JobFile() }
    }

    private fun JobRelativePath.isImage(): Boolean = Image.FileType.detect(this).isNotNull()

    // Expects File Names relative to root dir.
    internal fun defaultIgnore(file: Path): Boolean {
        return !file.pathString.contains(GENERATED_PATH)
    }

    internal open fun ignore(file: Path): Boolean = defaultIgnore(file)

    companion object {
        fun generatedCropPaths(file: JobRelativePath, data: JobFile): Set<JobRelativePath> {
            if (data.vertexes.isNullOrEmpty()) return setOf()
            return data.vertexes.keys
                // TODO: Remove extension from the middle resolution here.
                .flatMap<String, JobRelativePath?> { boxName ->
                    data.vertexes[boxName]?.keyedBoundaries?.keys?.map { cropId ->
                        JobRelativePath(
                            file.rootDirectory,
                            Path(GENERATED_PATH)
                                .resolve(file.relative.pathString)
                                .resolve("$boxName-$cropId.${file.relative.extension}"),
                        )
                    } ?: setOf()
                }
                .filterNotNull()
                .toSet()
        }

        fun cropPath(file: JobRelativePath, boxId: String, cropId: String): JobRelativePath {
            return JobRelativePath(
                file.rootDirectory,
                Path(GENERATED_PATH)
                    .resolve(file.relative.pathString)
                    .resolve("${boxId}-${cropId}.${file.relative.extension}"),
            )
        }
    }
}

sealed interface JobStateVariant<T : JobState> {
    object JSON : JobStateVariant<JSONJobState>

    object Memory : JobStateVariant<ConcreteJobState>
}
