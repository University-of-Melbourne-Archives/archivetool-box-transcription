package au.edu.unimelb.archivetool.app.cli.ocr

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.switchboard.Switchboard
import com.github.ajalt.clikt.core.Context
import kotlin.io.path.Path

class OCRReviewCommand : Command(name = "review") {

    override fun help(context: Context): String =
        "Open OCR results for review in the current working directory."

    override fun run() {
        val jobRoot = Path(".").toAbsolutePath().normalize()
        Switchboard.reviewOCRPreview(jobRoot)
    }
}
