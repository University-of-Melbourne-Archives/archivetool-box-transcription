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

        // Validates a single string with the only valid format being
        // "yyyy-yyyy;yyyy-MM-dd;yyyy-MM-dd" or "yyyy;;".
        // Throws IllegalArgumentException with a corresponding user message on fail.
        fun fromUserInput(input: String): DateRange {
            val textParts = input.trim().split(';').map { it.trim() }

            require(textParts.size == 3) {
                "Expected \"yyyy-yyyy;yyyy-MM-dd;yyyy-MM-dd\", or \"yyyy;;\" if there is no end date."
            }

            val (years, start, end) = textParts

            // Check if year or year range exists and is formatted correctly
            require(Regex("^\\d{4}(?:-\\d{4})?$").matches(years)) {
                "\"$years\" is not a valid year or year range (expected yyyy or yyyy-yyyy)."
            }

            if (start.isEmpty() && end.isEmpty()) {
                require('-' !in years) {
                    "A year range needs a start and end date. Use just \"yyyy;;\" if there is no end date."
                }

                return DateRange(LocalDate(years.toInt(), 1, 1), null)
            }

            require(start.isNotEmpty() && end.isNotEmpty()) {
                "Provide both a start and an end date, or neither (\"yyyy;;\")."
            }

            val startDate = parseUserDate(start, "Start")
            val endDate = parseUserDate(end, "End")
            // Impossible year range
            require(endDate >= startDate) { "The end date is before the start date." }

            val range = DateRange(startDate, endDate)
            // Inconsistent year range
            require(range.yearRange == years) {
                "Year range \"$years\" does not match the dates (expected \"${range.yearRange}\")."
            }

            return range
        }

        private fun parseUserDate(text: String, label: String): LocalDate {
            val match =
                Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").matchEntire(text)
                    ?: throw IllegalArgumentException(
                        "$label date \"$text\" must look like yyyy-MM-dd."
                    )

            val (year, month, day) = match.destructured

            return try {
                LocalDate(year.toInt(), month.toInt(), day.toInt())
            } catch (e: IllegalArgumentException) {
                // If calendar date does not exist
                throw IllegalArgumentException("$label date \"$text\" is not a real calendar date.")
            }
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
