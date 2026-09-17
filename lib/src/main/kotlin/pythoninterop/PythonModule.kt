package au.edu.unimelb.archivetool.pythoninterop

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.Log.expect
import java.io.BufferedReader
import java.lang.ref.WeakReference
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.io.path.toPath
import kotlin.io.path.walk
import kotlin.reflect.KProperty
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

// A Python context where the code is treated as a "module".
// The code is evaluated at load-time, and should ideally have minimal side-effects
// (ie. everything should sit in a function and not execute until called)
// *Most* functionality should exist within one of these,
// ideally wrapped by a Java class that handles input/output conversion.
class PythonModule(filePaths: Set<String>, inject: Set<Injectable> = setOf()) :
    PythonContext(inject) {
    constructor(
        folder: String,
        inject: Set<Injectable> = setOf(),
    ) : this(
        {}.javaClass
            .getResource("/python/$folder")
            ?.toURI()
            .expect("Python ModuleFolder $folder not found")
            .let { it.toPath() to it }
            .let {
                val path = it.first
                val uri = it.second
                when (uri.scheme) {
                    "jar" -> {
                        FileSystems.getFileSystem(uri).use { jarFs ->
                            val dirPath: Path = jarFs.getPath("/python/$folder")
                            dirPath
                                .walk()
                                .map {
                                    it.takeIf { it.exists() && it.isRegularFile() }
                                        ?.relativeTo(jarFs.getPath("/python"))
                                }
                                .filter { it?.extension == "py" }
                                .map { it.toString() }
                                .toSet()
                        }
                    }
                    else -> {
                        path
                            .walk()
                            .map {
                                it.takeIf { it.exists() && it.isRegularFile() }
                                    ?.relativeTo(
                                        {}.javaClass.getResource("/python")?.toURI()?.toPath()!!
                                    )
                            }
                            .filter { it?.extension == "py" }
                            .map { it.toString() }
                            .toSet()
                    }
                }
            },
        inject,
    )

    init {
        Log.debug("Initialising Python Module")

        _context.enter()
        filePaths
            .map {
                Log.debug("\tLoading Module File $it")
                BufferedReader({}.javaClass.getResourceAsStream("/python/$it")!!.reader())
                    .readText()
            }
            .forEach {
                Log.trace("\tRunning Python Module Code:\n\"\"\"\n$it\n\"\"\"")
                _context.eval("python", it)
            }
        _context.leave()
    }

    private val allMembers: MutableMap<String, Value> by lazy { mutableMapOf() }
    val members by lazy { MemberSet(WeakReference(allMembers), _context) }
    val methods by lazy { members.withFilter({ it.canExecute() }) }
}

data class MemberSet(
    private val members: WeakReference<MutableMap<String, Value>>,
    private val ctx: Context,
    private val filters: MutableSet<(Value) -> Boolean> = mutableSetOf(),
) {
    private constructor(orig: MemberSet) : this(orig.members, orig.ctx, orig.filters.toMutableSet())

    internal val filteredMembers
        get() = members.get()!!.toMap().filter { member -> filters.all { it(member.value) } }

    operator fun get(index: String): Value? {
        if (filteredMembers.containsKey(index)) return filteredMembers[index]

        return members.get()?.let { members ->
            if (members.containsKey(index)) return null

            val out = ctx.getBindings("python").getMember(index)

            members[index] = out

            out.takeIf { v -> filters.all { it(v) } }
        }
    }

    operator fun <OwnerT> getValue(thisRef: OwnerT, property: KProperty<*>): Value {
        return get(property.name)!!
    }

    fun withFilter(vararg filt: (Value) -> Boolean): MemberSet =
        MemberSet(this).apply { filters.addAll(filt) }
}
