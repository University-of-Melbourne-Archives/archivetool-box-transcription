package au.edu.unimelb.archivetool.switchboard.interactions

// Designed to go on a companion object, allows for composition in a number of ways.
interface ComposableInteraction<T : Interaction> {
    fun compose(vararg children: T): T
}
