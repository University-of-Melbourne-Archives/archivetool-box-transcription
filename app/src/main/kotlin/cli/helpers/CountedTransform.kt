package au.edu.unimelb.archivetool.app.cli.helpers

import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.transformAll
import com.github.ajalt.clikt.parameters.options.transformValues
import com.github.ajalt.clikt.parameters.types.int

/** Map repeated short options (e.g. -vv) to a capped count and then to a value. */
fun <T> RawOption.countedTransform(
    limit: Int = Int.MAX_VALUE,
    clamp: Boolean = true,
    mapper: (Int) -> T,
): OptionWithValues<T, Int, Int> =
    int()
        .transformValues(0..0) { it.lastOrNull() ?: 1 }
        .transformAll {
            val count = it.sum()
            if (!clamp && count > limit) {
                fail(context.localization.countedOptionExceededLimit(count, limit))
            }
            mapper(count.coerceAtMost(limit))
        }
