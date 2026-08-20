package au.edu.unimelb.archivetool.app.cli.helpers

import au.edu.unimelb.archivetool.logging.LogLevel
import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.transformAll
import com.github.ajalt.clikt.parameters.options.transformValues
import com.github.ajalt.clikt.parameters.types.int

// Manually unrolled version of RawOption.counted(limit=2, clamp=true), that also converts the resultant value.
fun <T> RawOption.countedTransform(limit: Int = Int.MAX_VALUE, clamp: Boolean = true, mapper: (Int) -> T): OptionWithValues<T, Int, Int>{
    return int().transformValues(0..0) { it.lastOrNull() ?: 1 }.transformAll {
        val n = it.sum();
        if (!clamp && n > limit) {
            fail(context.localization.countedOptionExceededLimit(n, limit))
        }
        n.coerceAtMost(limit)
        mapper(n)
    }
}