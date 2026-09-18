package au.edu.unimelb.archivetool.switchboard.jobstate

import java.nio.file.Path
import kotlin.io.path.absolute

class JobRelativePath(val rootDirectory: Path, private val rel: Path) {
    init {
        require(rootDirectory.isAbsolute)
        require(!rel.isAbsolute)
    }

    val relative by this::rel
    val absolute by lazy { rootDirectory.resolve(rel).absolute() }

    override fun toString(): String {
        return "JobRelativePath($absolute: relative to $rootDirectory)"
    }
}
