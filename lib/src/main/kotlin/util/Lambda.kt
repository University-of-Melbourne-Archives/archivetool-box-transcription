package au.edu.unimelb.archivetool.util

fun <T> noop(it: T): T = it

fun <A, R> (() -> R).withIgnoredParameter(): ((A) -> R) = { _ -> this() }

fun <A, B, R> (() -> R).withIgnoredParameters(): ((A, B) -> R) = { _, _ -> this() }

fun <A, B, C, R> (() -> R).with3IgnoredParameters(): ((A, B, C) -> R) = { _, _, _ -> this() }

fun <A, B, C, D, R> (() -> R).with4IgnoredParameters(): ((A, B, C, D) -> R) = { _, _, _, _ ->
    this()
}
