import au.edu.unimelb.archivetool.pythoninterop.Injectable
import au.edu.unimelb.archivetool.pythoninterop.PythonModule

class PythonTestHarness(filePaths: Set<String>, inject: Set<Injectable> = setOf()) {
    constructor(
        folder: String,
        inject: Set<Injectable> = setOf(),
    ) : this(PythonModule.resolveModuleFolder(folder), inject)

    val module by lazy { PythonModule(filePaths, inject) }
}
