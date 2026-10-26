package au.edu.unimelb.archivetool.app.cli.preview

import au.edu.unimelb.archivetool.switchboard.AbstractSwitchboard
import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.inputDirectory
import au.edu.unimelb.archivetool.app.cli.helpers.normalizedAbsolute
import au.edu.unimelb.archivetool.util.extension.percentToFraction
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo

class PreviewReviewCommand(private val switchboard: AbstractSwitchboard) : Command(name = "review") {

    override fun help(context: Context): String =
        "Open box-detection previews for review"

    private val inputDirectory by inputDirectory()

    private val autoApproveThreshold by
        option(
                "-A",
                "--auto-approve-threshold",
                metavar = "threshold",
                help =
                    "[0-100] Threshold (as a percentage) that defines the minimum confidence of an image for it to be approved automatically",
            )
            .int()
            .restrictTo(0, 100)

    override fun run() {
        val jobRoot = inputDirectory.normalizedAbsolute()
        val threshold = autoApproveThreshold?.percentToFraction() ?: 1.0
        switchboard.reviewSegmentationPreview(jobRoot, threshold)
    }
}
