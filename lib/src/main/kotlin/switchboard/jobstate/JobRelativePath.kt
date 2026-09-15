package au.edu.unimelb.archivetool.switchboard.jobstate

import java.nio.file.Path
import kotlin.io.path.absolute

class JobRelativePath(private val rootDir: Path, private val rel: Path) {
    init {
        require(rootDir.isAbsolute)
        require(!rel.isAbsolute)
    }

    val relative by this::rel
    val absolute by lazy { rootDir.resolve(rel).absolute() }
}
