package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.parameters.types.restrictTo
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable
import kotlin.io.path.listDirectoryEntries

// Handles: archivetool run
class RunCommand : Command(name = "run") {

    override fun help(context: Context): String =
        """
        Run the complete archive processing workflow.

        Start a new job with input and job directories, or resume an existing job.
        """
            .trimIndent()

    // Directory containing the original archive images for a new job.
    val inputDirectory by
        option("-i", "--input-directory", help = "A directory containing photos of archive boxes.")
            .path(mustExist = true, canBeFile = false, mustBeReadable = true)

    // Directory used to store a new job.
    val jobDirectory by
        option("-o", "--job-directory", help = "A new directory, or an existing empty directory.")
            .path(mustExist = false, canBeFile = false)
            .check("must be new or an empty, readable and writable directory") {
                !it.exists() ||
                    (it.isReadable() && it.isWritable() && it.listDirectoryEntries().isEmpty())
            }

    // Existing job directory to resume from its saved state.
    val resumeDirectory by
        option("--resume", metavar = "job-directory", help = "Resume an existing archive job.")
            .path(mustExist = true, canBeFile = false, mustBeReadable = true)

    // Optional threshold for automatically approving detected previews.
    val autoApproveThreshold by
        option(
                "-A",
                "--auto-approve-threshold",
                metavar = "threshold",
                help = "Automatically approve previews with confidence at or above <threshold>%.",
            )
            .int()
            .restrictTo(0, 100)

    override fun run() {
        if (resumeDirectory != null && (inputDirectory != null || jobDirectory != null)) {
            throw UsageError("--resume cannot be used with --input-directory or --job-directory")
        }

        if (resumeDirectory == null && (inputDirectory == null || jobDirectory == null)) {
            throw UsageError(
                "--input-directory and --job-directory are required unless --resume is used"
            )
        }

        TODO("Connect this command to the Switchboard")
    }
}
