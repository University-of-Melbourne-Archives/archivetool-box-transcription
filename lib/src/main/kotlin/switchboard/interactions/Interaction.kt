package au.edu.unimelb.archivetool.switchboard.interactions

interface StatusInteraction {
    fun getStatus() {}
}

interface CreatePreviewInteraction {
    fun createPreview() {}
}

interface ReviewInteraction {
    fun review() {}
}

interface CropInteraction {
    fun applyCrops() {}
}

interface OCRInteraction {
    fun generateOCR() {}
}

interface ExportInteraction {
    fun export() {}
}