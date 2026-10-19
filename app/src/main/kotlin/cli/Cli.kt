package au.edu.unimelb.archivetool.app.cli

import com.github.ajalt.clikt.core.main

/** Entry point adapter; command definitions stay in BaseCommand. */
object Cli {
    const val newLineChar = '\u0085'

    fun parser(args: Array<String>) = BaseCommand().main(args)
}
