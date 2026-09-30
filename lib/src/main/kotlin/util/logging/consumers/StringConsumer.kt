package au.edu.unimelb.archivetool.util.logging.consumers

import au.edu.unimelb.archivetool.util.logging.ConsumerSettings
import au.edu.unimelb.archivetool.util.logging.LogConsumer
import au.edu.unimelb.archivetool.util.logging.LogLevel

class StringConsumer(
    override val maxLogLevel: LogLevel = LogLevel.Info,
    override val settings: ConsumerSettings = ConsumerSettings(),
) : LogConsumer {
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
