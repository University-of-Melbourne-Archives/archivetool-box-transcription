package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.preview.PreviewCreateCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.testing.test

class PreviewCommand : Command(name = "preview", subcommands = setOf(PreviewCreateCommand())) {
    override fun help(context: Context): String = """
        Generate and manage box detection previews
    """.trimIndent()

    override fun run() = Unit
}