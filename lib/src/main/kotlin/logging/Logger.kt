package au.edu.unimelb.archivetool.logging

import kotlin.jvm.optionals.getOrNull
import kotlin.time.Clock

object Log {
    private val consumers = mutableSetOf<LogConsumer>()

    fun registerConsumer(consumer: LogConsumer) {
        consumers.add(consumer)
    }

    fun log(logLevel: LogLevel, message: String) {
        consumers
            .filter { it.maxLogLevel >= logLevel }
            .forEach { it.recordLogEntry(formatLog(it.settings, logLevel, message)) }
    }

    private fun formatLog(settings: ConsumerSettings, level: LogLevel, message: String): String {
        val time = Clock.System.now();

        val caller = StackWalker.getInstance().walk { it.skip(1).filter { it.className != this.javaClass.name }.findFirst().getOrNull() }

        // TODO: Conditionally add ANSI color codes.

        return "${if (settings.showTime){"[$time]"} else {""}} $caller $level - $message\n"
    }

    fun trace(message: String) = log(LogLevel.Trace, message)
    fun debug(message: String) = log(LogLevel.Debug, message)
    fun info(message: String) = log(LogLevel.Info, message)
    fun warn(message: String) = log(LogLevel.Warn, message)
    fun error(message: String) = log(LogLevel.Error, message)
}

enum class LogLevel {
    Error,
    Warn,
    Info,
    Debug,
    Trace;

    override fun toString(): String {
        return when(this) {
            Error -> "ERROR"
            Warn -> "WARN"
            Info -> "INFO"
            Debug -> "DEBUG"
            Trace -> "TRACE"
        }
    }
}

interface LogConsumer {
    val maxLogLevel: LogLevel
    val settings: ConsumerSettings

    fun recordLogEntry(logEntry: String)
}

data class ConsumerSettings (val enableAnsiColor: Boolean = false, val showTime: Boolean = true)
