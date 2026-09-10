package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.preview.PreviewCreateCommand
import au.edu.unimelb.archivetool.app.cli.preview.PreviewReviewCommand
import com.github.ajalt.clikt.core.Context

class PreviewCommand :
    Command(
        // This command is called "preview".
        name = "preview",

        // Register "create" under "preview".
        subcommands = setOf(PreviewCreateCommand(), PreviewReviewCommand()),
    ) {
    // Text shown by: archivetool preview --help
    override fun help(context: Context): String =
        """
        Generate and manage box detection previews
        """
            .trimIndent()

    // The actual work is performed by its subcommands.
    override fun run() = Unit
}
