package au.edu.unimelb.archivetool.app.cli.helpers

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.installMordantMarkdown
import com.github.ajalt.clikt.core.subcommands

/** Common settings for commands and their help pages. */
abstract class Command(val name: String? = null, val subcommands: Set<CliktCommand> = emptySet()) :
    CliktCommand(name) {
    init {
        (this as CliktCommand).subcommands(subcommands.toList())
        installMordantMarkdown()
        context { helpFormatter = { CustomHelpFormatter(it) } }
    }
}
