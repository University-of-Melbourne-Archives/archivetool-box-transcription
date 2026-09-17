package au.edu.unimelb.archivetool.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

const val RWLock_READERS_MAX = Int.MAX_VALUE

class RWLock<T, LockIDType : Any>(private var inner: T, val maxReaders: Int = RWLock_READERS_MAX) {
    private val readers = mutableSetOf<LockRequest.Reader<LockIDType>>()
    private val writer = Mutex()

    private val requestQueue = ArrayDeque<LockRequest<LockIDType>>()

    private val queueAdmittance = MutableSharedFlow<LockRequest<LockIDType>>()

    suspend fun read(lockId: LockIDType): RWLockReadGuard<T, LockIDType> {
        suspend fun actuallyLock(): RWLockReadGuard<T, LockIDType> {
            if (!writer.isLocked) writer.lock(MutexHolder.Readers)
            if (readers.add(LockRequest.Reader(lockId))) error("Lock Already Held by this ID")
            return RWLockReadGuard(Box(inner), lockId, this::release)
        }
        if (
            (!writer.isLocked || writer.holdsLock(MutexHolder.Readers)) && readers.size < maxReaders
        ) {
            return actuallyLock()
        }
        val req = LockRequest.Reader(lockId)
        assert(!requestQueue.contains(req))
        requestQueue.addLast(req)
        queueAdmittance.first { it == req } // This waits until it's emitted.
        return actuallyLock()
    }

    suspend fun write(lockId: LockIDType): RWLockWriteGuard<T, LockIDType> {
        suspend fun actuallyLock(): RWLockWriteGuard<T, LockIDType> {
            writer.lock(MutexHolder.Writer(lockId))
            return RWLockWriteGuard(Box(inner), lockId, this::release)
        }
        if (writer.isLocked) {
            return actuallyLock()
        }
        val req = LockRequest.Reader(lockId)
        assert(!requestQueue.contains(req))
        requestQueue.addLast(req)
        queueAdmittance.first { it == req } // This waits until it's emitted.
        return actuallyLock()
    }

    private fun release(guard: RWLockGuard<T, LockIDType>) {
        when (guard) {
            is RWLockReadGuard<T, LockIDType> -> {
                readers.remove(LockRequest.Reader(guard.lockID))
            }
            is RWLockWriteGuard<T, LockIDType> -> {
                writer.unlock(MutexHolder.Writer(guard.lockID))
            }
        }

        if (writer.holdsLock(MutexHolder.Readers)) {
            if (readers.isEmpty()) writer.unlock(MutexHolder.Readers) else admitNewReaders()
        }

        if (!writer.isLocked) {
            when (requestQueue.firstOrNull()) {
                is LockRequest.Reader<LockIDType> -> {
                    admitNewReaders()
                }
                is LockRequest.Writer<LockIDType> -> {
                    assert(queueAdmittance.tryEmit(requestQueue.removeFirst()))
                }
                null -> {}
            }
        }
    }

    private fun admitNewReaders() =
        requestQueue
            .removeWhile { it is LockRequest.Reader<LockIDType> }
            .take(maxReaders - readers.size)
            .forEach { assert(queueAdmittance.tryEmit(it)) }

    sealed class RWLockGuard<T, LockIDType>(
        protected var inner: Box<T>?,
        val lockID: LockIDType,
        protected val onRelease: (RWLockGuard<T, LockIDType>) -> Unit,
        pleasePickPrimaryConstructor: Unit,
    ) : AutoCloseable {
        constructor(
            inner: Box<T>,
            lockID: LockIDType,
            onRelease: (RWLockGuard<T, LockIDType>) -> Unit,
        ) : this(inner, lockID, onRelease, Unit)

        // Emulate "Dereference". Gets the inner value, or panics if it has already been released.
        operator fun unaryPlus(): T = get()

        fun get(): T {
            if (inner == null) error("Tried to read inner value of already released lock.")
            return inner!!.inner
        }

        fun set(value: T) {
            if (inner == null) error("Tried to set inner value of already released lock.")
            inner!!.inner = value
        }

        fun release() {
            inner = null
            onRelease(this)
        }

        override fun close() {
            release()
        }

        protected class InnerDelegate<T, LockIDType> :
            ReadWriteProperty<RWLockGuard<T, LockIDType>, T> {
            override fun getValue(thisRef: RWLockGuard<T, LockIDType>, property: KProperty<*>): T =
                thisRef.get()

            override fun setValue(
                thisRef: RWLockGuard<T, LockIDType>,
                property: KProperty<*>,
                value: T,
            ) = thisRef.set(value)
        }
    }

    class RWLockReadGuard<T, LockIDType>(
        inner: Box<T>,
        lockID: LockIDType,
        onRelease: (RWLockGuard<T, LockIDType>) -> Unit,
    ) : RWLockGuard<T, LockIDType>(inner, lockID, onRelease) {
        val value by InnerDelegate()
    }

    class RWLockWriteGuard<T, LockIDType>(
        inner: Box<T>,
        lockID: LockIDType,
        onRelease: (RWLockGuard<T, LockIDType>) -> Unit,
    ) : RWLockGuard<T, LockIDType>(inner, lockID, onRelease) {
        var value by InnerDelegate()
    }

    sealed interface MutexHolder {
        object Readers : MutexHolder

        class Writer<LockIDType>(id: LockIDType) : MutexHolder
    }

    sealed interface LockRequest<LockIDType : Any> {
        class Reader<LockIDType : Any>(val id: LockIDType) : LockRequest<LockIDType> {
            override fun equals(other: Any?): Boolean =
                other is Reader<LockIDType> && other.id == id

            override fun hashCode(): Int {
                return id.hashCode()
            }
        }

        class Writer<LockIDType : Any>(val id: LockIDType) : LockRequest<LockIDType> {
            override fun equals(other: Any?): Boolean =
                other is Writer<LockIDType> && other.id == id

            override fun hashCode(): Int {
                return id.hashCode()
            }
        }
    }
}
