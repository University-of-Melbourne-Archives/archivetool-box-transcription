package au.edu.unimelb.archivetool.app

import au.edu.unimelb.archivetool.switchboard.Switchboard
import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.logging.LogLevel
import au.edu.unimelb.archivetool.util.logging.consumers.StdoutConsumer
import io.github.kelvindev15.kotlin2plantuml.plantuml.ClassDiagram
import io.github.kelvindev15.kotlin2plantuml.plantuml.Configuration
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.reflect.KVisibility



fun main() {
    Log.registerConsumer(StdoutConsumer())

    Log.error("test")
    Log.warn("test")
    Log.log(LogLevel.Info, "test")
    Log.debug("test")
    Log.trace("test")

    val uml = ClassDiagram(
        *(ClasspathScanner.findClasses("au.edu.unimelb.archivetool", requireReflectable = true).toTypedArray()),
        configuration = Configuration(false, false, false, recurse = true, KVisibility.PRIVATE, KVisibility.PRIVATE)
    ).plantUml()

    val f = createTempDirectory().resolve("archivetool.plantuml")
    f.writeText(uml)

    println("Wrote $uml to $f")
}
