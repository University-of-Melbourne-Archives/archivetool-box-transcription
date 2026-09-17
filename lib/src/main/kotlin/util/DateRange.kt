package au.edu.unimelb.archivetool.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char

// The UoM CMS always stores this in the format "yyyy-yyyy;yyyy-MM-dd;yyy-MM-dd".
// If there is no end date, store as just "yyyy;;".
class DateRange(val startDate: LocalDate, val endDate: LocalDate?) {
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
    }
}
