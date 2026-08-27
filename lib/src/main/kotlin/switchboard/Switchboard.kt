package au.edu.unimelb.archivetool.switchboard

import au.edu.unimelb.archivetool.switchboard.interactions.StatusInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.CreatePreviewInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.ReviewInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.CropInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.OCRInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.ExportInteraction

/*
// Implements a switchboard with default options.
// Allows for easy swapping or modification.
// You can also make additional switchboard types, like a `MockSwitchboard` or something.
object Switchboard: AbstractSwitchboard(

)
*/

// Acts as a dependency-injected base. Allows for 'hot-swappable' behaviors.
// Currently just takes one interaction per command,
// but will later also take stuff for things like Job data format parsing.
sealed class AbstractSwitchboard(
    val status: StatusInteraction,
    val createPreview: CreatePreviewInteraction,
    val reviewPreview: ReviewInteraction,
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