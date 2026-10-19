package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.preview.PreviewCreateCommand
import au.edu.unimelb.archivetool.app.cli.preview.PreviewReviewCommand
import com.github.ajalt.clikt.core.Context

/** Groups segmentation preview creation and human review commands. */
class PreviewCommand :
    Command(name = "preview", subcommands = setOf(PreviewCreateCommand(), PreviewReviewCommand())) {
    override fun help(context: Context) = "Generate and review box-detection previews."

    // Work is performed by the registered subcommands.
    override fun run() = Unit
}
