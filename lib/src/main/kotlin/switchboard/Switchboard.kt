package au.edu.unimelb.archivetool.switchboard

import au.edu.unimelb.archivetool.logging.Log.expect
import au.edu.unimelb.archivetool.logging.LogConsumer
import au.edu.unimelb.archivetool.switchboard.interactions.*
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import au.edu.unimelb.archivetool.switchboard.jobstate.json.JSONJobStateInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.memory.MemoryJobStateInteraction
import java.nio.file.Path

// Implements a switchboard with default options.
// You should never use this in its global scope,
// rather assigning it to a `AbstractSwitchboard` val.
// You can swap out a given interaction using `.with` or create an AbstractSwitchboard yourself.
// You can also make additional switchboard types, like a `MockSwitchboard` or something.
object Switchboard :
    AbstractSwitchboard(
        LogInitInteraction(),
        JSONJobStateInteraction(),
        StdoutStatusInteraction(),
        PythonCreatePreviewInteraction(),
        ReviewGUIInteraction(),
        ImageManipulationInteraction.compose(FlattenInteraction(), MonochromifyInteraction()),
        PythonOCRInteraction(),
        CSVExportInteraction(),
    )

object MockSwitchboard :
    AbstractSwitchboard(
        MockInitInteraction(),
        MemoryJobStateInteraction(),
        StdoutStatusInteraction(),
        MockCreatePreviewInteraction(),
        MockReviewInteraction(),
        MockImageManipulationInteraction(),
        MockOCRInteraction(),
        LogExportInteraction(),
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
    _manip: ImageManipulationInteraction,
    _ocr: OCRInteraction,
    _export: ExportInteraction,
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

    var manip: ImageManipulationInteraction
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
        manip = _manip
        ocr = _ocr
        export = _export
    }

    // Copy Constructor
    constructor(
        sw: AbstractSwitchboard
    ) : this(
        sw.init,
        sw.job,
        sw.status,
        sw.createPreview,
        sw.reviewPreview,
        sw.manip,
        sw.ocr,
        sw.export,
    )

    // Creates a new Switchboard with the swapped interaction.
    fun with(swapFor: Interaction): AbstractSwitchboard =
        AbstractSwitchboard(this).apply { swap(swapFor) }

    fun with(vararg swapFor: Interaction): AbstractSwitchboard =
        AbstractSwitchboard(this).apply { swap(*swapFor) }

    // Allows for on-the-fly swapping of interactions.
    fun swap(swapFor: Interaction) {
        when (swapFor) {
            is CreatePreviewInteraction -> createPreview = swapFor
            is ImageManipulationInteraction -> manip = swapFor
            is ExportInteraction -> export = swapFor
            is InitInteraction -> init = swapFor
            is JobStateInteraction -> job = swapFor
            is OCRInteraction -> ocr = swapFor
            is ReviewPreviewInteraction -> reviewPreview = swapFor
            is StatusInteraction -> status = swapFor
        }
    }

    fun swap(vararg swapFor: Interaction) = swapFor.forEach { swap(it) }

    fun init(logConsumers: Set<LogConsumer>) {
        init.init(logConsumers)
    }

    fun getStatus() {
        status.getStatus()
    }

    fun createPreview(
        directory: Path,
        permitExisting: Boolean = false,
        weights: Path = TODO("Put a default Weight Path here."),
        rejectThreshold: Double = 0.05,
    ) {
        val state: JobState? =
            if (permitExisting && job.companion.present(directory)) {
                job.read(directory) ?: job.create(directory)
            } else {
                job.create(directory)
            }

        val inFiles =
            state?.validFiles(createPreview, allowNulls = true)?.also {
                it.takeUnless { it.isEmpty() }.expect("No valid files for this step.")
            }

        val toUpdate =
            createPreview.createPreview(
                inFiles?.keys.expect("Could not load list of valid files."),
                weights,
                rejectThreshold,
            )

        state?.update(toUpdate)
    }

    fun reviewPreview(directory: Path) {
        val state: JobState? = job.read(directory)
        val toUpdate =
            reviewPreview.review(
                state
                    ?.validFiles(reviewPreview)
                    ?.also {
                        it.takeUnless { it.isEmpty() }.expect("No valid files for this step.")
                    }
                    .expect("Could not load list of valid files.")
            )
        state?.update(toUpdate)
    }

    fun manipulateImages(directory: Path) {
        val state: JobState? = job.read(directory)
        val toUpdate =
            manip.manipulate(
                state
                    ?.validFiles(manip)
                    ?.also { it.takeUnless { it.isEmpty() }.expect("No valid files for this step.") }
                    .expect("Could not load list of valid files.")
            )
        state?.update(toUpdate)
    }

    fun createOCR(directory: Path) {
        val state: JobState? = job.read(directory)
        val toUpdate =
            ocr.generateOCR(
                state
                    ?.validFiles(ocr)
                    ?.also { it.takeUnless { it.isEmpty() }.expect("No valid files for this step.") }
                    .expect("Could not load list of valid files.")
            )
    }

    fun generateExport(directory: Path) {
        val state: JobState? = job.read(directory)
        export.export(
            state
                ?.validFiles(export)
                ?.also { it.takeUnless { it.isEmpty() }.expect("No valid files for this step.") }
                .expect("Could not load list of valid files.")
        )
    }
}
