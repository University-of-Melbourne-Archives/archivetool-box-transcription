package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import java.nio.file.Path

class PythonOCRInteraction : OCRInteraction {
    override fun generateOCR(files: Map<Path, JobFile>): Map<Path, JobFile> {
        TODO("Concrete Implementation Incomplete.")
    }
}

class MockOCRInteraction : OCRInteraction {
    override fun generateOCR(files: Map<Path, JobFile>): Map<Path, JobFile> {
        Log.error("Running Mock OCR, nothing will be done.")
        files.forEach { (path, file) -> file.crops.forEach {
            Log.debug("Transcribing $path:${it.key}")
        } }
        return mapOf()
    }
}

interface OCRInteraction : Interaction {
    fun generateOCR(files: Map<Path, JobFile>): Map<Path, JobFile>

    override fun isEligible(file: Pair<Path, JobFile>): Boolean {
        return file.second.let { jobFile ->
            jobFile.crops.run { isNotEmpty() && values.any { it.transcription == null } }
        }
        /*TODO: Add a check for if the generated crop etc exists.*/
    }
}
