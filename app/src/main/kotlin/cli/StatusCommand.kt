package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.jobDirectory
import au.edu.unimelb.archivetool.app.cli.helpers.normalizedAbsolute
import au.edu.unimelb.archivetool.switchboard.Switchboard
import com.github.ajalt.clikt.core.Context

class StatusCommand : Command(name = "status") {

    override fun help(context: Context): String =
        """
        Request the current status of an archive job.

        Status output requires a Switchboard backend implementation.
        """
            .trimIndent()

    private val jobDir by jobDirectory()

    override fun run() {
        Switchboard.getStatus(jobDir.normalizedAbsolute())
    }
}
