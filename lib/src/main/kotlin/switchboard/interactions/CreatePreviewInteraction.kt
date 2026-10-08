package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.pythoninterop.PythonModule
import au.edu.unimelb.archivetool.pythoninterop.injectables.Logger as LoggerInjectable
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import java.nio.file.Path

class PythonCreatePreviewInteraction : CreatePreviewInteraction {
    val module by lazy {
        PythonModule(folder = TODO("Put Module Path Here"), setOf(LoggerInjectable()))
    }

    override fun createPreview(
        files: Set<JobRelativePath>,
        weights: Path,
        rejectConfidenceThreshold: Double,
        edgeBufferSize: Double,
        rejectCentroidRadius: Double,
    ): Map<JobRelativePath, JobFile> {
        TODO("Concrete Implementation Incomplete.")
    }
}

class MockCreatePreviewInteraction : CreatePreviewInteraction {
    override fun createPreview(
        files: Set<JobRelativePath>,
        weights: Path,
        rejectConfidenceThreshold: Double,
        edgeBufferSize: Double,
        rejectCentroidRadius: Double,
    ): Map<JobRelativePath, JobFile> {
        Log.error("Using Mock Implementation of Create Preview. Nothing will be done.")
        val fileCountLimit = 15
        val fileList =
            files
                .map { it.relative }
                .joinToString(
                    prefix = "[\n\t",
                    postfix = "]",
                    separator = ",\n\t",
                    limit = fileCountLimit,
                    truncated = "(and ${files.count() - fileCountLimit} others)",
                )
        Log.debug("Files: $fileList")
        Log.debug("Weights Path: $weights")
        Log.debug("Confidence Threshold: $rejectConfidenceThreshold")
        Log.debug("Edge Buffer Size: $edgeBufferSize")
        Log.debug("Duplicate Boundary Detection Radius: $rejectCentroidRadius")

        return mapOf()
    }
}

interface CreatePreviewInteraction : Interaction {
    fun createPreview(
        files: Set<JobRelativePath>,
        weights: Path,
        // Flatly rejects boxes under this threshold
        rejectConfidenceThreshold: Double = 0.05,
        // Distance from edge for which if a box is fully within, should be rejected.
        edgeBufferSize: Double = TODO(),
        // Compares the centroids of boxes to detect duplicates (in px)
        rejectCentroidRadius: Double = TODO(),
    ): Map<JobRelativePath, JobFile>

    override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean =
        file.second.let { it.vertexes.isNullOrEmpty() && !it.approved }
}
