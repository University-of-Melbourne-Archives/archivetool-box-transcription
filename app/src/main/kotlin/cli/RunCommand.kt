package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.switchboard.Switchboard
import au.edu.unimelb.archivetool.switchboard.interactions.export.CSVFileExportInteraction
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.parameters.types.restrictTo
import kotlin.io.path.Path

class RunCommand : Command(name = "run") {

    override fun help(context: Context): String =
        """
        Run the complete archive processing workflow.

        Processes archive images and writes the final CSV output.
        Use --resume to permit an existing job state in the input directory.
        """
            .trimIndent()

    val inputDirectory by
        option("-i", "--input-directory", help = "A directory containing photos of archive boxes.")
            .path(mustExist = true, canBeFile = false, mustBeReadable = true)
            .default(Path("."), defaultForHelp = "current working directory")

    val outputPath by
        option("-o", "--output", help = "Path for the generated CSV file.")
            .path(mustExist = false, canBeDir = false)
            .required()

    val resume by
        option("--resume", help = "Permit and reuse an existing job state in the input directory.")
            .flag(default = false)

    val autoApproveThreshold by
        option(
                "-A",
                "--auto-approve-threshold",
                metavar = "threshold",
                help = "Skip review for images whose boxes all meet <threshold>% confidence.",
            )
            .int()
            .restrictTo(0, 100)

    override fun run() {
        val jobRoot = inputDirectory.toAbsolutePath().normalize()
        val threshold = autoApproveThreshold?.toDouble()?.div(100.0) ?: 1.0
        val switchboard =
            Switchboard.with(
                CSVFileExportInteraction(outputPath.toAbsolutePath().normalize())
            )

        switchboard.createPreview(jobRoot, permitExisting = resume)
        switchboard.reviewSegmentationPreview(jobRoot, threshold)
        switchboard.manipulateImages(jobRoot)
        switchboard.createOCR(jobRoot)
        switchboard.reviewOCRPreview(jobRoot)
        switchboard.generateExport(jobRoot)
    }
}
