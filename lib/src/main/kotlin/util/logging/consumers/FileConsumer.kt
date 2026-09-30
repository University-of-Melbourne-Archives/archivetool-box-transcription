package au.edu.unimelb.archivetool.util.logging.consumers

import au.edu.unimelb.archivetool.util.logging.ConsumerSettings
import au.edu.unimelb.archivetool.util.logging.LogConsumer
import au.edu.unimelb.archivetool.util.logging.LogLevel
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

class FileConsumer(val filePath: String, override val maxLogLevel: LogLevel = LogLevel.Info) :
    LogConsumer {
    override val settings = ConsumerSettings()

    private val file by lazy {
        val handle = Paths.get(filePath)
        Files.write(handle, ByteArray(0))

        handle
    }

    override fun recordLogEntry(logEntry: String) {
        Files.write(file, logEntry.toByteArray(), StandardOpenOption.APPEND)
    }
}
