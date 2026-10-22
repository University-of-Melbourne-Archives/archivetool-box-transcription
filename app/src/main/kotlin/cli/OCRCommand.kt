package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.ocr.OCRCreateCommand
import au.edu.unimelb.archivetool.app.cli.ocr.OCRReviewCommand
import com.github.ajalt.clikt.core.Context

/** Groups OCR generation and human review commands. */
class OCRCommand :
    Command(name = "ocr", subcommands = setOf(OCRCreateCommand(), OCRReviewCommand())) {
    override fun help(context: Context) = "Create and review OCR output."

    override fun run() = Unit
}
