package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.ocr.OCRCreateCommand
import au.edu.unimelb.archivetool.app.cli.ocr.OCRReviewCommand
import com.github.ajalt.clikt.core.Context

/** Commands for creating and reviewing OCR results. */
class OCRCommand(backend: CliBackend) :
    Command(
        name = "ocr",
        subcommands = setOf(OCRCreateCommand(backend), OCRReviewCommand(backend)),
    ) {
    override fun help(context: Context) = "Create and review OCR output."

    override fun run() = Unit
}
