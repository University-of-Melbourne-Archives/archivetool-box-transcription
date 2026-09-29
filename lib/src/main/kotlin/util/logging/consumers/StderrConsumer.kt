package au.edu.unimelb.archivetool.util.logging.consumers

import au.edu.unimelb.archivetool.util.logging.ConsumerSettings
import au.edu.unimelb.archivetool.util.logging.LogConsumer
import au.edu.unimelb.archivetool.util.logging.LogLevel

class StderrConsumer(override val maxLogLevel: LogLevel = LogLevel.Info) : LogConsumer {
    override val settings = ConsumerSettings(enableAnsiColor = true, showClass = false)

    override fun recordLogEntry(logEntry: String) {
        System.err.println(logEntry)
    }
}
