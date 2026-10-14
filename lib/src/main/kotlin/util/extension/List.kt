package au.edu.unimelb.archivetool.util.extension

fun <T> List<T>.counted(): List<Pair<Int, T>> {
    val l = this
    return l.dedup().map { item -> l.count { it == item } to item }
}

fun <T> List<T>.dedup(): List<T> {
    val items = mutableSetOf<T>()
    return filter { items.add(it) }
}

fun <T : Any> Iterable<T?>.propagateNulls(): List<T>? =
    takeUnless { l -> l.any { it.isNull() } }?.map { it!! }

fun <T : Any> Iterable<T?>.propagateNullsRecursive(): Iterable<T>? =
    // If we have a non-null in our list and our list is of iterables.
    propagateNulls().applyIf({ it.isNotNull() && it!!.first() is Iterable<Any?> }) {
        map { ((it as Iterable<Any?>).propagateNullsRecursive()) as? T? }.propagateNulls()
    }
