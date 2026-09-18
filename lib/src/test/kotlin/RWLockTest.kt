/*
   Adapted from https://github.com/MayakaApps/KMP-RWMutex/blob/9ccbdad3bf4d1d1ab11a15cfeea0cce133c3e73f/rwmutex/src/commonTest/kotlin/com/mayakapps/rwmutex/ReadWriteMutexTest.kt
   Originally licensed under Apache 2.0.

   Original Notice:
    * Copyright 2024 MayakaApps
    *
    * Licensed under the Apache License, Version 2.0 (the "License");
    * you may not use this file except in compliance with the License.
    * You may obtain a copy of the License at
    *
    *     http://www.apache.org/licenses/LICENSE-2.0
    *
    * Unless required by applicable law or agreed to in writing, software
    * distributed under the License is distributed on an "AS IS" BASIS,
    * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    * See the License for the specific language governing permissions and
    * limitations under the License.
*/

import au.edu.unimelb.archivetool.util.concurrency.NonCapturingRWLock
import au.edu.unimelb.archivetool.util.concurrency.RWLock
import java.lang.Thread.sleep
import java.util.concurrent.atomic.AtomicInteger as AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

class ReadWriteLockTest {

    @Test
    fun parallelReaders() {
        testParallelReaders(1, 4)
        testParallelReaders(3, 4)
        testParallelReaders(4, 2)
    }

    private fun testParallelReaders(readerCount: Int, threadCount: Int) = runTest {
        val dispatcher = Dispatchers.Default
        val rwLock = NonCapturingRWLock()

        // Create semaphores to synchronize readers that have no available permits by default
        val readLocked = Semaphore(readerCount, readerCount)
        val unlockRead = Semaphore(readerCount, readerCount)

        // Start readers in parallel
        val readJobs =
            List(readerCount) {
                launch(dispatcher) {
                    val unlock = rwLock.readLock()
                    readLocked.release()
                    sleep(500)
                    unlockRead.acquire()
                    unlock()
                }
            }

        // Wait for all readers to acquire the read lock
        repeat(readerCount) { readLocked.acquire() }

        // Release the readers
        repeat(readerCount) { unlockRead.release() }

        // Wait for all readers to finish
        readJobs.joinAll()
    }

    @Test
    fun tryLock() = runTest {
        RWLock(Unit).run {
            launch {
                withWriteLock {
                    assertNull(tryWriteLock())
                    assertNull(tryReadLock())
                }

                val guard = tryWriteLock()
                assertNotNull(guard)
                guard.release()

                val readGuard1 = tryReadLock()
                val readGuard2 = tryReadLock()
                assertNotNull(readGuard1)
                assertNotNull(readGuard2)
                assertNull(tryWriteLock())

                readGuard1.release()
                readGuard2.release()
            }
        }
    }

    @Test
    fun stressReadWriteLock() = runTest {
        stressReadWriteLock(1, 1)
        stressReadWriteLock(1, 3)
        stressReadWriteLock(1, 10)
        stressReadWriteLock(4, 1)
        stressReadWriteLock(4, 3)
        stressReadWriteLock(4, 10)
        stressReadWriteLock(10, 1)
        stressReadWriteLock(10, 3)
        stressReadWriteLock(10, 10)
        stressReadWriteLock(10, 5)
    }

    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun stressReadWriteLock(readerCount: Int, threadCount: Int) {
        val dispatcher = Dispatchers.Default
        val rwLock = RWLock(Unit)

        val jobs = mutableListOf<Job>()
        val activity = AtomicInt(0) // number of active readers + 10000 * number of active writers

        withContext(dispatcher) {
            // Start a single writer with half the readers
            jobs += launch { stressWrite(rwLock, activity) }
            jobs += List(readerCount / 2) { launch { stressRead(rwLock, activity) } }

            // Start a new writer with the remaining readers (the +1 is to account for odd reader
            // counts)
            jobs += launch { stressWrite(rwLock, activity) }
            jobs += List((readerCount + 1) / 2) { launch { stressRead(rwLock, activity) } }
        }

        // Wait for the 2 writers and all readers to finish
        jobs.joinAll()
    }

    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun stressRead(rwLock: RWLock<Unit>, activity: AtomicInt) {
        repeat(STRESS_ITERATIONS) {
            val unlock = rwLock.readLock()::release
            val newActivity = activity.incrementAndGet()
            if (newActivity !in 1 until 10000) {
                unlock()
                throw IllegalStateException(
                    "Invalid activity value: $newActivity, maybe a read mutex locked while a writer is active"
                )
            }
            repeat(100) {} // Simulate some work
            activity.decrementAndGet()
            unlock()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun stressWrite(rwLock: RWLock<Unit>, activity: AtomicInt) {
        repeat(STRESS_ITERATIONS) {
            val guard = rwLock.writeLock()
            val newActivity = activity.addAndGet(10000)
            if (newActivity != 10000) {
                guard.release()
                throw IllegalStateException(
                    "Invalid activity value: $newActivity, maybe a writer locked while another reader or writer is active"
                )
            }
            repeat(100) {} // Simulate some work
            activity.addAndGet(-10000)
            guard.release()
        }
    }

    companion object {
        const val STRESS_ITERATIONS = 10000
    }
}
