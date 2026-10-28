package au.edu.unimelb.archivetool.app.cli

import com.github.ajalt.clikt.core.main

/** Pass command-line arguments to the main CLI command. */
object Cli {
    const val newLineChar = '\u0085'

    fun parser(args: Array<String>) = BaseCommand().main(args)
}
