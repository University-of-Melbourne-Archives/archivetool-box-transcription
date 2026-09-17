package au.edu.unimelb.archivetool.util

import kotlin.Pair

fun <A, B, X, Y> Pair<A, B>.map(left: (A) -> X, right: (B) -> Y): Pair<X, Y> {
    return Pair(left(this.first), right(this.second))
}
