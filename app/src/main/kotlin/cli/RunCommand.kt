package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.inputDirectory
import au.edu.unimelb.archivetool.app.cli.helpers.normalizedAbsolute
import au.edu.unimelb.archivetool.util.extension.percentToFraction
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.parameters.types.restrictTo

class RunCommand(private val backend: CliBackend) : Command(name = "run") {

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
                help =
                    "Segmentation confidence threshold in percent (0-100); passed to Switchboard.",
            )
            .int()
            .restrictTo(0, 100)

    // Run each stage in order, using the same job directory throughout.
    override fun run() {
        val jobRoot = inputDirectory.normalizedAbsolute()
        val threshold = autoApproveThreshold?.percentToFraction() ?: 1.0

        backend.createPreview(jobRoot, permitExisting = !noResume)
        backend.reviewPreview(jobRoot, threshold)
        backend.prepareImages(jobRoot)
        backend.createOCR(jobRoot)
        backend.reviewOCR(jobRoot)
        backend.export(jobRoot, outputPath.normalizedAbsolute())
    }
}
