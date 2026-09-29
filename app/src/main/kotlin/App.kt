package au.edu.unimelb.archivetool.app

import au.edu.unimelb.archivetool.gui.segmentation.SegmentationInterface
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.logging.LogLevel
import au.edu.unimelb.archivetool.util.logging.consumers.StdoutConsumer
import au.edu.unimelb.archivetool.util.math.geometry.Quad
import io.nacular.doodle.application.Modules.Companion.ImageModule
import io.nacular.doodle.application.Modules.Companion.PointerModule
import io.nacular.doodle.application.application
import io.nacular.doodle.theme.basic.BasicTheme.Companion.basicButtonBehavior
import io.nacular.doodle.theme.basic.BasicTheme.Companion.basicLabelBehavior
import java.nio.file.Path
import org.kodein.di.instance

fun main() {
    Log.registerConsumer(StdoutConsumer())

    Log.error("test")
    Log.warn("test")
    Log.log(LogLevel.Info, "test")
    Log.debug("test")
    Log.trace("test")
}
