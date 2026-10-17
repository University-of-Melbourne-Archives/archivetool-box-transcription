package au.edu.unimelb.archivetool.util

import au.edu.unimelb.archivetool.util.extension.isNotNull
import au.edu.unimelb.archivetool.util.extension.isNull
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun <This, T : Any> invalidatableLazy(
    lock: Any? = null,
    initializer: () -> T,
): ReadOnlyProperty<This, Pair<T, InvalidatableLazy.Companion.InvalidateFunc>> =
    InvalidatableLazy<This, T>(initializer, lock)

private class InvalidatableLazy<in This, out T : Any>(
    private val initializer: () -> T,
    lock: Any? = null,
) : ReadOnlyProperty<This, Pair<T, InvalidatableLazy.Companion.InvalidateFunc>> {
    @Volatile private var _value: T? = null

    // final field to ensure safe publication of 'SynchronizedLazyImpl' itself through
    // var lazy = lazy() {}
    private val lock = lock ?: this

    suspend fun invalidate() = Companion[lock].withLock { _value = null }

    fun isInitialized(): Boolean = _value.isNull()

    override fun toString(): String =
        if (isInitialized()) _value.toString() else "Lazy value not initialized yet."

    override fun getValue(thisRef: This, property: KProperty<*>): Pair<T, InvalidateFunc> =
        runBlocking {
            Companion[lock].withLock {
                if (_value.isNotNull()) {
                    _value!!
                } else {
                    val value = initializer()
                    _value = value
                    value
                }
            }
        } to ::invalidate

    companion object {
        typealias InvalidateFunc = suspend () -> Unit

        private val _locks: MutableMap<Any, Mutex> = mutableMapOf()

        fun getMutex(lock: Any): Mutex = _locks.getOrPut(lock, ::Mutex)

        operator fun get(lock: Any) = getMutex(lock)
    }
}
