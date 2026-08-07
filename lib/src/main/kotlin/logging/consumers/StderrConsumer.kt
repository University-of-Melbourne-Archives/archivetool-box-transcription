package au.edu.unimelb.archivetool.logging.consumers

import au.edu.unimelb.archivetool.logging.ConsumerSettings
import au.edu.unimelb.archivetool.logging.LogConsumer
import au.edu.unimelb.archivetool.logging.LogLevel

class StderrConsumer(override val maxLogLevel: LogLevel = LogLevel.Info) : LogConsumer {
    override val settings = ConsumerSettings(enableAnsiColor = true)

    override fun recordLogEntry(logEntry: String) {
        System.err.println(logEntry)
    }
}