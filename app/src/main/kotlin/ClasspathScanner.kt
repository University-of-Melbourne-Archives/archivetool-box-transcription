package au.edu.unimelb.archivetool.app

import java.io.IOException
import java.net.JarURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmErasure

/**
 * Scans the classpath for every class in a package and returns them as [KClass] instances.
 * No third-party dependencies.
 *
 * Handles:
 *  - exploded directories (e.g. build/classes, IDE runs)
 *  - regular JAR files
 *
 * Not handled out of the box:
 *  - Spring Boot fat JARs (nested jars use a custom protocol/classloader)
 *  - JPMS runtime images ("jrt:" URLs) - fine for your own app code, not for JDK packages
 *
 * Usage:
 *   val classes = ClasspathScanner.findClasses("com.example.plugins")
 *   val handlers = ClasspathScanner.findClasses("com.example.handlers") { it.isSubclassOf(Handler::class) }
 */
object ClasspathScanner {

    /**
     * Substrings that identify compiler/plugin-generated helper classes. Matched against the
     * binary class name, so they are filtered out before the class is even loaded.
     * Add your own here if other generated classes show up (e.g. "\$\$Lambda").
     */
    val GENERATED_MARKERS: List<String> = listOf(
        "\$\$serializer",         // kotlinx.serialization
        "\$WhenMappings",         // `when` over enums
        "\$DefaultImpls",         // interface default method bridges
        "\$\$inlined\$",          // inlined lambdas
        "\$\$ExternalSyntheticLambda",
    )

    /**
     * @param packageName     e.g. "com.example.plugins"
     * @param recursive       include sub-packages
     * @param initialize      run static initializers on load (default false - safer for scanning)
     * @param includeNested   include nested/inner/anonymous classes (names containing '$')
     * @param excludeGenerated
     *                        skip compiler/plugin-generated helper classes such as
     *                        `Foo$$serializer` (kotlinx.serialization), `$WhenMappings`,
     *                        `$DefaultImpls` and `$$inlined$...`. On by default; see
     *                        [GENERATED_MARKERS] for the full list.
     * @param includeAnonymousAndLocal
     *                        include anonymous classes (`object : Foo {}`, Java `Outer$1`) and
     *                        local classes declared inside functions. Their simpleName /
     *                        qualifiedName are null, which shows up as "null" in diagram and
     *                        serialization tools. Off by default.
     * @param includeKotlinSyntheticClasses
     *                        include Kotlin file facades (FooKt, multifile facades/parts) and
     *                        compiler-generated synthetic classes (lambdas, etc.). Off by default
     *                        because kotlin-reflect throws KotlinReflectionInternalError on many
     *                        of them (e.g. files with generic top-level functions).
     * @param requireReflectable
     *                        drop classes that kotlin-reflect cannot fully introspect (supertypes,
     *                        member parameter/return types, etc.). Avoids errors such as
     *                        "Cannot calculate JVM erasure for type: ???" in downstream tools.
     *                        Off by default since it touches every member of every class.
     * @param classLoader     loader used to find resources and load classes
     * @param filter          optional predicate applied to each loaded class
     */
    fun findClasses(
        packageName: String,
        recursive: Boolean = true,
        initialize: Boolean = false,
        includeNested: Boolean = true,
        excludeGenerated: Boolean = true,
        includeAnonymousAndLocal: Boolean = false,
        includeKotlinSyntheticClasses: Boolean = false,
        requireReflectable: Boolean = false,
        classLoader: ClassLoader = defaultClassLoader(),
        filter: (KClass<*>) -> Boolean = { true },
    ): List<KClass<*>> {
        require(packageName.isNotBlank()) { "packageName must not be blank" }

        val resourcePath = packageName.trim().replace('.', '/')
        val classNames = sortedSetOf<String>()

        for (url in classLoader.getResources(resourcePath)) {
            when (url.protocol) {
                "file" -> scanDirectory(Paths.get(url.toURI()), packageName, recursive, classNames)
                "jar" -> scanJar(url, resourcePath, recursive, classNames)
                // Other protocols (e.g. "jrt", "vfs") are skipped.
            }
        }

        return classNames.asSequence()
            .filter { name -> includeNested || '$' !in name }
            .filter { name -> !excludeGenerated || GENERATED_MARKERS.none { it in name } }
            .mapNotNull { name -> loadOrNull(name, initialize, includeKotlinSyntheticClasses, classLoader) }
            .filter { includeAnonymousAndLocal || !it.java.isAnonymousOrLocal() }
            .filter { !requireReflectable || it.isSafelyReflectable() }
            .filter(filter)
            .toList()
    }

    // ---------------------------------------------------------------------------------------------

    private fun scanDirectory(
        root: Path,
        packageName: String,
        recursive: Boolean,
        out: MutableSet<String>,
    ) {
        if (!Files.isDirectory(root)) return
        val maxDepth = if (recursive) Int.MAX_VALUE else 1

        Files.walk(root, maxDepth).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".class") }
                .forEach { file ->
                    val relative = root.relativize(file).toString()
                        .removeSuffix(".class")
                        .replace('/', '.')
                        .replace('\\', '.')
                    toClassName(packageName, relative)?.let(out::add)
                }
        }
    }

    private fun scanJar(
        url: URL,
        resourcePath: String,
        recursive: Boolean,
        out: MutableSet<String>,
    ) {
        val connection = url.openConnection() as JarURLConnection
        connection.useCaches = false // avoid leaving the jar locked/cached
        try {
            connection.jarFile.use { jar ->
                val prefix = "$resourcePath/"
                for (entry in jar.entries()) {
                    val name = entry.name
                    if (entry.isDirectory || !name.startsWith(prefix) || !name.endsWith(".class")) continue

                    val remainder = name.removePrefix(prefix)
                    if (!recursive && '/' in remainder) continue

                    val className = name.removeSuffix(".class").replace('/', '.')
                    if (!isSkippable(className)) out.add(className)
                }
            }
        } catch (_: IOException) {
            // Unreadable jar - ignore and keep scanning other locations.
        }
    }

    private fun toClassName(packageName: String, relative: String): String? {
        val full = "$packageName.$relative"
        return full.takeUnless(::isSkippable)
    }

    private fun isSkippable(className: String): Boolean {
        val simple = className.substringAfterLast('.')
        return simple == "package-info" || simple == "module-info"
    }

    private fun loadOrNull(
        name: String,
        initialize: Boolean,
        includeKotlinSyntheticClasses: Boolean,
        loader: ClassLoader,
    ): KClass<*>? =
        try {
            val clazz = Class.forName(name, initialize, loader)
            if (!includeKotlinSyntheticClasses && clazz.isKotlinSyntheticOrFacade()) null else clazz.kotlin
        } catch (_: ClassNotFoundException) {
            null
        } catch (_: LinkageError) {
            // NoClassDefFoundError, UnsatisfiedLinkError, ExceptionInInitializerError, etc.
            null
        }

    /**
     * Uses java.lang.Class rather than kotlin-reflect so it can't itself blow up on odd classes.
     * A null canonicalName covers anonymous classes, local classes, and anything nested inside them.
     */
    private fun Class<*>.isAnonymousOrLocal(): Boolean =
        runCatching { isAnonymousClass || isLocalClass || canonicalName == null }
            .getOrDefault(true) // malformed names: treat as unusable

    /**
     * kotlin.Metadata.kind values:
     *   1 = class, 2 = file facade, 3 = synthetic class (lambdas etc.),
     *   4 = multifile class facade, 5 = multifile class part
     */
    private fun Class<*>.isKotlinSyntheticOrFacade(): Boolean {
        val kind = getAnnotation(Metadata::class.java)?.kind ?: return false
        return kind != 1
    }

    private fun defaultClassLoader(): ClassLoader =
        Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
}

/**
 * True if kotlin-reflect can resolve the JVM erasure of every supertype and every member's
 * return/parameter type. Any Throwable (KotlinReflectionInternalError, UnsupportedOperationException,
 * LinkageError from a missing dependency, ...) counts as "not reflectable".
 */
fun KClass<*>.isSafelyReflectable(): Boolean =
    runCatching {
        supertypes.forEach { it.jvmErasure }
        members.forEach { member ->
            member.returnType.jvmErasure
            member.parameters.forEach { it.type.jvmErasure }
        }
        true
    }.getOrDefault(false)

/**
 * Diagnostic: returns the classes in the package that kotlin-reflect cannot handle, with the
 * failure reason, so you can see exactly what is tripping up your tooling.
 */
fun findUnreflectableClasses(packageName: String, recursive: Boolean = true): Map<KClass<*>, Throwable> =
    ClasspathScanner.findClasses(packageName, recursive).mapNotNull { kClass ->
        runCatching {
            kClass.supertypes.forEach { it.jvmErasure }
            kClass.members.forEach { member ->
                member.returnType.jvmErasure
                member.parameters.forEach { it.type.jvmErasure }
            }
        }.exceptionOrNull()?.let { kClass to it }
    }.toMap()
