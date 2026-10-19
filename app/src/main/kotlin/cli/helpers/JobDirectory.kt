package au.edu.unimelb.archivetool.app.cli.helpers

import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import kotlin.io.path.Path

/** Convert a CLI path to the absolute, normalised path expected by Switchboard. */
fun Path.normalizedAbsolute(): Path = toAbsolutePath().normalize()

/** Reusable optional positional job directory, currently used by `status`. */
fun Command.inputDirectory() =
    option("-i", "--input-directory", help = "A directory containing photos of archive boxes.")
        .path(mustExist = true, canBeFile = false, mustBeReadable = true)
        .default(Path("."), defaultForHelp = "current working directory")
