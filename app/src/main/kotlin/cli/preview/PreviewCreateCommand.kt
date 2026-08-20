package au.edu.unimelb.archivetool.app.cli.preview

import au.edu.unimelb.archivetool.app.cli.Cli
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.optionalValue
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.parameters.types.restrictTo
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable
import kotlin.io.path.listDirectoryEntries

class PreviewCreateCommand : CliktCommand(name = "create") {
    override fun help(context: Context): String = """
        Create a folder of previews.
        
        Transforms the images in the input directory into a project, with a job.json and various previews delineating the automatically cropped boxes and labels. 
    """.trimIndent()

    val input by option(
        "-i", "--input-directory",
        help = "A path to a directory containing photos of archive boxes."
    ).path(mustExist = true, canBeFile = false, mustBeReadable = true).required()

    val output by option(
        "-o", "--job-directory",
        help = "A path to an empty directory, or to where a directory can be created.${Cli.newLineChar}Will be used to store program state and previews of cropped-boxes."
    ).path(mustExist = false, canBeFile = false).required().check {
        // Either the path doesn't exist and can be created, or exists, is readable and empty.
        (!it.exists() && it.createDirectories().exists()) || (it.isReadable() && it.isWritable() && it.listDirectoryEntries().isEmpty())
    }

    // TODO: Do we get a confidence from box detetction?
    val preApproved by option(
        "-A", "--pre-approve",
        metavar = "threshold",
        help = "Automatically mark all generated boxes as approved.${Cli.newLineChar}If <threshold> is specified, only boxes with a confidence over <threshold>% are automatically approved.${Cli.newLineChar}Useful for scripts, where humans cannot review the generated images."
    ).int().optionalValue(0).restrictTo(0, 100)

    override fun run() {
        TODO("Not yet implemented")
    }
}