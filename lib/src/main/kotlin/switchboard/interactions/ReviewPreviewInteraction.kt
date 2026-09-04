package au.edu.unimelb.archivetool.switchboard.interactions

class PythonReviewGUIInteraction : ReviewPreviewInteraction {
    override fun review() {
        TODO("This requires PythonInterop code to be merged.")
    }
}

interface ReviewPreviewInteraction : Interaction {
    fun review(files: Map<Path, JobFile?>, autoApproveThreshold: Double = 1.0): Map<Path, JobFile>

    override fun isEligible(file: Pair<Path, JobFile>): Boolean =
        file.second.let { it.crops.isNotEmpty() && !it.approved }
}
