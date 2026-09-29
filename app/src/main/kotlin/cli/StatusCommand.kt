package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.jobDirectory
import au.edu.unimelb.archivetool.switchboard.Switchboard
import com.github.ajalt.clikt.core.Context

class StatusCommand : Command(name = "status") {

    override fun help(context: Context): String =
        """
        Show the current status of an archive job.

        Reports the current stage and the number of approved and pending previews.
        """
            .trimIndent()

    val jobDir by jobDirectory()

    override fun run() {
        Switchboard.getStatus(jobDir.toAbsolutePath().normalize())
    }
}
