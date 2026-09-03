package au.edu.unimelb.archivetool.app.cli.preview

import au.edu.unimelb.archivetool.app.cli.Cli
import au.edu.unimelb.archivetool.app.cli.helpers.Command
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.parameters.types.restrictTo
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable
import kotlin.io.path.listDirectoryEntries

// Handles: archivetool preview create
class PreviewCreateCommand : Command(name = "create") {

    override fun help(context: Context): String = """
        Create previews from a directory of archive images.

        The generated previews and job state will be stored in the job directory.
    """.trimIndent()

    // Directory containing the original archive images.
    val inputDirectory by option(
        "-i",
        "--input-directory",
        help = "A directory containing photos of archive boxes."
    ).path(
        mustExist = true,
        canBeFile = false,
        mustBeReadable = true
    ).required()

    // Directory used to store the job state and generated previews.
    val jobDirectory by option(
        "-o",
        "--job-directory",
        help = "A new directory, or an existing empty directory.${Cli.newLineChar}" +
            "It will store the job state and generated previews."
    ).path(
        mustExist = false,
        canBeFile = false
    ).required().check("must be new or an empty, readable and writable directory") {
        !it.exists() ||
            (it.isReadable() && it.isWritable() && it.listDirectoryEntries().isEmpty())
    }

    // Automatically approve results at or above this confidence percentage.
    val autoApproveThreshold by option(
        "-A",
        "--auto-approve-threshold",
        metavar = "threshold",
        help = "Automatically approve detected previews with confidence at or above <threshold>%."
    ).int().restrictTo(0, 100)

    override fun run() {
        TODO("Connect this command to the Switchboard")
    }
}
