package au.edu.unimelb.archivetool.app.cli.helpers

import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import kotlin.io.path.Path

/** Convert a CLI path to the absolute, normalised path expected by Switchboard. */
fun Path.normalizedAbsolute(): Path = toAbsolutePath().normalize()

/** Reusable optional input directory flag on `-i`. */
fun Command.inputDirectory() =
    option(
            "-i",
            "--input-directory",
            help = "Job directory that contains archive photos and saved progress",
        )
        .path(mustExist = true, canBeFile = false, mustBeReadable = true)
        .default(Path("."), defaultForHelp = "current working directory")
