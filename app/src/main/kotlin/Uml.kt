import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.logging.consumers.StdoutConsumer
import io.github.kelvindev15.kotlin2plantuml.plantuml.ClassDiagram
import io.github.kelvindev15.kotlin2plantuml.plantuml.Configuration
import kotlinx.serialization.Serializable
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmErasure

fun main() {
    Log.registerConsumer(StdoutConsumer())

    val root = "au.edu.unimelb.archivetool"

    // Build a global map of SimpleName -> FullyQualifiedName from all scanned classes
    val allClasses = ClasspathScanner.findClasses(root, requireReflectable = true)
    val globalTypeMap = mutableMapOf<String, String>()
    val externalSupertypes = mutableMapOf<String, MutableSet<String>>() // childSimpleName -> parentNames
    val classAnnotations = mutableMapOf<String, MutableSet<String>>()   // className -> annotationNames

    allClasses.forEach { kClass ->
        val simpleName = kClass.java.simpleName
        val qualName = kClass.normalisedQualifiedName()
        globalTypeMap[simpleName] = qualName

        // 1. Capture Supertypes (Inheritance)
        kClass.supertypes.forEach { supertype ->
            runCatching {
                val superKClass = supertype.jvmErasure
                val superSimple = superKClass.java.simpleName
                val superQual = superKClass.normalisedQualifiedName()

                if (superSimple != "Any" && superSimple != "Object") {
                    globalTypeMap[superSimple] = superQual
                    externalSupertypes.getOrPut(simpleName) { mutableSetOf() }.add(superSimple)
                }
            }
        }

        // 2. Capture Annotations for relationship lines
        kClass.annotations.forEach { ann ->
            val annClass = ann.annotationClass
            val annSimple = annClass.java.simpleName
            val annQual = annClass.qualifiedName ?: annClass.java.name

            // Skip built-in metadata annotations if desired, or keep them
            if (annSimple != "Metadata" && annSimple != "Target" && annSimple != "Retention") {
                globalTypeMap[annSimple] = annQual
                classAnnotations.getOrPut(simpleName) { mutableSetOf() }.add(annSimple)
            }
        }
    }

    val runs = allClasses
        .groupBy { it.java.packageName }
        .toSortedMap()
        .mapNotNull { (pkg, classes) ->
            runCatching {
                PackageRun(
                    packageName = pkg,
                    classNames = classes.map { it.normalisedQualifiedName() }.toSet(),
                    declarations = classes.map { kClass ->
                        val javaClass = kClass.java
                        val enclosing = javaClass.enclosingClass
                        DeclarationMetadata(
                            qualifiedName = kClass.normalisedQualifiedName(),
                            simpleName = javaClass.simpleName,
                            enclosingQualifiedName = enclosing?.name?.replace('$', '.'),
                            isObject = runCatching { kClass.objectInstance != null }.getOrDefault(false),
                            isInner = kClass.isInner,
                            companionMembers = kClass.synthesiseCompanionMembers(),
                            annotations = kClass.annotations.map { it.annotationClass.java.simpleName },
                            isSerializable = kClass.annotations.any { it is Serializable },
                        )
                    },
                    uml = ClassDiagram(
                        *classes.toTypedArray(),
                        configuration = Configuration(false, false, false, recurse = true, KVisibility.PRIVATE, KVisibility.PRIVATE)
                    ).plantUml(),
                )
            }.onFailure { println("Skipping package $pkg: $it") }.getOrNull()
        }

    val uml = UmlCombiner.combine(runs, rootPackage = root, globalTypeMap, externalSupertypes, classAnnotations)

    val f = createTempDirectory().resolve("archivetool.plantuml")
    f.writeText(uml)

    println("Wrote ${runs.size} packages to $f")
}

private fun KClass<*>.normalisedQualifiedName(): String =
    (qualifiedName ?: java.name).replace('$', '.')

private fun KClass<*>.synthesiseCompanionMembers(): List<String> {
    val companion = companionObject ?: return emptyList()
    return companion.declaredMembers
        .filterNot { it.isSyntheticMember() }
        .mapNotNull { member -> runCatching {
            val line = member.toPlantUmlLine() ?: return@runCatching null
            // Filter out serializer() method from companion members
            if (line.contains("serializer()")) return@runCatching null
            line
        }.getOrNull() }
        .distinct()
}

/**
 * `KCallable` has no `isSynthetic`. The backing Java member does, and kotlin-reflect exposes it
 * through `javaField` / `javaMethod` / `javaConstructor`. Any of those that is a
 * `java.lang.reflect.Member` carries the flag.
 */
private fun KCallable<*>.isSyntheticMember(): Boolean {
    val member: java.lang.reflect.Member? = when (this) {
        is KProperty<*> -> runCatching { javaField }.getOrNull()
        is KFunction<*> -> runCatching { javaMethod }.getOrNull()
        else -> null
    }
    return member?.isSynthetic ?: false
}

private fun KCallable<*>.toPlantUmlLine(): String? {
    val visibility = when (visibility) {
        KVisibility.PUBLIC -> "+"
        KVisibility.PROTECTED -> "#"
        KVisibility.INTERNAL -> "~"
        KVisibility.PRIVATE -> "-"
        null -> "+"
    }
    val name = name
    return when (this) {
        is KProperty<*> -> {
            val isConstant = isFinal && (this.visibility == KVisibility.PUBLIC) // heuristic for constants
            val prefix = if (isConstant) "{static} {const} " else "$visibility"
            val type = returnType.jvmErasure.simpleName ?: return null
            "$prefix$name: $type"
        }
        is KFunction<*> -> {
            // Drop the instance / extension-receiver slots; keep declared parameters only.
            val params = parameters
                .filter { it.kind == KParameter.Kind.VALUE }
                .joinToString(", ") { p ->
                    val t = p.type.jvmErasure.simpleName ?: "Any"
                    "${p.name ?: "_"}: $t"
                }
            val ret = returnType.jvmErasure.simpleName ?: "Unit"
            "$visibility$name($params): $ret"
        }
        else -> null
    }
}