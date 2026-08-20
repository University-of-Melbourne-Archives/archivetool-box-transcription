package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.app.cli.helpers.jobDirectory
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.types.path
import kotlin.io.path.Path

class StatusCommand : Command(name = "status") {
    override fun help(context: Context): String = """
        Get the status of a job
        
        Shows information about the job, including the number of input files and how many crops are approved/pending.
        """.trimIndent()

    val jobDir by jobDirectory()
    override fun run() {
        TODO("Not yet implemented")
    }
}