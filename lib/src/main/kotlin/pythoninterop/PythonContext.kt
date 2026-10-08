package au.edu.unimelb.archivetool.pythoninterop

import au.edu.unimelb.archivetool.logging.Log
import java.lang.ref.WeakReference
import kotlin.getValue
import org.graalvm.polyglot.Context
import org.graalvm.python.embedding.GraalPyResources
import org.graalvm.python.embedding.VirtualFileSystem

sealed class PythonContext(inject: Set<Injectable> = setOf()) {
    private val vfs by lazy {
        VirtualFileSystem.newBuilder().allowHostIO(VirtualFileSystem.HostIO.READ).build()
    }

    protected val _context: Context by lazy {
        GraalPyResources.contextBuilder(vfs).allowAllAccess(true).build().apply {
            inject.forEach {
                Log.debug("Injecting injectable: $inject")
                it.inject(this)
            }
            getBindings("python").putMember("KOTLIN", true)
        }
    }

    val context
        get() = WeakReference(_context)
}
