package au.edu.unimelb.archivetool.app.cli.ocr

import au.edu.unimelb.archivetool.switchboard.AbstractSwitchboard
import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.inputDirectory
import au.edu.unimelb.archivetool.app.cli.helpers.normalizedAbsolute
import com.github.ajalt.clikt.core.Context

class OCRCreateCommand(private val switchboard: AbstractSwitchboard) : Command(name = "create") {

    override fun help(context: Context): String =
        "Prepare approved images and generate OCR results for the selected job."

    private val inputDirectory by inputDirectory()

    // Images need to be prepared before OCR starts.
    override fun run() {
        val jobRoot = inputDirectory.normalizedAbsolute()
        switchboard.manipulateImages(jobRoot)
        switchboard.createOCR(jobRoot)
    }
}
