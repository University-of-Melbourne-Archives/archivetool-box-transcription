package au.edu.unimelb.archivetool.util.extension

import kotlin.Pair

object PairExt {
    fun <A, B, X, Y> Pair<A, B>.map(left: (A) -> X, right: (B) -> Y): Pair<X, Y> =
        Pair(left(this.first), right(this.second))

    fun <A, X> Pair<A, A>.map(mapper: (A) -> X): Pair<X, X> = this.map(mapper, mapper)

    fun <A> Pair<A, A>.any(predicate: (A) -> Boolean): Boolean =
        predicate(first) || predicate(second)
}
