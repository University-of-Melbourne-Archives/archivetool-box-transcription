package au.edu.unimelb.archivetool.app.cli.helpers

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.installMordantMarkdown
import com.github.ajalt.clikt.output.MordantHelpFormatter

abstract class Command(
    // The name used for this command in the terminal.
    val name: String? = null,

    // The child commands registered under this command.
    val subcommands: Set<CliktCommand> = setOf()

// All custom commands inherit Clikt's base command class.
) : CliktCommand(name) {

    // Runs automatically whenever a Command object is created.
    init {
        // Register the supplied child commands with Clikt.
        (this as CliktCommand).subcommands(subcommands.toList())

        // Allow Markdown formatting in help text.
        installMordantMarkdown()

        // Use the project's custom --help layout.
        context {
            helpFormatter = { CustomHelpFormatter(it) }
        }
    }
}