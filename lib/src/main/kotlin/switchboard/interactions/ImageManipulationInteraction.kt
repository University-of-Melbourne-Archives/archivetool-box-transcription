package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import java.nio.file.Path

class MockImageManipulationInteraction : ImageManipulationInteraction {
    override fun apply() {
        TODO("Not yet implemented")
    }
}

class CropInteraction : ImageManipulationInteraction {
    override fun apply() {
        TODO("Not yet implemented")
    }
}

class FlattenInteraction : ImageManipulationInteraction {
    override fun apply() {
        TODO("Not yet implemented")
    }

    override fun isEligible(file: Pair<Path, JobFile>): Boolean {
        return super.isEligible(file) // TODO: Also check that the image has already been cropped.
    }
}

interface ImageManipulationInteraction : Interaction {
    fun apply()

    override fun isEligible(file: Pair<Path, JobFile>): Boolean =
        file.second.let {
            it.crops.isNotEmpty() &&
                it.approved &&
                true /*TODO: Add a check for if the generated crop etc exists.*/
        }

    companion object : ComposableInteraction<ImageManipulationInteraction> {
        override fun compose(
            vararg children: ImageManipulationInteraction
        ): ImageManipulationInteraction {
            return object : ImageManipulationInteraction {
                override fun apply() {
                    children.forEach { it.apply() }
                }

                override fun isEligible(file: Pair<Path, JobFile>): Boolean = children.all {
                    it.isEligible(file)
                }
            }
        }
    }
}
