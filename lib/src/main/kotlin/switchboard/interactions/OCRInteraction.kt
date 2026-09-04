package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import java.nio.file.Path

class PythonOCRInteraction : OCRInteraction {
    override fun generateOCR() {
        TODO("Concrete Implementation Incomplete.")
    }
}

class MockOCRInteraction : OCRInteraction {
    override fun generateOCR() {
        TODO("Mock Implementation Incomplete.")
    }
}

interface OCRInteraction : Interaction {
    fun generateOCR()

    override fun isEligible(file: Pair<Path, JobFile>): Boolean {
        return file.second.let { jobFile ->
            jobFile.crops.run { isNotEmpty() && values.any { it.transcription == null } }
        }
        /*TODO: Add a check for if the generated crop etc exists.*/
    }
}
