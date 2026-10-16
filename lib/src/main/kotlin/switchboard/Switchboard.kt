package au.edu.unimelb.archivetool.switchboard

import au.edu.unimelb.archivetool.switchboard.interactions.*
import au.edu.unimelb.archivetool.switchboard.interactions.export.CSVExportInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.imagemanip.CLAHEInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.imagemanip.FlattenInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import au.edu.unimelb.archivetool.switchboard.jobstate.json.JSONJobStateInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.memory.MemoryJobStateInteraction
import au.edu.unimelb.archivetool.util.error.expect
import au.edu.unimelb.archivetool.util.extension.orNull
import au.edu.unimelb.archivetool.util.logging.LogConsumer
import java.nio.file.Path
import kotlin.io.path.Path

// Implements a switchboard with default options.
// You should never use this in its global scope,
// rather assigning it to a `AbstractSwitchboard` val.
// You can swap out a given interaction using `.with` or create an AbstractSwitchboard yourself.
// You can also make additional switchboard types, like a `MockSwitchboard` or something.
// Also note: Config Creation Interactions are composed internally in the constructor.
object Switchboard :
    AbstractSwitchboard(
        LogInitInteraction(),
        JSONConfigInteraction(),
        JSONJobStateInteraction(),
        StdoutStatusInteraction(),
        KotlinCreatePreviewInteraction(),
        SegmentationGUIInteraction(),
        OCRGUIInteraction(),
        ImageManipulationInteraction.compose(FlattenInteraction(), CLAHEInteraction()),
        PythonOCRInteraction(),
        CSVExportInteraction(
            Path("/")
        ), // TODO: Flag if the export destination is not set. We should set this to a default in the
        // CLI before getting here.
    )

object MockSwitchboard :
    AbstractSwitchboard(
        MockInitInteraction(),
        MockConfigInteraction(),
        MemoryJobStateInteraction(),
        StdoutStatusInteraction(),
        MockCreatePreviewInteraction(),
        MockSegmentationReviewInteraction(),
        MockOCRReviewInteraction(),
        MockImageManipulationInteraction(),
        MockOCRInteraction(),
        LogExportInteraction(),
    )

// Acts as a dependency-injected base. Allows for 'hot-swappable' behaviors.
// Currently just takes one interaction per command,
// but will later also take stuff for things like Job data format parsing, or initial setup things.
open class AbstractSwitchboard(
    var _init: InitInteraction,
    _config: ConfigInteraction,
    _job: JobStateInteraction,
    _status: StatusInteraction,
    _createPreview: CreatePreviewInteraction,
    _segmentationReviewPreview: SegmentationPreviewInteraction,
    _ocrReviewPreview: OCRPreviewInteraction,
    _manip: ImageManipulationInteraction,
    _ocr: OCRInteraction,
    _export: ExportInteraction,
) {
    var init: InitInteraction = _config.createInteraction()?.let {
        InitInteraction.compose(it, _init )
    } ?: _init
        private set

    var config: ConfigInteraction = _config
        private set

    var job: JobStateInteraction = _job
        private set

    var status: StatusInteraction = _status
        private set

    var createPreview: CreatePreviewInteraction = _createPreview
        private set

    var segmentationPreview: SegmentationPreviewInteraction = _segmentationReviewPreview
        private set

    var ocrPreview: OCRPreviewInteraction = _ocrReviewPreview
        private set

    var manip: ImageManipulationInteraction = _manip
        private set

    var ocr: OCRInteraction = _ocr
        private set

    var export: ExportInteraction = _export
        private set

    // Copy Constructor
    constructor(
        sw: AbstractSwitchboard
    ) : this(
        sw.init,
        sw.config,
        sw.job,
        sw.status,
        sw.createPreview,
        sw.segmentationPreview,
        sw.ocrPreview,
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
            is ConfigInteraction -> {
                config = swapFor
                init = config.createInteraction()?.let {
                    InitInteraction.compose(it, _init )
                } ?: _init
            }
            is CreatePreviewInteraction -> createPreview = swapFor
            is ImageManipulationInteraction -> manip = swapFor
            is ExportInteraction -> export = swapFor
            is InitInteraction -> {
                _init = swapFor
                init = config.createInteraction()?.let {
                    InitInteraction.compose(it, swapFor )
                } ?: swapFor
            }
            is JobStateInteraction -> job = swapFor
            is OCRInteraction -> ocr = swapFor
            is SegmentationPreviewInteraction -> segmentationPreview = swapFor
            is OCRPreviewInteraction -> ocrPreview = swapFor
            is StatusInteraction -> status = swapFor
        }
    }

    fun swap(vararg swapFor: Interaction) = swapFor.forEach { swap(it) }

    fun init(logConsumers: Set<LogConsumer>) {
        init.init(logConsumers)
    }

    fun getStatus(directory: Path) {
        val state: JobState? =
            job.companion.present(directory).orNull()?.let { job.read(directory) }

        val inFiles =
            state?.validFiles(status, allowNulls = true)?.also { it.takeUnless { it.isEmpty() } }

        status.getStatus(inFiles)
    }

    fun createPreview(
        directory: Path,
        permitExisting: Boolean = false,
        weights: Path? = null,
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

    fun reviewSegmentationPreview(directory: Path, autoApproveThreshold: Double = 1.0) {
        val state: JobState? = job.read(directory)
        val toUpdate =
            segmentationPreview.review(
                state
                    ?.validFiles(segmentationPreview)
                    ?.also {
                        it.takeUnless { it.isEmpty() }.expect("No valid files for this step.")
                    }
                    .expect("Could not load list of valid files."),
                autoApproveThreshold,
            )
        state?.update(toUpdate)
    }

    fun reviewOCRPreview(directory: Path, autoApproveThreshold: Double = 1.0) {
        val state: JobState? = job.read(directory)
        val toUpdate =
            ocrPreview.review(
                state
                    ?.validFiles(ocrPreview)
                    ?.also {
                        it.takeUnless { it.isEmpty() }.expect("No valid files for this step.")
                    }
                    .expect("Could not load list of valid files."),
                autoApproveThreshold,
            )
        state?.update(toUpdate)
    }

    fun manipulateImages(directory: Path) {
        val state: JobState? = job.read(directory)
        val toUpdate =
            manip.manipulate(
                state
                    ?.validFiles(manip)
                    ?.also {
                        it.takeUnless { it.isEmpty() }.expect("No valid files for this step.")
                    }
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
                    ?.also {
                        it.takeUnless { it.isEmpty() }.expect("No valid files for this step.")
                    }
                    .expect("Could not load list of valid files."),
                config.getConfig(),
            )
        state?.update(toUpdate)
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
