package au.edu.unimelb.archivetool.util.math

import au.edu.unimelb.archivetool.util.error.expect

fun Double.lerp(v0: Double, v1: Double): Double =
    v0 + this.takeIf { it in 0.0..1.0 }.expect("Tried to lerp with out-of-range value.") * (v1 - v0)
