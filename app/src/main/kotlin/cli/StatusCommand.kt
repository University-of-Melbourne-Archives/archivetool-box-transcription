package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.jobDirectory
import com.github.ajalt.clikt.core.Context

// Handles: archivetool status [job-directory]
class StatusCommand : Command(name = "status") {

    override fun help(context: Context): String =
        """
        Show the current status of an archive job.

        Reports the current stage and the number of approved and pending previews.
        """
            .trimIndent()

    // Directory containing an existing archive job.
    val jobDir by jobDirectory()

    override fun run() {
        TODO("Connect this command to the Switchboard")
    }
}
