package au.edu.unimelb.archivetool.switchboard.interactions

class UnimplementedCropInteraction : CropInteraction {
    override fun applyCrops() {
        TODO("Not yet implemented")
    }
}

interface CropInteraction : Interaction {
    fun applyCrops()
}