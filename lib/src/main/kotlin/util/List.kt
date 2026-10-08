package au.edu.unimelb.archivetool.util

fun <T> List<T>.counted(): List<Pair<Int, T>> {
    val l = this
    return l.dedup().map { item -> l.count { it == item } to item }
}

fun <T> List<T>.dedup(): List<T> {
    val items = mutableSetOf<T>()
    return filter { items.add(it) }
}
