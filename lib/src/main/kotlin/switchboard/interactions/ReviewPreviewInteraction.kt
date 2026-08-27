package au.edu.unimelb.archivetool.switchboard.interactions

class PythonReviewGUIInteraction : ReviewPreviewInteraction {
    override fun review() {
        TODO("This requires PythonInterop code to be merged.")
    }
}

interface ReviewPreviewInteraction : Interaction {
    fun review()
}