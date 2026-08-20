package au.edu.unimelb.archivetool.app

import au.edu.unimelb.archivetool.app.cli.Cli
import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.LogLevel
import au.edu.unimelb.archivetool.logging.consumers.StdoutConsumer

fun main(args: Array<String>) = Cli.parser(args)