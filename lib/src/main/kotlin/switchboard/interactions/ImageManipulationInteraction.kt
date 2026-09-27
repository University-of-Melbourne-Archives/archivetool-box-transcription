package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.switchboard.interactions.imagemanip.CropInteraction
import au.edu.unimelb.archivetool.switchboard.interactions.imagemanip.FlattenInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.logging.Log

class MockImageManipulationInteraction : ImageManipulationInteraction {
    override fun manipulate(files: Map<JobRelativePath, JobFile>): Map<JobRelativePath, JobFile> {
        Log.error("Using mock image manipulation tool, nothing will happen.")
        files.keys.forEach { Log.debug("Manipulating Image ${it.relative}") }
        return mapOf()
    }
}

// TODO add a helper function that loads an existing manipulated version of a file if it exists.
// TODO add an interaction that blanks out labels from box images
// TODO should have a thing that converts image to an API supported format
interface ImageManipulationInteraction : Interaction {
    fun manipulate(files: Map<JobRelativePath, JobFile>): Map<JobRelativePath, JobFile>

    override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean =
        // NOTE: We always re-run image processing because there's no good way to know if we need to
        // apply/reapply.
        file.second.let { it.vertexes?.isNotEmpty() ?: false && it.approved }

    companion object : ComposableInteraction<ImageManipulationInteraction> {
        override fun compose(
            vararg children: ImageManipulationInteraction
        ): ImageManipulationInteraction {
            require(
                // If we have a Flatten, we already crop so we cant have both.
                !(children.any { it is FlattenInteraction }) ||
                    !(children.any { it is CropInteraction })
            ) {
                "FlattenInteraction is mutually exclusive with CropInteraction and cannot compose with it."
            }
            return object : ImageManipulationInteraction {
                override fun manipulate(
                    files: Map<JobRelativePath, JobFile>
                ): Map<JobRelativePath, JobFile> {
                    var stillValid = files
                    children.forEach { stillValid = it.manipulate(stillValid) }
                    return stillValid
                }

                override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean =
                    children.all {
                        it.isEligible(file)
                    }
            }
        }
    }
}
