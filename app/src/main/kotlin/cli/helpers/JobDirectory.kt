package au.edu.unimelb.archivetool.app.cli.helpers

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.types.path
import kotlin.io.path.Path

fun Command.jobDirectory() =
    argument("Job Directory", "The input-photo directory used as the archive job root.")
        .path(mustExist = true, canBeFile = false, mustBeReadable = true)
        .default(Path("."), defaultForHelp = "current working directory")
