package au.edu.unimelb.archivetool.util.concurrency

import kotlinx.coroutines.sync.Semaphore

val Semaphore.hasPermits get() = availablePermits > 0