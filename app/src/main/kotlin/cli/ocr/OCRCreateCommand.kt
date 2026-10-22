package au.edu.unimelb.archivetool.app.cli.ocr

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.inputDirectory
import au.edu.unimelb.archivetool.app.cli.helpers.normalizedAbsolute
import au.edu.unimelb.archivetool.switchboard.Switchboard
import com.github.ajalt.clikt.core.Context

class OCRCreateCommand : Command(name = "create") {

    override fun help(context: Context): String =
        "Prepare approved images and generate OCR results in the current working directory."

    private val inputDirectory by inputDirectory()

    // Respect the Switchboard pipeline: image preparation precedes OCR.
    override fun run() {
        val jobRoot = inputDirectory.normalizedAbsolute()
        Switchboard.manipulateImages(jobRoot)
        Switchboard.createOCR(jobRoot)
    }
}
