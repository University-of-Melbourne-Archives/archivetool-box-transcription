package au.edu.unimelb.archivetool.switchboard

import au.edu.unimelb.archivetool.switchboard.interactions.CSVExportInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.StatusInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.CreatePreviewInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.ReviewPreviewInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.CropInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.OCRInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.ExportInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.PythonCreatePreviewInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.PythonOCRInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.PythonReviewGUIInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.UnimplementedCropInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.UnimplementedStatusInteraction

// Implements a switchboard with default options.
// Allows for easy swapping or modification.
// You can also make additional switchboard types, like a `MockSwitchboard` or something.
object Switchboard: AbstractSwitchboard(
    UnimplementedStatusInteraction(),
    PythonCreatePreviewInteraction(),
    PythonReviewGUIInteraction(),
    UnimplementedCropInteraction(),
    PythonOCRInteraction(),
    CSVExportInteraction()
)

// Acts as a dependency-injected base. Allows for 'hot-swappable' behaviors.
// Currently just takes one interaction per command,
// but will later also take stuff for things like Job data format parsing, or initial setup things.
sealed class AbstractSwitchboard(
    val status: StatusInteraction,
    val createPreview: CreatePreviewInteraction,
    val reviewPreview: ReviewPreviewInteraction,
    val crop: CropInteraction,
    val ocr: OCRInteraction,
    val export: ExportInteraction
) {
    fun getStatus() {
        status.getStatus()
    }
    fun createPreview() {
        createPreview.createPreview()
    }
    fun reviewPreview() {
        reviewPreview.review()
    }
    fun applyCrops() {
        crop.applyCrops()
    }
    fun createOCR() {
        ocr.generateOCR()
    }
    fun generateExport() {
        export.export()
    }
}