package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.gui.segmentation.SegmentationInterface
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.MutableWaitFlow
import au.edu.unimelb.archivetool.util.WaitFlow
import au.edu.unimelb.archivetool.util.extension.isNotNull
import au.edu.unimelb.archivetool.util.extension.noop
import au.edu.unimelb.archivetool.util.logging.Log
import io.nacular.doodle.application.Modules.Companion.ImageModule
import io.nacular.doodle.application.Modules.Companion.PointerModule
import io.nacular.doodle.application.application
import io.nacular.doodle.theme.basic.BasicTheme.Companion.basicButtonBehavior
import io.nacular.doodle.theme.basic.BasicTheme.Companion.basicLabelBehavior
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.kodein.di.instance

class ReviewGUIInteraction : ReviewPreviewInteraction {
    override fun review(
        files: Map<JobRelativePath, JobFile?>,
        autoApproveThreshold: Double,
    ): Map<JobRelativePath, JobFile> {
        lateinit var resultFlow: WaitFlow<Map<JobRelativePath, JobFile>>
        lateinit var result: Map<JobRelativePath, JobFile>
        val app = application(
            modules =
                listOf(basicLabelBehavior(), basicButtonBehavior(), PointerModule, ImageModule)
        ) {
            val returnFlow = MutableWaitFlow<Map<JobRelativePath, JobFile>>(mapOf())
            resultFlow = returnFlow
            SegmentationInterface(
                jobs = files,
                display = instance(),
                windows = instance(),
                themeManager = instance(),
                theme = instance(),
                imageLoader = instance(),
                returnFlow = returnFlow
            )
        }
        runBlocking {
            result = resultFlow.collect()
            app.shutdown()
        }
        return result
    }
}

class MockReviewInteraction : ReviewPreviewInteraction {
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

interface ReviewPreviewInteraction : Interaction {
    fun review(
        files: Map<JobRelativePath, JobFile?>,
        autoApproveThreshold: Double = 1.0,
    ): Map<JobRelativePath, JobFile>

    override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean =
        file.second.let { it.vertexes.isNotNull() && !it.approved }
}
