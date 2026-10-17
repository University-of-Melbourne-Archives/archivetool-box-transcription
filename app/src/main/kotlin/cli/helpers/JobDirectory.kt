package au.edu.unimelb.archivetool.app.cli.helpers

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import kotlin.io.path.Path

/** The directory containing input images is the root of an archive job. */
fun currentJobRoot(): Path = Path(".").toAbsolutePath().normalize()

/** Convert a CLI path to the absolute, normalised path expected by Switchboard. */
fun Path.normalizedAbsolute(): Path = toAbsolutePath().normalize()

/**
 * Convert a validated CLI percentage (0-100) to the 0.0-1.0 scale expected by Switchboard. An
 * omitted option uses 1.0, matching the original CLI default.
 */
fun Int?.asApprovalFraction(): Double = (this ?: 100).toDouble() / 100.0

/** Reusable optional positional job directory, currently used by `status`. */
fun Command.jobDirectory() =
    argument("Job Directory", "The input-photo directory used as the archive job root.")
        .path(mustExist = true, canBeFile = false, mustBeReadable = true)
        .default(Path("."), defaultForHelp = "current working directory")
