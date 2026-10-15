package au.edu.unimelb.archivetool.app.cli.ocr

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.switchboard.Switchboard
import com.github.ajalt.clikt.core.Context
import kotlin.io.path.Path

class OCRCreateCommand : Command(name = "create") {

    override fun help(context: Context): String =
        "Prepare approved images and generate OCR results in the current working directory."

    override fun run() {
        val jobRoot = Path(".").toAbsolutePath().normalize()
        Switchboard.manipulateImages(jobRoot)
        Switchboard.createOCR(jobRoot)
    }
}
