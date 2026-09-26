package au.edu.unimelb.archivetool.util

import kotlin.Triple

fun <A, B, C, X, Y, Z> Triple<A, B, C>.map(left: (A) -> X, middle: (B) -> Y, right: (C) -> Z): Triple<X, Y, Z> =
    Triple(left(this.first), middle(this.second), right(this.third))

fun <A, X> Triple<A, A, A>.map(mapper: (A) -> X): Triple<X, X, X> =
    this.map(mapper, mapper, mapper)
