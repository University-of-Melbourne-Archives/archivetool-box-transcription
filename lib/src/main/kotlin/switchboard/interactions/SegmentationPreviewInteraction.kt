package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.gui.applicationWithResult
import au.edu.unimelb.archivetool.gui.segmentation.SegmentationInterface
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.extension.isNotNull
import au.edu.unimelb.archivetool.util.file.Image
import au.edu.unimelb.archivetool.util.logging.Log
import io.nacular.doodle.application.Modules.Companion.ImageModule
import io.nacular.doodle.application.Modules.Companion.PointerModule
import io.nacular.doodle.theme.basic.BasicTheme.Companion.basicButtonBehavior
import io.nacular.doodle.theme.basic.BasicTheme.Companion.basicLabelBehavior
import kotlinx.coroutines.runBlocking
import org.kodein.di.instance

class SegmentationGUIInteraction : SegmentationPreviewInteraction, GUIInteraction {
    override fun mayRequireConversion(): Boolean = true

    override fun supported(): Image.FileType.Supported = super<GUIInteraction>.supported()

    override fun review(
        files: Map<JobRelativePath, JobFile?>,
        autoApproveThreshold: Double,
    ): Map<JobRelativePath, JobFile> {
        Log.info("Running ReviewGUIInteraction")
        return runBlocking {
            applicationWithResult<SegmentationInterface, Map<JobRelativePath, JobFile>>(
                mapOf(),
                listOf(basicLabelBehavior(), basicButtonBehavior(), PointerModule, ImageModule),
            ) {
                SegmentationInterface(
                    jobs = files,
                    display = instance(),
                    windows = instance(),
                    themeManager = instance(),
                    theme = instance(),
                    imageLoader = instance(),
                    returnFlow = it,
                )
            }
        }
    }
}

class MockSegmentationReviewInteraction : SegmentationPreviewInteraction {
    override fun review(
        files: Map<JobRelativePath, JobFile?>,
        autoApproveThreshold: Double,
    ): Map<JobRelativePath, JobFile> {
        Log.error("Using Mock Implementation of Review Preview. Nothing will be done.")

        Log.debug(
            "${files.count()} total files, ${files.filterValues { (it?.minConfidence ?: -1.0) > autoApproveThreshold}.count() } above auto-approval threshold $autoApproveThreshold"
        )

        return mapOf()
    }
}

interface SegmentationPreviewInteraction : Interaction {
    fun review(
        files: Map<JobRelativePath, JobFile?>,
        autoApproveThreshold: Double = 1.0,
    ): Map<JobRelativePath, JobFile>

    override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean =
        file.second.let { it.vertexes.isNotNull() && !it.approved }
}
