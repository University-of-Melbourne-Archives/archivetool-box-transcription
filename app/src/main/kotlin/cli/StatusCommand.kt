package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.inputDirectory
import au.edu.unimelb.archivetool.app.cli.helpers.normalizedAbsolute
import com.github.ajalt.clikt.core.Context

class StatusCommand(private val backend: CliBackend) : Command(name = "status") {

    override fun help(context: Context): String =
        """
        Show the progress of the job in the selected directory.
        """
            .trimIndent()

    private val inputDirectory by inputDirectory()

    override fun run() {
        backend.status(inputDirectory.normalizedAbsolute())
    }
}
