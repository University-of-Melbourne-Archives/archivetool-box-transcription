package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.switchboard.AbstractSwitchboard
import au.edu.unimelb.archivetool.switchboard.Switchboard
import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.countedTransform
import au.edu.unimelb.archivetool.util.logging.LogConsumer
import au.edu.unimelb.archivetool.util.logging.LogLevel
import au.edu.unimelb.archivetool.util.logging.consumers.FileConsumer
import au.edu.unimelb.archivetool.util.logging.consumers.StderrConsumer
import com.github.ajalt.clikt.completion.CompletionCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.option
import java.util.Optional
import kotlin.jvm.optionals.getOrNull
import com.github.ajalt.clikt.core.UsageError

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
                            "Increase console log verbosity${Cli.newLineChar}Can be added multiple times to further increase verbosity",
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
                            "Decrease console log verbosity${Cli.newLineChar}Can be added multiple times to silence more logs",
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

    // The file has its own log level, separate from stderr.
    val logFile by
        option("-L", "--log-file", help = "Write log messages to a file")

    val fileLogLevel by
        mutuallyExclusiveOptions(
                option(
                        "-V",
                        help =
                            "Increase log file verbosity${Cli.newLineChar}Can be added multiple times to further increase verbosity",
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
                        "-Q",
                        help =
                            "Decrease log file verbosity${Cli.newLineChar}Can be added multiple times to silence more logs",
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

    override fun run() {
        if (logFile == null && fileLogLevel != null) {
            throw UsageError("-V and -Q require -L/--log-file")
        }

        val consumers = mutableSetOf<LogConsumer>()
        logLevel.getOrNull()?.let { consumers.add(StderrConsumer(maxLogLevel = it)) }
        logFile?.let { path ->
            (fileLogLevel ?: Optional.of(LogLevel.Info)).getOrNull()?.let { level ->
                consumers.add(FileConsumer(path, maxLogLevel = level))
            }
        }
        switchboard.init(consumers)
    }
}
