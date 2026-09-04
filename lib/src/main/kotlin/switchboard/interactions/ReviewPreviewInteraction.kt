package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import java.nio.file.Path

class ReviewGUIInteraction : ReviewPreviewInteraction {
    override fun review(
        files: Map<Path, JobFile?>,
        autoApproveThreshold: Double,
    ): Map<Path, JobFile> {
        TODO("Concrete Implementation Incomplete.")
    }
}

class MockReviewInteraction : ReviewPreviewInteraction {
    override fun review(
        files: Map<Path, JobFile?>,
        autoApproveThreshold: Double,
    ): Map<Path, JobFile> {
        Log.error("Using Mock Implementation of Review Preview. Nothing will be done.")

        Log.debug(
            "${files.count()} total files, ${files.filterValues { it?.crops?.values?.all{crop -> crop.confidence > autoApproveThreshold} ?: false }.count() } above auto-approval threshold $autoApproveThreshold"
        )

        return mapOf()
    }
}

interface ReviewPreviewInteraction : Interaction {
    fun review(files: Map<Path, JobFile?>, autoApproveThreshold: Double = 1.0): Map<Path, JobFile>

    override fun isEligible(file: Pair<Path, JobFile>): Boolean =
        file.second.let { it.crops.isNotEmpty() && !it.approved }
}
