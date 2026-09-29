package au.edu.unimelb.archivetool.app.cli.preview

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.switchboard.Switchboard
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import kotlin.io.path.Path

class PreviewReviewCommand : Command(name = "review") {

    override fun help(context: Context): String =
        "Open pending previews for review in the current working directory."

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
        val jobRoot = Path(".").toAbsolutePath().normalize()
        val threshold = autoApproveThreshold?.toDouble()?.div(100.0) ?: 1.0
        Switchboard.reviewPreview(jobRoot, threshold)
    }
}
