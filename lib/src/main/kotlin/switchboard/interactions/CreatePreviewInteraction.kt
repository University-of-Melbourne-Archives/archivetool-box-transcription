package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import java.nio.file.Path

class PythonCreatePreviewInteraction : CreatePreviewInteraction {
    override fun createPreview(
        files: Set<Path>,
        weights: Path,
        rejectConfidenceThreshold: Double,
    ): Map<Path, JobFile> {
        TODO("Concrete Implementation Incomplete.")
    }
}

class MockCreatePreviewInteraction : CreatePreviewInteraction {
    override fun createPreview(
        files: Set<Path>,
        weights: Path,
        rejectConfidenceThreshold: Double,
    ): Map<Path, JobFile> {
        Log.error("Using Mock Implementation of Create Preview. Nothing will be done.")
        val fileCountLimit = 15
        val fileList =
            files.joinToString(
                prefix = "[\n",
                postfix = "]",
                separator = ",\n\t",
                limit = fileCountLimit,
                truncated = "(and ${files.count() - fileCountLimit} others)",
            )
        Log.debug("Files: $fileList")
        Log.debug("Weights Path: $weights")
        Log.debug("Confidence Threshold: $rejectConfidenceThreshold")

        return mapOf()
    }
}

interface CreatePreviewInteraction : Interaction {
    fun createPreview(
        files: Set<Path>,
        weights: Path,
        rejectConfidenceThreshold: Double = 0.05,
    ): Map<Path, JobFile>

    override fun isEligible(file: Pair<Path, JobFile>): Boolean =
        file.second.let { it.crops.isEmpty() && !it.approved }
}
