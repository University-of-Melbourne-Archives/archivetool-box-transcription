package au.edu.unimelb.archivetool.util.logging.consumers

import au.edu.unimelb.archivetool.util.logging.ConsumerSettings
import au.edu.unimelb.archivetool.util.logging.LogConsumer
import au.edu.unimelb.archivetool.util.logging.LogLevel

class StringConsumer(
    override val maxLogLevel: LogLevel = LogLevel.Info,
    override val settings: ConsumerSettings = ConsumerSettings(),
) : LogConsumer {
    private val stringBuilder = StringBuilder()
    private var justReset = false

    override fun recordLogEntry(logEntry: String) {
        if (justReset) {
            justReset = false
            stringBuilder.clear()
        }
        stringBuilder.append(logEntry)
    }

    fun getLog(): String {
        justReset = true
        return stringBuilder.toString()
    }

    override fun toString(): String = getLog()
}
