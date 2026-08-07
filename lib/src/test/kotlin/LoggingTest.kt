import au.edu.unimelb.archivetool.logging.ConsumerSettings
import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.LogLevel
import au.edu.unimelb.archivetool.logging.consumers.StringConsumer
import org.junit.jupiter.api.Test

class LoggingTest {
    @Test
    fun basicTest(){
        // Disable time for consistent test results.
        val consumer = StringConsumer(maxLogLevel = LogLevel.Trace, settings = ConsumerSettings(showTime = false))
        Log.registerConsumer(consumer)

        Log.error("errorTest")
        Log.warn("warnTest")
        Log.info("infoTest")
        Log.debug("debugTest")
        Log.trace("traceTest")

        Log.log(LogLevel.Info, "logTest")

        val log = consumer.getLog()
        assert(log.contains(Regex("LoggingTest\\.basicTest\\(LoggingTest\\.kt:\\d+\\) ERROR - errorTest")))
        assert(log.contains(Regex("LoggingTest\\.basicTest\\(LoggingTest\\.kt:\\d+\\) WARN - warnTest")))
        assert(log.contains(Regex("LoggingTest\\.basicTest\\(LoggingTest\\.kt:\\d+\\) INFO - infoTest")))
        assert(log.contains(Regex("LoggingTest\\.basicTest\\(LoggingTest\\.kt:\\d+\\) DEBUG - debugTest")))
        assert(log.contains(Regex("LoggingTest\\.basicTest\\(LoggingTest\\.kt:\\d+\\) TRACE - traceTest")))

        assert(log.contains(Regex("LoggingTest\\.basicTest\\(LoggingTest\\.kt:\\d+\\) .* - logTest")))

        // Clean Up
        Log.revokeConsumer(consumer)
    }

    @Test
    fun levelFiltering(){
        // Disable time for consistent test results.
        val consumer = StringConsumer(maxLogLevel = LogLevel.Info, settings = ConsumerSettings(showTime = false))
        Log.registerConsumer(consumer)

        Log.error("errorTest")
        Log.warn("warnTest")
        Log.info("infoTest")
        Log.debug("debugTest")
        Log.trace("traceTest")

        Log.log(LogLevel.Info, "logTest")

        val log = consumer.getLog()
        assert(log.contains(Regex("LoggingTest\\.levelFiltering\\(LoggingTest\\.kt:\\d+\\) ERROR - errorTest")))
        assert(log.contains(Regex("LoggingTest\\.levelFiltering\\(LoggingTest\\.kt:\\d+\\) WARN - warnTest")))
        assert(log.contains(Regex("LoggingTest\\.levelFiltering\\(LoggingTest\\.kt:\\d+\\) INFO - infoTest")))
        assert(!log.contains(Regex("LoggingTest\\.levelFiltering\\(LoggingTest\\.kt:\\d+\\) DEBUG - debugTest")))
        assert(!log.contains(Regex("LoggingTest\\.levelFiltering\\(LoggingTest\\.kt:\\d+\\) TRACE - traceTest")))

        assert(log.contains(Regex("LoggingTest\\.levelFiltering\\(LoggingTest\\.kt:\\d+\\) .* - logTest")))

        // Clean Up
        Log.revokeConsumer(consumer)
    }
}