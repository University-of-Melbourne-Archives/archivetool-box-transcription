package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.switchboard.AbstractSwitchboard
import au.edu.unimelb.archivetool.switchboard.Switchboard
import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.countedTransform
import au.edu.unimelb.archivetool.util.logging.LogLevel
import au.edu.unimelb.archivetool.util.logging.consumers.StderrConsumer
import com.github.ajalt.clikt.completion.CompletionCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.option
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

/** Main CLI command and its subcommands. */
class BaseCommand(private val switchboard: AbstractSwitchboard = Switchboard) :
    Command(
        name = "archivetool",
        subcommands =
            setOf(
                InfoCommand(),
                RunCommand(switchboard),
                PreviewCommand(switchboard),
                OCRCommand(switchboard),
                StatusCommand(switchboard),
                FinaliseCommand(switchboard),
            ),
    ) {
    init {
        subcommands(
            CompletionCommand(
                name = "completions",
                help = "Generate a tab-complete script for bash, zsh or fish",
            )
        )
    }

    // Allow the alternative US spelling as well.
    override fun aliases(): Map<String, List<String>> = mapOf("finalize" to listOf("finalise"))

    val logLevel by
        mutuallyExclusiveOptions(
                option(
                        "-v",
                        "--verbose",
                        help =
                            "Enable more verbose logging${Cli.newLineChar}Can be added multiple times to further increase verbosity",
                    )
                    .countedTransform(limit = 2, clamp = true) {
                        Optional.of(
                            when (it) {
                                1 -> LogLevel.Debug
                                2 -> LogLevel.Trace
                                else -> LogLevel.Info
                            }
                        )
                    },
                option(
                        "-q",
                        "--quiet",
                        help =
                            "Disable a level of logging${Cli.newLineChar}Can be added multiple times to silence more logs",
                    )
                    .countedTransform(limit = 3, clamp = true) {
                        when (it) {
                            1 -> Optional.of(LogLevel.Warn)
                            2 -> Optional.of(LogLevel.Error)
                            3 -> Optional.empty()
                            else -> Optional.of(LogLevel.Info)
                        }
                    },
            )
            .single()
            .default(Optional.of(LogLevel.Info))

    override fun run() {
        switchboard.init(
            logLevel.getOrNull()?.let { setOf(StderrConsumer(maxLogLevel = it)) } ?: emptySet()
        )
    }
}
