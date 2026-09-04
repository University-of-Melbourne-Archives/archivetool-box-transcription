package au.edu.unimelb.archivetool.switchboard.interactions

class CropInteraction : ImageManipulationInteraction {
    override fun apply() {
        TODO("Not yet implemented")
    }
}

class FlattenInteraction : ImageManipulationInteraction {
    override fun apply() {
        TODO("Not yet implemented")
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
