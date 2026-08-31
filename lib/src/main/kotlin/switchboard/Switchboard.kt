package au.edu.unimelb.archivetool.switchboard

import au.edu.unimelb.archivetool.logging.LogConsumer
import au.edu.unimelb.archivetool.switchboard.interactions.CSVExportInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.StatusInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.CreatePreviewInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.ReviewPreviewInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.ImageManipulationInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.OCRInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.ExportInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.InitInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.Interaction
import au.edu.unimelb.archivetool.switchboard.interactions.JSONJobStateInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.JobStateInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.LogInitInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.PythonCreatePreviewInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.PythonOCRInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.PythonReviewGUIInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.CropInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.FlattenInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.UnimplementedStatusInteraction

// Implements a switchboard with default options.
// You should never use this in its global scope,
// rather assigning it to a `AbstractSwitchboard` val.
// You can swap out a given interaction using `.with` or create an AbstractSwitchboard yourself.
// You can also make additional switchboard types, like a `MockSwitchboard` or something.
object Switchboard: AbstractSwitchboard(
    LogInitInteraction(),
    JSONJobStateInteraction(),
    UnimplementedStatusInteraction(),
    PythonCreatePreviewInteraction(),
    PythonReviewGUIInteraction(),
    ImageManipulationInteraction.compose(CropInteraction(), FlattenInteraction()),
    PythonOCRInteraction(),
    CSVExportInteraction()
)

// Acts as a dependency-injected base. Allows for 'hot-swappable' behaviors.
// Currently just takes one interaction per command,
// but will later also take stuff for things like Job data format parsing, or initial setup things.
open class AbstractSwitchboard(
    _init: InitInteraction,
    _job: JobStateInteraction,
    _status: StatusInteraction,
    _createPreview: CreatePreviewInteraction,
    _reviewPreview: ReviewPreviewInteraction,
    _crop: ImageManipulationInteraction,
    _ocr: OCRInteraction,
    _export: ExportInteraction
) {
    var init: InitInteraction
        private set
    var job: JobStateInteraction
        private set
    var status: StatusInteraction
        private set
    var createPreview: CreatePreviewInteraction
        private set
    var reviewPreview: ReviewPreviewInteraction
        private set
    var crop: ImageManipulationInteraction
        private set
    var ocr: OCRInteraction
        private set
    var export: ExportInteraction
        private set

    init {
        init = _init
        job = _job
        status = _status
        createPreview = _createPreview
        reviewPreview = _reviewPreview
        crop = _crop
        ocr = _ocr
        export = _export
    }

    // Allows for on-the-fly swapping of interactions.
    fun with(swapFor: Interaction) {
        when (swapFor) {
            is CreatePreviewInteraction -> createPreview = swapFor
            is ImageManipulationInteraction -> crop = swapFor
            is ExportInteraction -> export = swapFor
            is InitInteraction -> init = swapFor
            is JobStateInteraction -> job = swapFor
            is OCRInteraction -> ocr = swapFor
            is ReviewPreviewInteraction -> reviewPreview = swapFor
            is StatusInteraction -> status = swapFor
        }
    }

    fun init(logConsumers: Set<LogConsumer>) {
        init.init(logConsumers)
    }

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
        crop.apply()
    }
    fun createOCR() {
        ocr.generateOCR()
    }
    fun generateExport() {
        export.export()
    }
}