package au.edu.unimelb.archivetool.util

// Removes from the front of the queue until
fun <T> ArrayDeque<T>.removeWhile(predicate: (T) -> Boolean): Sequence<T> = sequence {
    while (isNotEmpty() && predicate(first())) {
        yield(removeFirst())
    }
}
