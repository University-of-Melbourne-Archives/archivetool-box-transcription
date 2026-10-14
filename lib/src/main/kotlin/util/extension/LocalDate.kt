package au.edu.unimelb.archivetool.util.extension

import kotlin.time.Instant
import kotlin.time.isDistantFuture
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

val LocalDate.Companion.DISTANT_FUTURE by lazy {
    Instant.DISTANT_FUTURE.toLocalDateTime(TimeZone.currentSystemDefault()).date
}

val LocalDate.isDistantFuture
    get() =
        this.atTime(LocalTime(23, 59, 59))
            .toInstant(TimeZone.currentSystemDefault())
            .isDistantFuture
