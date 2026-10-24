package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.preview.PreviewCreateCommand
import au.edu.unimelb.archivetool.app.cli.preview.PreviewReviewCommand
import com.github.ajalt.clikt.core.Context

/** Commands for checking detected boxes. */
class PreviewCommand(backend: CliBackend) :
    Command(
        name = "preview",
        subcommands = setOf(PreviewCreateCommand(backend), PreviewReviewCommand(backend)),
    ) {
    override fun help(context: Context) = "Generate and review box-detection previews."

    // Work is performed by the registered subcommands.
    override fun run() = Unit
}
