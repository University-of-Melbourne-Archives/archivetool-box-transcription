package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.switchboard.AbstractSwitchboard
import au.edu.unimelb.archivetool.switchboard.Switchboard
import au.edu.unimelb.archivetool.switchboard.interactions.export.CSVFileExportInteraction
import java.nio.file.Path

internal object CliSwitchboard {
    fun createPreview(inputDirectory: Path, permitExisting: Boolean = false) {
        Switchboard.createPreview(normalise(inputDirectory), permitExisting = permitExisting)
    }

    fun reviewPreview(inputDirectory: Path, autoApproveThreshold: Int?) {
        reviewPreview(Switchboard, normalise(inputDirectory), autoApproveThreshold)
    }

    fun getStatus(inputDirectory: Path) {
        Switchboard.getStatus(normalise(inputDirectory))
    }

    fun finalise(inputDirectory: Path, outputPath: Path) {
        Switchboard.with(CSVFileExportInteraction(normalise(outputPath)))
            .generateExport(normalise(inputDirectory))
    }

    fun runWorkflow(
        inputDirectory: Path,
        outputPath: Path,
        resume: Boolean,
        autoApproveThreshold: Int?,
    ) {
        val jobRoot = normalise(inputDirectory)
        val switchboard = Switchboard.with(CSVFileExportInteraction(normalise(outputPath)))

        switchboard.createPreview(jobRoot, permitExisting = resume)
        reviewPreview(switchboard, jobRoot, autoApproveThreshold)
        switchboard.manipulateImages(jobRoot)
        switchboard.createOCR(jobRoot)
        switchboard.generateExport(jobRoot)
    }

    private fun reviewPreview(
        switchboard: AbstractSwitchboard,
        jobRoot: Path,
        autoApproveThreshold: Int?,
    ) {
        if (autoApproveThreshold == null) {
            switchboard.reviewPreview(jobRoot)
        } else {
            switchboard.reviewPreview(jobRoot, autoApproveThreshold / 100.0)
        }
    }

    private fun normalise(path: Path): Path = path.toAbsolutePath().normalize()
}
