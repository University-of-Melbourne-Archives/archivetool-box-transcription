package au.edu.unimelb.archivetool.logging

import kotlin.jvm.optionals.getOrNull
import kotlin.time.Clock

object AnsiColors {
    const val red = "\u001b[31m"
    const val yellow = "\u001b[0;33m"
    const val green = "\u001b[0;32m"
    const val cyan = "\u001b[0;36m"
    const val purple = "\u001b[0;35m"
    const val reset = "\u001b[0m"
}

object Log {
    private val consumers = mutableSetOf<LogConsumer>()

    fun registerConsumer(consumer: LogConsumer) {
        consumers.add(consumer)
    }

    fun revokeConsumer(consumer: LogConsumer) {
        consumers.remove(consumer)
    }

    fun log(logLevel: LogLevel, message: String) {
        consumers
            .filter { it.maxLogLevel >= logLevel }
            .forEach { it.recordLogEntry(formatLog(it.settings, logLevel, message)) }
    }

    private fun formatLog(settings: ConsumerSettings, level: LogLevel, message: String): String {
        val time = Clock.System.now();

        val caller = StackWalker.getInstance().walk { it.skip(1).filter { it.className != this.javaClass.name }.findFirst().getOrNull() }

        val color = if (settings.enableAnsiColor) {level.msgColor()} else {""}

        return "${if (settings.showTime){"[$time]"} else {""}}$caller $color$level${AnsiColors.reset} - $message\n"
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
