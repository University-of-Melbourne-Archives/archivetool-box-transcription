package au.edu.unimelb.archivetool.python.interop.extensions

import au.edu.unimelb.archivetool.python.interop.extensions.get
import org.graalvm.polyglot.Value

operator fun Value.get(index: Long): Value = getArrayElement(index)

operator fun Value.get(index: Int): Value = this[index.toLong()]
