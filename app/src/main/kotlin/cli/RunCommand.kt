package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.inputDirectory
import au.edu.unimelb.archivetool.app.cli.helpers.normalizedAbsolute
import au.edu.unimelb.archivetool.switchboard.Switchboard
import au.edu.unimelb.archivetool.switchboard.interactions.export.CSVFileExportInteraction
import au.edu.unimelb.archivetool.util.extension.percentToFraction
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.parameters.types.restrictTo

class RunCommand : Command(name = "run") {

    override fun help(context: Context): String =
        """
        Run the complete archive processing workflow.

        Processes archive images and writes the final CSV output.
        An existing job state is reused by default; use --no-resume to disable reuse.
        """
            .trimIndent()

    private val inputDirectory by inputDirectory()

    private val outputPath by
        option("-o", "--output", help = "Path for the generated CSV file.")
            .path(mustExist = false, canBeDir = false)
            .required()

    private val noResume by
        option("--no-resume", help = "Do not reuse existing job state in the input directory.")
            .flag(default = false)

    private val autoApproveThreshold by
        option(
                "-A",
                "--auto-approve-threshold",
                metavar = "threshold",
                help = "Segmentation confidence threshold in percent (0-100); passed to Switchboard.",
            )
            .int()
            .restrictTo(0, 100)

    /**
     * Coordinate the six published Switchboard stages in their required order.
     * Segmentation, image processing, OCR, review and job-state persistence are
     * deliberately owned by Switchboard rather than reimplemented by the CLI.
     */
    override fun run() {
        val jobRoot = inputDirectory.normalizedAbsolute()
        val threshold = autoApproveThreshold?.percentToFraction() ?: 1.0
        val switchboard =
            Switchboard.with(
                CSVFileExportInteraction(outputPath.normalizedAbsolute())
            )

        // Resume by default; the opt-out flag prevents reuse of existing job state.
        switchboard.createPreview(jobRoot, permitExisting = !noResume)
        switchboard.reviewSegmentationPreview(jobRoot, threshold)
        switchboard.manipulateImages(jobRoot)
        switchboard.createOCR(jobRoot)
        switchboard.reviewOCRPreview(jobRoot)
        switchboard.generateExport(jobRoot)
    }
}
