package au.edu.unimelb.archivetool.app.cli.preview

import au.edu.unimelb.archivetool.switchboard.AbstractSwitchboard
import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.inputDirectory
import au.edu.unimelb.archivetool.app.cli.helpers.normalizedAbsolute
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

class PreviewCreateCommand(private val switchboard: AbstractSwitchboard) : Command(name = "create") {

    override fun help(context: Context): String =
        """
        Create previews from a directory of archive images.

        Any existing job state is reused by default; use --no-resume to disable reuse
        """
            .trimIndent()

    private val inputDirectory by inputDirectory()

    private val noResume by
        option("--no-resume", help = "Do not reuse any existing job state")
            .flag(default = false)

    override fun run() {
        switchboard.createPreview(inputDirectory.normalizedAbsolute(), permitExisting = !noResume)
    }
}
