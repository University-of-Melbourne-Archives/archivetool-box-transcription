package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.jobDirectory
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.defaultByName
import com.github.ajalt.clikt.parameters.groups.groupChoice
import com.github.ajalt.clikt.parameters.groups.groupSwitch
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.optionalValue
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo


class FinaliseCommand : Command(name = "finalise") {
    override fun help(context: Context): String = """
        Generate a CSV table describing a directory of previews.
        
        Runs an OCR model to detect text.
    """.trimIndent()

    val jobDir by jobDirectory()

    // You can detect the specific strategy using the `is` operator, then cast with `as`.
    val keepDuplicates: DuplicateStrategy by option(
        "--duplicate-strategy",
        help = "Strategy to use when resolving boxes that share an ID"
    ).groupChoice(
        "keep" to KeepAllDuplicates(),
        "keep-best" to KeepBestDuplicate(),
        "delete-below" to KeepAboveThreshold()
    ).defaultByName("keep-best")

    val unintelligileIsUnique by option(
        "-L", "--label-threshold",
        metavar = "threshold",
        help = "Automatically treat a box as unique if the confidence for its label is below <threshold>%.${Cli.newLineChar}This prevents unintelligible boxes from potentially overwriting each-other."
    ).int().restrictTo(0, 100).default(0, defaultForHelp = "0 (disabled)")

    override fun run() {
        TODO("Not yet implemented")
    }
}

sealed class DuplicateStrategy(val name: String, val help: String) : OptionGroup("Options for --duplicate-strategy=$name", help)
class KeepAllDuplicates : DuplicateStrategy("keep", "Keeps all duplicate values as table entries.")
class KeepBestDuplicate : DuplicateStrategy("keep-best", "Only records the best value")
class KeepAboveThreshold : DuplicateStrategy("delete-below", "Removes any duplicates that lie below a given confidence threshold.") {
    val threshold by option(
        "--retention-threshold",
        metavar = "threshold",
        help = "Only retain duplicates with a confidence over <threshold>%"
    ).int().restrictTo(0, 100).required()

    val deleteBest by option("--delete-best", help = "Behaviour for duplicates of a value where all duplicates fall below <threshold>").flag("--keep-best", default = false, defaultForHelp = "keep");
}
// TODO: Maybe a smart strategy that determines a similarity score between the duplicates as well as considering confidence.
