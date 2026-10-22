package au.edu.unimelb.archivetool.util.os

import au.edu.unimelb.archivetool.util.error.expect
import au.edu.unimelb.archivetool.util.extension.takeIfNotNull
import au.edu.unimelb.archivetool.util.extension.tryOrIgnore
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable
import kotlin.io.path.pathString

sealed class PlatformImpl {
    internal abstract fun configBasePath(): Path

    fun configPath(): Path =
        configBasePath().resolve("archivetool").tryOrIgnore<
            Path,
            java.nio.file.FileAlreadyExistsException,
        > {
            createDirectory()
        }
}

fun validConfigPath(it: Path) =
    it.exists() && it.isDirectory() && it.isReadable() && it.isWritable()

fun tryDirectoryString(path: String?): Path? = path?.let { Path(path).takeIf(::validConfigPath) }

object Linux : PlatformImpl() {
    // Linux uses XDG_CONFIG_HOME or ~/.config
    override fun configBasePath(): Path {
        val configAllBase =
            tryDirectoryString(System.getenv("XDG_CONFIG_HOME"))
                ?: Path(
                        System.getProperty("user.home").takeIfNotNull(::tryDirectoryString)
                            ?: System.getenv("HOME").takeIfNotNull(::tryDirectoryString)
                            ?: Path("~")
                                .toAbsolutePath()
                                .pathString
                                .takeIfNotNull(::tryDirectoryString)
                                .expect("Could not find XDG_CONFIG_HOME or HOME directory")
                    )
                    .resolve(".config")
                    .takeIf(::validConfigPath)
                    .expect(
                        "Found a configuration directory that doesn't exist or isn't accessible."
                    )
        return configAllBase
    }
}

object MacOS : PlatformImpl() {
    // OSX uses ~/Library/Application Support/
    override fun configBasePath(): Path {
        val configAllBase =
            Path(
                    System.getProperty("user.home").takeIfNotNull(::tryDirectoryString)
                        ?: System.getenv("HOME").takeIfNotNull(::tryDirectoryString)
                        ?: Path("~")
                            .toAbsolutePath()
                            .pathString
                            .takeIfNotNull(::tryDirectoryString)
                            .expect("Could not find HOME directory")
                )
                .resolve("Library")
                .resolve("Application Support")
                .takeIf(::validConfigPath)
                .expect("Found a configuration directory that doesn't exist or isn't accessible.")
        return configAllBase
    }
}

object Windows : PlatformImpl() {
    // Windows uses %APPDATA% or %USERPROFILE%/AppData/Roaming
    override fun configBasePath(): Path {
        val configAllBase =
            tryDirectoryString(System.getenv("APPDATA"))
                ?: Path(
                        System.getProperty("user.home").takeIfNotNull(::tryDirectoryString)
                            ?: System.getenv("USERPROFILE").takeIfNotNull(::tryDirectoryString)
                            ?: Path("~")
                                .toAbsolutePath()
                                .pathString
                                .takeIfNotNull(::tryDirectoryString)
                                .expect("Could not find APPDATA or USERPROFILE directory")
                    )
                    .resolve("AppData")
                    .resolve("Roaming")
                    .takeIf(::validConfigPath)
                    .expect(
                        "Found a configuration directory that doesn't exist or isn't accessible."
                    )
        return configAllBase
    }
}

object Unknown : PlatformImpl() {
    // We just make our best guess and presume we are some other Unix-like that complies with XDG
    override fun configBasePath(): Path = Linux.configBasePath()
}

object Platform : PlatformImpl() {
    val actualPlatform by lazy { detectOperatingSystem() }

    override fun configBasePath(): Path = actualPlatform.configBasePath()
}
