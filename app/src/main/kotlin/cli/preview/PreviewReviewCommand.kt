package au.edu.unimelb.archivetool.app.cli.preview

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.jobDirectory
import com.github.ajalt.clikt.core.Context

// Handles: archivetool preview review
class PreviewReviewCommand : Command(name = "review") {

    override fun help(context: Context): String =
        "Open pending previews for review."

    // Existing job directory containing the previews.
    val jobDir by jobDirectory()

    override fun run() {
        TODO("Switchboard integration is not implemented yet")
    }
}