package au.edu.unimelb.archivetool.app.cli.preview

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path

class PreviewCreateCommand : Command(name = "create") {

    override fun help(context: Context): String =
        """
        Create previews from a directory of archive images.

        Job state and intermediate files are managed by the Switchboard.
        """
            .trimIndent()

    val inputDirectory by
        option("-i", "--input-directory", help = "A directory containing photos of archive boxes.")
            .path(mustExist = true, canBeFile = false, mustBeReadable = true)
            .required()

    override fun run() {
        TODO("Connect this command to the Switchboard")
    }
}
