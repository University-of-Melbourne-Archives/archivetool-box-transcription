package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path

class FinaliseCommand : Command(name = "finalise") {

    override fun help(context: Context): String =
        """
        Finalise an approved job and generate its CSV output.

        The Switchboard loads the current job state and writes the export to the requested path.
        """
            .trimIndent()

    val outputPath by
        option("-o", "--output", help = "Path for the generated CSV file.")
            .path(mustExist = false, canBeDir = false)
            .required()

    override fun run() {
        TODO("Connect this command to the Switchboard")
    }
}
