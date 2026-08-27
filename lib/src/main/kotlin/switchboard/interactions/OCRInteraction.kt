package au.edu.unimelb.archivetool.switchboard.interactions

class PythonOCRInteraction : OCRInteraction {
    override fun generateOCR() {
        TODO("This requires PythonInterop code to be merged.")
    }
}

interface OCRInteraction : Interaction {
    fun generateOCR()
}