package au.edu.unimelb.archivetool.util.extension

fun <K, V, T, U> Map<K, V>.mapEntries(mapper: ((Map.Entry<K, V>) -> Pair<T, U>)): Map<T, U> =
    map(mapper).toMap()
