package au.edu.unimelb.archivetool.util.extension

operator fun <T : Comparable<T>> ClosedRange<T>.component1() = start

operator fun <T : Comparable<T>> ClosedRange<T>.component2() = endInclusive

fun <T : Comparable<T>> Iterable<ClosedRange<T>>.tightestBounds() =
    fold(
            // This type-cast feels stupid, but kotlin defaults to guessing the type is
            // `Nothing?`
            null as Pair<T, T>?
        ) { acc, (start, end) ->
            var oldest = acc?.first ?: start
            var newest = acc?.second ?: end
            if (start < oldest) {
                oldest = start
            }
            if (newest == null || end > newest) {
                newest = end
            }
            oldest to newest
        }
        ?.let { it.first..it.second }

fun <T : Comparable<T>> Iterable<ClosedRange<T>>.tightestBoundsWithSentinel(
    sentinel: T,
    isSentinel: (T) -> Boolean,
) =
    fold(
            // This type-cast feels stupid, but kotlin defaults to guessing the type is
            // `Nothing?`
            null as Pair<T, T?>?
        ) { acc, (start, end) ->
            val end = end.takeUnless { isSentinel(end) }
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
        ?.let { it.first..(it.second ?: sentinel) }
