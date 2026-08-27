package au.edu.unimelb.archivetool.switchboard.interactions

class PythonCreatePreviewInteraction : CreatePreviewInteraction {
    override fun createPreview() {
        TODO("This requires PythonInterop code to be merged.")
    }
}

interface CreatePreviewInteraction : Interaction {
    fun createPreview()
}