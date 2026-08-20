package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.countedTransform
import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.LogLevel
import au.edu.unimelb.archivetool.logging.consumers.StderrConsumer
import com.github.ajalt.clikt.completion.CompletionCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.option
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

class BaseCommand : Command(name = "archivetool", subcommands = setOf(PreviewCommand(), StatusCommand(), FinaliseCommand())) {
    init {
        // Additionally include the shell completion generation subcommand, which is of a different type and cannot go in the inherited constructor.
        subcommands(CompletionCommand(name = "completions", help = "Generate a tab-complete script for bash, zsh or fish"))
    }

    override fun aliases(): Map<String, List<String>> = mapOf(
        "finalize" to listOf("finalise"),
    )

    // Used to set the log level: -q means quieter, and -v means more verbose
    val logLevel by mutuallyExclusiveOptions(
        option("-v", "--verbose", help = "Enable more verbose logging${Cli.newLineChar}Can be added multiple times to further increase verbosity").countedTransform(limit = 2, clamp = true) {
            Optional.of(when (it) {
                1 -> LogLevel.Debug;
                2 -> LogLevel.Trace;
                else -> LogLevel.Info
            })
        },
        option("-q", "--quiet", help = "Disable a level of logging${Cli.newLineChar}Can be added multiple times to silence more logs").countedTransform(limit = 3, clamp = true) {
            when (it) {
                1 -> Optional.of(LogLevel.Warn);
                2 -> Optional.of(LogLevel.Error);
                3 -> Optional.empty()
                else -> Optional.of(LogLevel.Info)
            }
        }
    ).single().default(Optional.of(LogLevel.Info))

    override fun run() {
        logLevel.getOrNull()?.let {
            Log.registerConsumer(StderrConsumer(maxLogLevel = it))
        }
    }
}