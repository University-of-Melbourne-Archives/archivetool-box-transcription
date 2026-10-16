package au.edu.unimelb.archivetool.python.interop.extensions

import org.graalvm.polyglot.Value

@JvmInline
value class PolyglotIterator(private val iterator: Value) : Iterator<Value> {
    init {
        require(iterator.isIterator)
    }

    override fun next(): Value = iterator.iteratorNextElement

    override fun hasNext(): Boolean = iterator.hasIteratorNextElement()

    companion object {
        fun Value.asIterator() = PolyglotIterator(this)
    }
}

@JvmInline
value class PolyglotIterable(private val iterator: Value) : Iterable<Value> {
    init {
        require(iterator.isIterator)
    }

    override fun iterator(): Iterator<Value> = PolyglotIterator(this.iterator)

    companion object {
        fun Value.asIterable() = PolyglotIterable(this)
    }
}
