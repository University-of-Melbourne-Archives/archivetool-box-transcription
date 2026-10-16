package au.edu.unimelb.archivetool.app

import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.logging.LogLevel
import au.edu.unimelb.archivetool.util.logging.consumers.StdoutConsumer

fun main() {
    Log.registerConsumer(StdoutConsumer())

    Log.error("test")
    Log.warn("test")
    Log.log(LogLevel.Info, "test")
    Log.debug("test")
    Log.trace("test")
}
