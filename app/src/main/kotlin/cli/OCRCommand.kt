package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.ocr.OCRReviewCommand
import com.github.ajalt.clikt.core.Context

class OCRCommand :
    Command(
        name = "ocr",
        subcommands = setOf(OCRReviewCommand()),
    ) {
    override fun help(context: Context): String = "Manage OCR output and review."

    override fun run() = Unit
}
