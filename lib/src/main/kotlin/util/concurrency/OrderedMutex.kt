package au.edu.unimelb.archivetool.util.concurrency

import kotlinx.coroutines.selects.SelectClause2
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore

// Internally using a Semaphore gives us a FIFO guarantee of Semaphore acquisition that we don't get
// with kotlinx Mutex
// To make implementation easier, we ignore the ownership stuff.
class OrderedMutex : Mutex {
    val semaphore = Semaphore(1)
    override val isLocked: Boolean by semaphore::hasPermits

    @Deprecated(
        "Mutex.onLock deprecated without replacement. For additional details please refer to #2794",
        level = DeprecationLevel.ERROR,
    )
    override val onLock: SelectClause2<Any?, Mutex>
        get() = error("No plans to implement.")

    override fun tryLock(owner: Any?): Boolean = semaphore.tryAcquire()

    override suspend fun lock(owner: Any?) = semaphore.acquire()

    override fun holdsLock(owner: Any): Boolean =
        error("OrderedMutex has no notion of named ownership.")

    override fun unlock(owner: Any?) = semaphore.release()
}
