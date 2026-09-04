package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import java.nio.file.Path

class CSVExportInteraction : ExportInteraction {
    override fun export() {
        TODO()
    }
}

interface ExportInteraction : Interaction {
    fun export()

    override fun isEligible(file: Pair<Path, JobFile>): Boolean =
        file.second.crops.all { it.value.transcription != null }
}
