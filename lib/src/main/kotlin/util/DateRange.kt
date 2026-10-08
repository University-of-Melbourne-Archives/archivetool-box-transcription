package au.edu.unimelb.archivetool.util

import java.time.format.DateTimeParseException
import kotlin.collections.fold
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.serialization.Serializable

// The UoM CMS always stores this in the format "yyyy-yyyy;yyyy-MM-dd;yyy-MM-dd".
// If there is no end date, store as just "yyyy;;".
@Serializable
data class DateRange(val startDate: LocalDate, val endDate: LocalDate?) {
    val startYear by lazy { startDate.year }
    val endYear by lazy { endDate?.year }

    val yearRange by lazy { "$startYear${endYear?.let { "-$endYear" } ?: ""}" }

    val dateFormatter: DateTimeFormat<LocalDate> by lazy {
        LocalDate.Format {
            year(Padding.NONE)
            char('-')
            monthNumber()
            char('-')
            day()
        }
    }

    val startDateString by lazy { startDate.format(dateFormatter) }

    val endDateString by lazy { endDate?.format(dateFormatter) }

    val CSVString by lazy {
        yearRange + (endDate?.let { ";$startDateString;$endDateString" } ?: ";;")
    }

    companion object {
        fun scan(transcript: String): DateRange? {
            val dates = getAllDates(transcript)

            if (dates.isEmpty()) return null

            val last = if (dates.size > 1) dates.max() else null

            return DateRange(dates.min(), last)
        }

        // If we only have years, insert them as YYYY-01-01 or YYYY-12-31 depending on if start or
        // end.
        // Right now this doesn't do anything because this scanning is potentially a weird
        // algorithm.
        // We should also be aware that sometimes boxes feature numbers that look like dates, but
        // aren’t.
        private fun getAllDates(transcript: String): Set<LocalDate> {
            TODO("Do some pattern matching or something here.")
        }

        fun fromOCRoutput(dateRange: String): DateRange? {
            if (dateRange.contains("None")) return null
            val ranges = dateRange.split(", ")
            return ranges
                .map { it.split('!', limit = 1).let { it[0] to it.getOrNull(1) } }
                .map { (start, end) ->
                    val startDate =
                        try {
                            LocalDate.parse(start)
                        } catch (e: DateTimeParseException) {
                            LocalDate.parse("$start-01-01")
                        }
                    val endDate = end?.let {
                        try {
                            LocalDate.parse(end)
                        } catch (e: DateTimeParseException) {
                            LocalDate.parse("$end-12-31")
                        }
                    }

                    DateRange(startDate, endDate)
                }
                .tightestBounds()
        }

        fun Iterable<DateRange>.tightestBounds(): DateRange? =
            this.fold(
                    // This type-cast feels stupid, but kotlin defaults to guessing the type is
                    // `Nothing?`
                    null as Pair<LocalDate, LocalDate?>?
                ) { acc, (start, end) ->
                    var oldest = acc?.first ?: start
                    var newest = acc?.second ?: end
                    if (start < oldest) {
                        oldest = start
                    }
                    end?.let {
                        if (newest == null || end > newest) {
                            newest = end
                        }
                    }
                    oldest to newest
                }
                ?.let { DateRange(it.first, it.second) }
    }
}
