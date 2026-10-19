package au.edu.unimelb.archivetool.app.cli.preview

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.asApprovalFraction
import au.edu.unimelb.archivetool.app.cli.helpers.currentJobRoot
import au.edu.unimelb.archivetool.switchboard.Switchboard
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo

class PreviewReviewCommand : Command(name = "review") {

    override fun help(context: Context): String =
        "Open pending previews for review in the current working directory."

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

    override fun run() {
        val jobRoot = currentJobRoot()
        val threshold = autoApproveThreshold.asApprovalFraction()
        Switchboard.reviewSegmentationPreview(jobRoot, threshold)
    }
}
