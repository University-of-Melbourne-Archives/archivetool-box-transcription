package au.edu.unimelb.archivetool.util.concurrency

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Implementation inspired by Allen B. Downey's wonderful Little Book of Semaphores:
//                                          https://greenteapress.com/wp/semaphores/
// This file is available under the AGPL license of the larger work, or CC BY-NC-SA 4.0 at your
// option.
class Lightswitch() {
    private val counter: AtomicInteger = AtomicInteger(0)
    private val mutex: Mutex = Mutex()

    fun tryLock(otherMutex: Mutex): Boolean {
        if (!mutex.tryLock()) return false
        val counterSnapshot = counter.incrementAndGet()
        if (counterSnapshot == 1) {
            if (!otherMutex.tryLock()) {
                // roll back: we incremented but couldn't get `other`
                counter.decrementAndGet()
                mutex.unlock()
                return false
            }
        }
        mutex.unlock()
        return true
    }

    suspend fun lock(otherMutex: Mutex) {
        mutex.withLock {
            val counterSnapshot = counter.incrementAndGet()
            if (counterSnapshot == 1) {
                otherMutex.lock()
            }
        }
    }

    fun tryUnlock(otherMutex: Mutex): Boolean {
        if (!mutex.tryLock()) return false
        val counterSnapshot = counter.decrementAndGet()
        if (counterSnapshot == 0) {
            otherMutex.unlock()
        }
        mutex.unlock()
        return true
    }

    suspend fun unlock(otherMutex: Mutex) {
        mutex.withLock {
            val counterSnapshot = counter.decrementAndGet()
            if (counterSnapshot == 0) {
                otherMutex.unlock()
            }
        }
    }
}
