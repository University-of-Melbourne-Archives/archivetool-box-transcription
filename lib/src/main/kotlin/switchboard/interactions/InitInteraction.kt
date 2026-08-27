package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.LogConsumer

class LogInitInteraction : InitInteraction {
    override fun init(logConsumers: Set<LogConsumer>) {
        logConsumers.forEach { Log.registerConsumer(it) }
    }
}

interface InitInteraction : Interaction {
    // this defines a bunch of params, you don't have to use them all lol
    fun init(logConsumers: Set<LogConsumer>)

    companion object : ComposableInteraction<InitInteraction> {
        override fun compose(vararg children: InitInteraction): InitInteraction {
            return object : InitInteraction {
                override fun init(logConsumers: Set<LogConsumer>) {
                    children.forEach { it.init(logConsumers) }
                }
            }
        }
    }
}