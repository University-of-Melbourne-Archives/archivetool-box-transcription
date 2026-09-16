package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath

class PythonOCRInteraction : OCRInteraction {
    override fun generateOCR(files: Map<JobRelativePath, JobFile>): Map<JobRelativePath, JobFile> {
        TODO("Concrete Implementation Incomplete.")
    }
}

class MockOCRInteraction : OCRInteraction {
    override fun generateOCR(files: Map<JobRelativePath, JobFile>): Map<JobRelativePath, JobFile> {
        Log.error("Running Mock OCR, nothing will be done.")
        files.forEach { (path, file) ->
            file.vertexes.forEach { (boxId, box) ->
                box.keyedBoundaries.forEach { (cropId, boundary) ->
                    Log.debug("Transcribing ${path.relative}::$boxId-$cropId")
                }
            }
        }
        return mapOf()
    }
}

interface OCRInteraction : Interaction {
    fun generateOCR(files: Map<JobRelativePath, JobFile>): Map<JobRelativePath, JobFile>

    override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean {
        return file.second.let { jobFile ->
            jobFile.vertexes.values.run {
                isNotEmpty() &&
                    this.any { box -> box.keyedBoundaries.values.any { it.transcription == null } }
            }
        }
        /*TODO: Add a check for if the generated crop etc exists.*/
    }
}
