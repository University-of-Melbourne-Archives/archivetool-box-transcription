package au.edu.unimelb.archivetool.app.cli

import com.github.ajalt.clikt.core.main

object Cli {
    const val newLineChar = '\u0085'

    fun parser(args: Array<String>) = BaseCommand().main(args)
}
