package au.edu.unimelb.archivetool.pythoninterop.extensions

import au.edu.unimelb.archivetool.pythoninterop.extensions.PolyglotIterator.Companion.asIterator
import au.edu.unimelb.archivetool.util.Ref
import io.nacular.doodle.controls.table.map
import org.graalvm.polyglot.Value
import kotlin.collections.mapKeys

@JvmInline
value class PolyglotMap(private val hash: Value): Map<Value, Value> {
    init {
        require(hash.hasHashEntries())
    }

    override val size: Int
        get() = hash.hashSize.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    override val keys: Set<Value>
        get() = hash.hashKeysIterator.asIterator().asSequence().toSet()
    override val values: Collection<Value>
        get() = hash.hashValuesIterator.asIterator().asSequence().toSet()
    override val entries: Set<Map.Entry<Value, Value>>
        get() = hash.hashEntriesIterator.asIterator().map {
            object : Map.Entry<Value, Value> {
                override val key
                    get() = it.getArrayElement(0)
                override val value
                    get() = it.getArrayElement(1)
            }
        }.asSequence().toSet()

    override fun isEmpty(): Boolean =
        size == 0

    override fun containsKey(key: Value): Boolean =
        hash.hasHashEntry(key)

    override fun containsValue(value: Value): Boolean =
        values.contains(value)

    override fun get(key: Value): Value? =
        hash.getHashValueOrDefault(key, null)

    companion object {
        fun Value.asMap() = PolyglotMap(this)
    }
}

operator fun Value.get(key: Value): Value = getHashValue(key)

fun <V> Map<Value, V>.withStringKeys(): Map<String, V> = mapKeys { (k, v) -> k.asString() }
