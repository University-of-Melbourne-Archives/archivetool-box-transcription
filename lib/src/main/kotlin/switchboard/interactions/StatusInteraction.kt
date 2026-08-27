package au.edu.unimelb.archivetool.switchboard.interactions

class UnimplementedStatusInteraction : StatusInteraction {
    override fun getStatus() {
        TODO("Not yet implemented")
    }
}

interface StatusInteraction : Interaction {
    fun getStatus()
}