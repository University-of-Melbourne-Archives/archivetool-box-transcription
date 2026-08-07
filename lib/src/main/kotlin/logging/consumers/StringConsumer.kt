package au.edu.unimelb.archivetool.logging.consumers

import au.edu.unimelb.archivetool.logging.ConsumerSettings
import au.edu.unimelb.archivetool.logging.LogConsumer
import au.edu.unimelb.archivetool.logging.LogLevel

class StringConsumer(override val maxLogLevel: LogLevel = LogLevel.Info, override val settings: ConsumerSettings = ConsumerSettings()) : LogConsumer {
    private val stringBuilder = StringBuilder()

    override fun recordLogEntry(logEntry: String) {
        stringBuilder.append(logEntry)
    }

    fun getLog(): String {
        val out = stringBuilder.toString()
        stringBuilder.clear()
        return out
    }

    override fun toString(): String = getLog()
}