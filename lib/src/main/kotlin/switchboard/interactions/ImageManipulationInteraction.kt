package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import java.nio.file.Path

class MockImageManipulationInteraction : ImageManipulationInteraction {
    override fun manipulate(files: Map<Path, JobFile>): Map<Path, JobFile> {
        Log.error("Using mock image manipulation tool, nothing will happen.")
        files.keys.forEach { Log.info("Manipulating Image $it") }
        return mapOf()
    }
}

class CropInteraction : ImageManipulationInteraction {
    override fun manipulate(files: Map<Path, JobFile>): Map<Path, JobFile> {
        TODO("Not yet implemented")
    }
}

class FlattenInteraction : ImageManipulationInteraction {
    override fun manipulate(files: Map<Path, JobFile>): Map<Path, JobFile> {
        // These are technically composable, but flatten depends on crop.
        CropInteraction().manipulate(files)
        TODO("Not yet implemented")
    }
}

class MonochromifyInteraction : ImageManipulationInteraction {
    override fun manipulate(files: Map<Path, JobFile>): Map<Path, JobFile> {
        TODO("Not yet implemented")
    }
}

// TODO add a helper function that loads an existing manipulated version of a file if it exists.
interface ImageManipulationInteraction : Interaction {
    fun manipulate(files: Map<Path, JobFile>): Map<Path, JobFile>

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
            require(
                // If we have a Flatten, we already crop so we cant have both.
                !(children.any { it is FlattenInteraction }) ||
                    !(children.any { it is CropInteraction })
            ) {
                "FlattenInteraction already depends on CropInteraction and cannot compose with it."
            }
            return object : ImageManipulationInteraction {
                override fun manipulate(files: Map<Path, JobFile>): Map<Path, JobFile> {
                    var stillValid = files
                    children.forEach { stillValid = it.manipulate(stillValid) }
                    return stillValid
                }

                override fun isEligible(file: Pair<Path, JobFile>): Boolean = children.all {
                    it.isEligible(file)
                }
            }
        }
    }
}
