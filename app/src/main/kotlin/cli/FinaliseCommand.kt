package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.currentJobRoot
import au.edu.unimelb.archivetool.app.cli.helpers.normalizedAbsolute
import au.edu.unimelb.archivetool.switchboard.Switchboard
import au.edu.unimelb.archivetool.switchboard.interactions.export.CSVFileExportInteraction
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path

class FinaliseCommand : Command(name = "finalise") {

    override fun help(context: Context): String =
        """
        Export existing processed job data to a CSV file.

        The current working directory is used as the job root.
        """
            .trimIndent()

    private val outputPath by
        option("-o", "--output", help = "Path for the generated CSV file.")
            .path(mustExist = false, canBeDir = false)
            .required()

    // Export previously processed data; do not rerun segmentation or OCR.
    override fun run() {
        val jobRoot = currentJobRoot()
        Switchboard.with(CSVFileExportInteraction(outputPath.normalizedAbsolute()))
            .generateExport(jobRoot)
    }
}
