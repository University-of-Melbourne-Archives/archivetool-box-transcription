package au.edu.unimelb.archivetool.app.cli.helpers

import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.transformAll
import com.github.ajalt.clikt.parameters.options.transformValues
import com.github.ajalt.clikt.parameters.types.int

// Count how many times an option appears,
// limit the count, and convert it into another value.
fun <T> RawOption.countedTransform(
    // Maximum count to accept.
    limit: Int = Int.MAX_VALUE,

    // If true, excessive repetitions are reduced to the limit.
    // If false, excessive repetitions produce an error.
    clamp: Boolean = true,

    // Converts the final count into the required result.
    mapper: (Int) -> T,
): OptionWithValues<T, Int, Int> {

    return int()

        // Treat each option occurrence as a count of one.
        .transformValues(0..0) { it.lastOrNull() ?: 1 }

        // Add all occurrences together.
        .transformAll {
            val n = it.sum()

            // Reject excessive repetitions when clamping is disabled.
            if (!clamp && n > limit) {
                fail(context.localization.countedOptionExceededLimit(n, limit))
            }

            // Limit the count and convert it into the final value.
            n.coerceAtMost(limit)
            mapper(n)
        }
}
