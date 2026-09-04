package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.LogConsumer
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import java.nio.file.Path

class LogInitInteraction : InitInteraction {
    override fun init(logConsumers: Set<LogConsumer>) {
        logConsumers.forEach { Log.registerConsumer(it) }
    }

    // This is an interaction that doesn't need files.
    override fun isEligible(file: Pair<Path, JobFile>): Boolean = false
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

                override fun isEligible(file: Pair<Path, JobFile>): Boolean = children.all {
                    it.isEligible(file)
                }
            }
        }
    }
}
