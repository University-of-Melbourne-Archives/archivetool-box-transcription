// Required for UByteArray
@file:OptIn(ExperimentalUnsignedTypes::class)

package au.edu.unimelb.archivetool.util.extension

fun UByteArray.toUInt(): UInt {
    assert(size == 4)
    return ((this[0].toUInt() and 0xFFu) shl 24) or
        ((this[1].toUInt() and 0xFFu) shl 16) or
        ((this[2].toUInt() and 0xFFu) shl 8) or
        (this[3].toUInt() and 0xFFu)
}

fun uBytesToUInt(vararg bytes: UByte): UInt = bytes.toUInt()

fun UByteArray.toUShort(): UShort {
    assert(size == 2)
    return ((this[0].toUShort() and 0xFFu) shl 8) or (this[1].toUShort() and 0xFFu)
}

fun uBytesToUShort(vararg bytes: UByte): UShort = bytes.toUShort()
