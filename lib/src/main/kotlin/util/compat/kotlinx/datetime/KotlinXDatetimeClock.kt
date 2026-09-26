package kotlinx.datetime

import kotlin.time.Clock as KtClock
import kotlin.time.Instant as KtInstant

// Shim: kotlinx.datetime.Instant and kotlinx.datetime.Clock
// no longer exist as their own class in 0.8.0
// (they're now typealiases for their kotlin.time counterparts),
// but doodle's bytecode expects distinct types.


class Instant internal constructor(internal val delegate: KtInstant) {
    fun toEpochMilliseconds(): Long = delegate.toEpochMilliseconds()
    override fun toString(): String = delegate.toString()
    override fun equals(other: Any?): Boolean = other is Instant && delegate == other.delegate
    override fun hashCode(): Int = delegate.hashCode()
    // TODO: add any other members doodle actually calls
}

interface Clock {
    fun now(): Instant

    object System : Clock {
        override fun now(): Instant = Instant(KtClock.System.now())
    }
}