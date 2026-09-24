package au.edu.unimelb.archivetool.util.os

fun detectOperatingSystem(): PlatformImpl {
    System.getProperty("os.name").lowercase().let {
        return when {
            it.contains("win") -> Windows
            it.contains("linux") -> Linux
            it.contains("mac") -> MacOS
            else -> Unknown
        }
    }
}