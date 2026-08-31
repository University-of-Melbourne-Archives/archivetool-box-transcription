package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.logging.LogConsumer

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

    companion object : ComposableInteraction<ImageManipulationInteraction> {
        override fun compose(vararg children: ImageManipulationInteraction): ImageManipulationInteraction {
            return object : ImageManipulationInteraction {
                override fun apply() {
                    children.forEach { it.apply() }
                }
            }
        }
    }
}