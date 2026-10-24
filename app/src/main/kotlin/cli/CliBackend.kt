package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.switchboard.Switchboard
import au.edu.unimelb.archivetool.switchboard.interactions.export.CSVFileExportInteraction
import au.edu.unimelb.archivetool.util.logging.LogConsumer
import java.nio.file.Path

/**
 * The CLI calls Switchboard through this interface. In tests we replace it with a small recorder,
 * so OCR and the review windows do not need to start.
 */
interface CliBackend {
    fun init(consumers: Set<LogConsumer>)

    fun createPreview(directory: Path, permitExisting: Boolean)

    fun reviewPreview(directory: Path, threshold: Double)

    fun prepareImages(directory: Path)

    fun createOCR(directory: Path)

    fun reviewOCR(directory: Path)

    fun status(directory: Path)

    fun export(directory: Path, output: Path)
}

object SwitchboardCliBackend : CliBackend {
    override fun init(consumers: Set<LogConsumer>) = Switchboard.init(consumers)

    override fun createPreview(directory: Path, permitExisting: Boolean) =
        Switchboard.createPreview(directory, permitExisting = permitExisting)

    override fun reviewPreview(directory: Path, threshold: Double) =
        Switchboard.reviewSegmentationPreview(directory, threshold)

    override fun prepareImages(directory: Path) = Switchboard.manipulateImages(directory)

    override fun createOCR(directory: Path) = Switchboard.createOCR(directory)

    override fun reviewOCR(directory: Path) = Switchboard.reviewOCRPreview(directory)

    override fun status(directory: Path) = Switchboard.getStatus(directory)

    override fun export(directory: Path, output: Path) =
        Switchboard.with(CSVFileExportInteraction(output)).generateExport(directory)
}
