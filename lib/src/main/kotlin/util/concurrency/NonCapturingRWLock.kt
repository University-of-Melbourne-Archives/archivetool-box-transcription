package au.edu.unimelb.archivetool.util.concurrency

import au.edu.unimelb.archivetool.logging.Log.expect
import kotlinx.coroutines.sync.Mutex

const val MAX_UNLOCK_ATTEMPTS = 1000

// Implementation inspired by the Writer-priority reader-writer problem
// in Allen B. Downey's wonderful Little Book of Semaphores:
// https://greenteapress.com/wp/semaphores/
// This file is available under the AGPL license of the larger work, or CC BY-NC-SA 4.0 at your
// option.
class NonCapturingRWLock {
    typealias Unlocker = suspend () -> Unit

    typealias TryUnlocker = (() -> Unit)

    val readSwitch = Lightswitch()
    val writeSwitch = Lightswitch()
    val noReaders = Mutex()
    val noWriters = OrderedMutex()

    suspend fun writeLock(): Unlocker {
        writeSwitch.lock(noReaders)
        noWriters.lock()
        return {
            noWriters.unlock()
            writeSwitch.unlock(noReaders)
        }
    }

    fun tryWriteLock(): TryUnlocker? {
        if (!writeSwitch.tryLock(noReaders)) return null
        if (!noWriters.tryLock()) {
            assert(writeSwitch.tryUnlock(noReaders))
            return null
        }
        return {
            noWriters.unlock()
            var unlockAttempts = 0
            while (!writeSwitch.tryUnlock(noReaders)) {
                (unlockAttempts++)
                    .takeIf { it < MAX_UNLOCK_ATTEMPTS }
                    .expect("Could not unlock non-suspending writer.")
            }
        }
    }

    suspend fun readLock(): Unlocker {
        noReaders.lock()
        readSwitch.lock(noWriters)
        noReaders.unlock()

        return { readSwitch.unlock(noWriters) }
    }

    fun tryReadLock(): TryUnlocker? {
        if (!noReaders.tryLock()) return null
        val gotSwitch = readSwitch.tryLock(noWriters)
        noReaders.unlock()
        if (!gotSwitch) return null
        return {
            var unlockAttempts = 0
            while (!readSwitch.tryUnlock(noWriters)) {
                (unlockAttempts++)
                    .takeIf { it < MAX_UNLOCK_ATTEMPTS }
                    .expect("Could not unlock non-suspending reader.")
            }
        }
    }
}
