package au.edu.unimelb.archivetool.util

import au.edu.unimelb.archivetool.util.extension.isNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.takeWhile

class MutableWaitFlow<T : Any>(initialValue: T) : WaitFlow<T>() {
    override var innerFlow: MutableStateFlow<T?> = MutableStateFlow(initialValue)

    suspend fun emit(value: T) = innerFlow.emit(value)

    suspend fun close() = innerFlow.emit(null)
}

sealed class WaitFlow<T : Any> {
    protected abstract var innerFlow: MutableStateFlow<T?>

    suspend fun collect(collector: FlowCollector<T> = {}): T {
        lateinit var out: T
        collector.apply { innerFlow.takeWhile { it.isNotNull() }.collect { out = it!! } }

        return out
    }
}
