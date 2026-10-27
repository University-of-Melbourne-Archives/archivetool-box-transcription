package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.inputDirectory
import au.edu.unimelb.archivetool.app.cli.helpers.normalizedAbsolute
import au.edu.unimelb.archivetool.switchboard.AbstractSwitchboard
import au.edu.unimelb.archivetool.switchboard.interactions.export.CSVFileExportInteraction
import au.edu.unimelb.archivetool.util.extension.percentToFraction
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.parameters.types.restrictTo

class RunCommand(private val switchboard: AbstractSwitchboard) : Command(name = "run") {

    override fun help(context: Context): String =
        """
        Run the complete processing workflow

        Equivalent to running the following commands in order:
        - `preview create`
        - `preview review`
        - `ocr create`
        - `ocr review`
        - `finalise`

        Options behave identically in the subcommand that they are inherited from
        """
            .trimIndent()

    private val inputDirectory by inputDirectory()

    private val outputPath by
        option("-o", "--output", help = "Path for the generated CSV file (finalise)")
            .path(mustExist = false, canBeDir = false)
            .required()

    private val noResume by
        option("--no-resume", help = "Prevent reuse of any existing job state (preview create)")
            .flag(default = false)

    private val autoApproveThreshold by
        option(
                "-A",
                "--auto-approve-threshold",
                metavar = "threshold",
                help =
                    "[0-100] Threshold (as a percentage) that defines the minimum confidence of an image for it to be approved automatically (preview review)",
            )
            .int()
            .restrictTo(0, 100)

    // Run each stage in order, using the same job directory throughout.
    override fun run() {
        val jobRoot = inputDirectory.normalizedAbsolute()
        val threshold = autoApproveThreshold?.percentToFraction() ?: 1.0

        switchboard.createPreview(jobRoot, permitExisting = !noResume)
        switchboard.reviewSegmentationPreview(jobRoot, threshold)
        switchboard.manipulateImages(jobRoot)
        switchboard.createOCR(jobRoot)
        switchboard.reviewOCRPreview(jobRoot)
        switchboard
            .with(CSVFileExportInteraction(outputPath.normalizedAbsolute()))
            .generateExport(jobRoot)
    }
}
