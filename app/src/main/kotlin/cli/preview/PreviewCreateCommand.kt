package au.edu.unimelb.archivetool.app.cli.preview

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.inputDirectory
import au.edu.unimelb.archivetool.app.cli.helpers.normalizedAbsolute
import au.edu.unimelb.archivetool.switchboard.Switchboard
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import kotlin.io.path.Path

class PreviewCreateCommand : Command(name = "create") {

    override fun help(context: Context): String =
        """
        Create previews from a directory of archive images.

        Switchboard manages job state and intermediate files.
        An existing job state is reused by default; use --no-resume to disable reuse.
        """
            .trimIndent()

    private val inputDirectory by inputDirectory()

    private val noResume by
        option("--no-resume", help = "Do not reuse existing job state in the input directory.")
            .flag(default = false)

    override fun run() {
        Switchboard.createPreview(
            inputDirectory.normalizedAbsolute(),
            permitExisting = !noResume,
        )
    }
}
