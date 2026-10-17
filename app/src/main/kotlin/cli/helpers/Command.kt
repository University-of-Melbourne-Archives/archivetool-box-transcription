package au.edu.unimelb.archivetool.app.cli.helpers

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.installMordantMarkdown
import com.github.ajalt.clikt.core.subcommands

/**
 * Shared Clikt setup: register subcommands, enable Markdown help, and use the project's consistent
 * help layout. Does not execute backend operations.
 */
abstract class Command(val name: String? = null, val subcommands: Set<CliktCommand> = emptySet()) :
    CliktCommand(name) {
    init {
        (this as CliktCommand).subcommands(subcommands.toList())
        installMordantMarkdown()
        context { helpFormatter = { CustomHelpFormatter(it) } }
    }
}
