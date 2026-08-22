package au.edu.unimelb.archivetool.pythoninterop

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.util.unwrap
import au.edu.unimelb.archivetool.util.validate
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import java.io.BufferedReader
import java.lang.ref.WeakReference
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.toPath
import kotlin.io.path.walk
import kotlin.properties.Delegates


// A Python context where the code is treated as a "module".
// The code is evaluated at load-time, and should ideally have minimal side-effects
// (ie. everything should sit in a function and not execute until called)
// *Most* functionality should exist within one of these,
// ideally wrapped by a Java class that handles input/output conversion.
class PythonModule(filePaths: Set<String>, inject: Set<Injectable> = setOf()) : PythonContext(inject) {
    constructor(folder: String, inject: Set<Injectable> = setOf()): this(
        {}.javaClass.getResource("/python/$folder")
            ?.toURI()
            .unwrap { Log.error("Python ModuleFolder $folder not found"); throw RuntimeException() }
            .let {
                it.toPath() to it
            }
            .let {
                val path = it.first
                val uri = it.second
                when (uri.scheme) {
                    "jar" -> {
                        FileSystems.getFileSystem(uri).use { jarFs ->
                            val dirPath: Path = jarFs.getPath("/python/$folder")
                            dirPath.walk().map {
                                it.validate { it.exists() && it.isRegularFile() }
                                    ?.relativeTo(
                                        jarFs.getPath("/python")
                                    )
                            }.filter { it?.extension == "py" }
                                .map {it.toString()}.toSet()
                        }
                    }
                    else -> {
                        path.walk().map {
                            it.validate { it.exists() && it.isRegularFile() }
                                ?.relativeTo(
                                    {}.javaClass.getResource("/python")
                                    ?.toURI()?.toPath()!!
                                )
                        }.filter { it?.extension == "py" }
                            .map { it.toString() }.toSet()
                    }
                }
            },
        inject
    )

    init {
        Log.debug("Initialising Python Module")

        filePaths.map {
            Log.debug("\tLoading Module File $it")
            BufferedReader(
                {}.javaClass
                    .getResourceAsStream("/python/$it")!!
                    .reader()
            ).readText()
        }.forEach {
            Log.trace("\tRunning Python Module Code:\n\"\"\"\n$it\n\"\"\"")
            context.eval("python", it)
        }
    }

    private val allMembers: MutableMap<String, Value> by lazy { mutableMapOf() }
    val members by lazy { MemberSet(WeakReference(allMembers), context) }
    val methods by lazy { members.withFilter({it.canExecute()}) }
}

data class MemberSet(val members: WeakReference<MutableMap<String, Value>>, private val ctx: Context, val filters: MutableSet<(Value)->Boolean> = mutableSetOf()) {
    private constructor(orig: MemberSet) : this(orig.members, orig.ctx, orig.filters.toMutableSet())

    internal val filteredMembers
        get() = members.get()!!.toMap().filter { member -> filters.all {it(member.value)} }

    @OptIn(ExperimentalStdlibApi::class)
    operator fun get(index: String): Value? {
        if (filteredMembers.containsKey(index)) return filteredMembers[index]

        return members.get()?.let { members ->
            if (members.containsKey(index)) return null

            val out = ctx.getBindings("python").getMember(index)

            members[index] = out

            out.validate { v -> filters.all { it(v) } }
        }
    }

    fun withFilter(vararg filt: (Value) -> Boolean): MemberSet =
        MemberSet(this).apply { filters.addAll(filt) }
}