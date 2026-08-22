import au.edu.unimelb.archivetool.logging.ConsumerSettings
import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.LogLevel
import au.edu.unimelb.archivetool.logging.consumers.StringConsumer
import org.junit.jupiter.api.Test

class LoggingTest {
    @Test
    fun `test basic log functionality`(){
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
        assert(log.contains(Regex("LoggingTest\\.test basic log functionality\\(LoggingTest\\.kt:\\d+\\) ERROR - errorTest")))
        assert(log.contains(Regex("LoggingTest\\.test basic log functionality\\(LoggingTest\\.kt:\\d+\\) WARN - warnTest")))
        assert(log.contains(Regex("LoggingTest\\.test basic log functionality\\(LoggingTest\\.kt:\\d+\\) INFO - infoTest")))
        assert(log.contains(Regex("LoggingTest\\.test basic log functionality\\(LoggingTest\\.kt:\\d+\\) DEBUG - debugTest")))
        assert(log.contains(Regex("LoggingTest\\.test basic log functionality\\(LoggingTest\\.kt:\\d+\\) TRACE - traceTest")))

        assert(log.contains(Regex("LoggingTest\\.test basic log functionality\\(LoggingTest\\.kt:\\d+\\) .* - logTest")))

        // Clean Up
        Log.revokeConsumer(consumer)
    }

    @Test
    fun `test log level filters`(){
        // Disable time for consistent test results.
        val infoConsumer = StringConsumer(maxLogLevel = LogLevel.Info, settings = ConsumerSettings(showTime = false))
        Log.registerConsumer(infoConsumer)
        val traceConsumer = StringConsumer(maxLogLevel = LogLevel.Info, settings = ConsumerSettings(showTime = false))
        Log.registerConsumer(traceConsumer)


        Log.error("errorTest")
        Log.warn("warnTest")
        Log.info("infoTest")
        Log.debug("debugTest")
        Log.trace("traceTest")

        val infoLog = infoConsumer.getLog()
        assert(infoLog.contains(Regex("LoggingTest\\.test log level filters\\(LoggingTest\\.kt:\\d+\\) ERROR - errorTest")))
        assert(infoLog.contains(Regex("LoggingTest\\.test log level filters\\(LoggingTest\\.kt:\\d+\\) WARN - warnTest")))
        assert(infoLog.contains(Regex("LoggingTest\\.test log level filters\\(LoggingTest\\.kt:\\d+\\) INFO - infoTest")))
        assert(!infoLog.contains(Regex("LoggingTest\\.test log level filters\\(LoggingTest\\.kt:\\d+\\) DEBUG - debugTest")))
        assert(!infoLog.contains(Regex("LoggingTest\\.test log level filters\\(LoggingTest\\.kt:\\d+\\) TRACE - traceTest")))

        val traceLog = traceConsumer.getLog()
        assert(traceLog.contains(Regex("LoggingTest\\.test log level filters\\(LoggingTest\\.kt:\\d+\\) ERROR - errorTest")))
        assert(traceLog.contains(Regex("LoggingTest\\.test log level filters\\(LoggingTest\\.kt:\\d+\\) WARN - warnTest")))
        assert(traceLog.contains(Regex("LoggingTest\\.test log level filters\\(LoggingTest\\.kt:\\d+\\) INFO - infoTest")))
        assert(!traceLog.contains(Regex("LoggingTest\\.test log level filters\\(LoggingTest\\.kt:\\d+\\) DEBUG - debugTest")))
        assert(!traceLog.contains(Regex("LoggingTest\\.test log level filters\\(LoggingTest\\.kt:\\d+\\) TRACE - traceTest")))

        // Clean Up
        Log.revokeConsumer(infoConsumer)
        Log.revokeConsumer(traceConsumer)
    }
}