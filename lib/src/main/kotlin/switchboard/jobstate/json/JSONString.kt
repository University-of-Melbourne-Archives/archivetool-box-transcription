package au.edu.unimelb.archivetool.switchboard.jobstate.json

import com.sun.jdi.InvalidTypeException
import java.util.Optional
import kotlin.jvm.optionals.getOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

// Represents a string with the invariant that we know it is valid JSON.
// More of an experiment on this type of light wrapper than something unbelievably practical.
@JvmInline
value class JSONString private constructor(val inner: String) : CharSequence {
    // Call inner with an intentionally invalid JSON string if empty.
    // We can only exist in a bad state until the constructor terminates.
    // This also has optional so the function signiatures don't clash.
    constructor(json: Optional<String>) : this(json.getOrNull() ?: "}") {
        if (!verify()) throw InvalidTypeException()
    }

    fun verify(): Boolean {
        try {
            toJsonElement()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun toJsonElement(): JsonElement {
        return Json.parseToJsonElement(inner)
    }

    override val length: Int
        get() = inner.length

    override fun get(index: Int): Char = inner[index]

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        inner.subSequence(startIndex, endIndex)

    companion object {
        fun JsonElement.toStrictString(): JSONString {
            return JSONString(Json.encodeToString(this))
        }
    }
}
