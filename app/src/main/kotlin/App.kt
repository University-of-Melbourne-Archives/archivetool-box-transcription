package au.edu.unimelb.archivetool.app

import SegmentationReview
import io.nacular.doodle.application.application
import org.kodein.di.instance

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.LogLevel
import au.edu.unimelb.archivetool.logging.consumers.StdoutConsumer

fun main() {
    Log.registerConsumer(StdoutConsumer())

    Log.error("test")
    Log.warn("test")
    Log.log(LogLevel.Info, "test")
    Log.debug("test")
    Log.trace("test")

    application {
        SegmentationReview(instance())
    }
}
