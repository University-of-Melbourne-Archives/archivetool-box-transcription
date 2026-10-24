package au.edu.unimelb.archivetool.app.cli.ocr

import au.edu.unimelb.archivetool.switchboard.AbstractSwitchboard
import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.inputDirectory
import au.edu.unimelb.archivetool.app.cli.helpers.normalizedAbsolute
import com.github.ajalt.clikt.core.Context

class OCRReviewCommand(private val switchboard: AbstractSwitchboard) : Command(name = "review") {

    override fun help(context: Context): String =
        "Open OCR results for review in the selected job directory."

    private val inputDirectory by inputDirectory()

    override fun run() {
        val jobRoot = inputDirectory.normalizedAbsolute()
        switchboard.reviewOCRPreview(jobRoot)
    }
}
