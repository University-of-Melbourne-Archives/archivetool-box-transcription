package au.edu.unimelb.archivetool.util.math

infix fun UInt.checkedDiv(denom: UInt): UInt? = denom.takeIf { it != 0u }?.let { this / it }

infix fun Double.checkedDiv(denom: Double): Double? = denom.takeIf { it != 0.0 }?.let { this / it }
