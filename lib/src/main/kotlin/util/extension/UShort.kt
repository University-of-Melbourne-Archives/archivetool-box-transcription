package au.edu.unimelb.archivetool.util.extension

infix fun UShort.shr(amount: Int): UShort = (toUInt() shr amount).toUShort()

infix fun UShort.shl(amount: Int): UShort = (toUInt() shl amount).toUShort()
