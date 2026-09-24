package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import kotlin.io.path.Path

class FinaliseCommand : Command(name = "finalise") {

    override fun help(context: Context): String =
        """
        Finalise an approved job and generate its CSV output.

        The current working directory is used as the job root.
        """
            .trimIndent()

    val outputPath by
        option("-o", "--output", help = "Path for the generated CSV file.")
            .path(mustExist = false, canBeDir = false)
            .required()

    override fun run() {
        CliSwitchboard.finalise(Path("."), outputPath)
    }
}
