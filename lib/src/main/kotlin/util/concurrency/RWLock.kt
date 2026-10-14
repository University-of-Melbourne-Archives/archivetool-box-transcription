package au.edu.unimelb.archivetool.util.concurrency

import au.edu.unimelb.archivetool.util.Ref
import au.edu.unimelb.archivetool.util.logging.Log
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.runBlocking

const val RWLock_READERS_MAX = Int.MAX_VALUE

class RWLock<T>(
    private var inner: T,
    val maxReaders: Int = RWLock_READERS_MAX,
    private val internalLock: NonCapturingRWLock = NonCapturingRWLock(),
) {
    var value
        get() = runBlocking { readAction { value } }
        set(new) = runBlocking { writeAction { value = new } }

    suspend fun <R> withReadLock(body: suspend (T) -> R): R {
        return readAction { body(value) }
    }

    suspend fun <R> withWriteLock(body: suspend (T) -> R): R {
        return writeAction { body(value) }
    }

    private suspend fun <R> readAction(action: suspend RWLockReadGuard<T>.() -> R): R {
        return when (internalLock) {
            // is ReentrantReadWriteLock -> internalLock.read(action)
            else -> {
                readLock().use { it.action() }
            }
        }
    }

    private suspend fun <R> writeAction(action: suspend RWLockWriteGuard<T>.() -> R): R {
        return when (internalLock) {
            // is ReentrantReadWriteLock -> internalLock.write(action)
            else -> {
                writeLock().use { it.action() }
            }
        }
    }

    fun tryReadLock(): RWLockTryReadGuard<T>? = RWLockTryReadGuard.new(Ref(::inner), internalLock)

    suspend fun readLock(): RWLockReadGuard<T> = RWLockReadGuard.new(Ref(::inner), internalLock)

    fun tryWriteLock(): RWLockTryWriteGuard<T>? =
        RWLockTryWriteGuard.new(Ref(::inner), internalLock)

    suspend fun writeLock(): RWLockWriteGuard<T> = RWLockWriteGuard.new(Ref(::inner), internalLock)

    sealed class RWLockGuard<T>
    private constructor(protected var inner: Ref<T>?, pleasePickPrimaryConstructor: Unit) {
        protected abstract val unlock: NonCapturingRWLock.Unlocker

        constructor(inner: Ref<T>) : this(inner, Unit)

        private fun get(): T {
            if (inner == null) error("Tried to read inner value of already released lock.")
            return inner!!.inner
        }

        private fun set(value: T) {
            if (inner == null) error("Tried to set inner value of already released lock.")
            inner!!.inner = value
        }

        suspend fun release() {
            if (inner == null) {
                Log.warn("Trying to release an already released lock!")
            }
            inner = null
            unlock()
        }

        protected class InnerDelegate<T> : ReadWriteProperty<RWLockGuard<T>, T> {
            override fun getValue(thisRef: RWLockGuard<T>, property: KProperty<*>): T =
                thisRef.get()

            override fun setValue(thisRef: RWLockGuard<T>, property: KProperty<*>, value: T) =
                thisRef.set(value)
        }
    }

    class RWLockReadGuard<T>
    private constructor(inner: Ref<T>, override val unlock: NonCapturingRWLock.Unlocker) :
        RWLockGuard<T>(inner) {
        val value by InnerDelegate()

        suspend fun <R> use(block: suspend ((RWLockReadGuard<T>) -> R)): R {
            val result = block(this)
            unlock()
            return result
        }

        companion object {
            suspend fun <T> new(inner: Ref<T>, locker: NonCapturingRWLock): RWLockReadGuard<T> =
                RWLockReadGuard(inner, locker.readLock())
        }
    }

    class RWLockWriteGuard<T>
    private constructor(inner: Ref<T>, override val unlock: NonCapturingRWLock.Unlocker) :
        RWLockGuard<T>(inner) {
        var value by InnerDelegate()

        suspend fun <R> use(block: suspend ((RWLockWriteGuard<T>) -> R)): R {
            val result = block(this)
            unlock()
            return result
        }

        companion object {
            suspend fun <T> new(inner: Ref<T>, locker: NonCapturingRWLock): RWLockWriteGuard<T> =
                RWLockWriteGuard(inner, locker.writeLock())
        }
    }

    sealed class RWLockTryGuard<T>
    private constructor(protected var inner: Ref<T>?, pleasePickPrimaryConstructor: Unit) {
        protected abstract val unlock: NonCapturingRWLock.TryUnlocker

        constructor(inner: Ref<T>) : this(inner, Unit)

        private fun get(): T {
            if (inner == null) error("Tried to read inner value of already released lock.")
            return inner!!.inner
        }

        private fun set(value: T) {
            if (inner == null) error("Tried to set inner value of already released lock.")
            inner!!.inner = value
        }

        fun release() {
            if (inner == null) {
                Log.warn("Trying to release an already released lock!")
            }
            inner = null
            unlock()
        }

        protected class InnerDelegate<T> : ReadWriteProperty<RWLockTryGuard<T>, T> {
            override fun getValue(thisRef: RWLockTryGuard<T>, property: KProperty<*>): T =
                thisRef.get()

            override fun setValue(thisRef: RWLockTryGuard<T>, property: KProperty<*>, value: T) =
                thisRef.set(value)
        }
    }

    class RWLockTryReadGuard<T>
    private constructor(inner: Ref<T>, override val unlock: NonCapturingRWLock.TryUnlocker) :
        RWLockTryGuard<T>(inner) {
        val value by InnerDelegate()

        fun <R> use(block: ((RWLockTryReadGuard<T>) -> R)): R {
            val result = block(this)
            unlock()
            return result
        }

        companion object {
            fun <T> new(inner: Ref<T>, locker: NonCapturingRWLock): RWLockTryReadGuard<T>? =
                locker.tryReadLock()?.let { RWLockTryReadGuard(inner, it) }
        }
    }

    class RWLockTryWriteGuard<T>
    private constructor(inner: Ref<T>, override val unlock: NonCapturingRWLock.TryUnlocker) :
        RWLockTryGuard<T>(inner) {
        var value by InnerDelegate()

        fun <R> use(block: ((RWLockTryWriteGuard<T>) -> R)): R {
            val result = block(this)
            unlock()
            return result
        }

        companion object {
            fun <T> new(inner: Ref<T>, locker: NonCapturingRWLock): RWLockTryWriteGuard<T>? =
                locker.tryWriteLock()?.let { RWLockTryWriteGuard(inner, it) }
        }
    }
}
