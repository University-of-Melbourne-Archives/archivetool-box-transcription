package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.jobDirectory
import com.github.ajalt.clikt.core.Context

// Handles: archivetool finalise [job-directory]
class FinaliseCommand : Command(name = "finalise") {

    override fun help(context: Context): String = """
        Finalise an approved job and generate its CSV output.

        Runs image processing, OCR and CSV export through the Switchboard.
    """.trimIndent()

    // Directory containing an existing archive job.
    val jobDir by jobDirectory()

    override fun run() {
        TODO("Connect this command to the Switchboard")
    }
}
