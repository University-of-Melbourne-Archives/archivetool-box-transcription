package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.extension.isNotNull
import au.edu.unimelb.archivetool.util.logging.Log

class ReviewGUIInteraction : ReviewPreviewInteraction {
    override fun review(
        files: Map<JobRelativePath, JobFile?>,
        autoApproveThreshold: Double,
    ): Map<JobRelativePath, JobFile> {
        TODO("Concrete Implementation Incomplete.")
    }
}

class MockReviewInteraction : ReviewPreviewInteraction {
    override fun review(
        files: Map<JobRelativePath, JobFile?>,
        autoApproveThreshold: Double,
    ): Map<JobRelativePath, JobFile> {
        Log.error("Using Mock Implementation of Review Preview. Nothing will be done.")

        Log.debug(
            "${files.count()} total files, ${files.filterValues { (it?.minConfidence ?: -1.0) > autoApproveThreshold}.count() } above auto-approval threshold $autoApproveThreshold"
        )

        return mapOf()
    }
}

interface ReviewPreviewInteraction : Interaction {
    fun review(
        files: Map<JobRelativePath, JobFile?>,
        autoApproveThreshold: Double = 1.0,
    ): Map<JobRelativePath, JobFile>

    override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean =
        file.second.let { it.vertexes.isNotNull() && !it.approved }
}
