import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.logging.LogLevel
import au.edu.unimelb.archivetool.util.logging.consumers.StdoutConsumer
import io.github.kelvindev15.kotlin2plantuml.plantuml.ClassDiagram
import io.github.kelvindev15.kotlin2plantuml.plantuml.Configuration
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.reflect.KVisibility

fun processUml(uml: String): String = uml.run {
    val prepend = listOf("hide empty members", "hide circle")
    val edges = mutableSetOf<String>()
    val append = mutableListOf<String>()
    val classNames = mutableSetOf<String>()
    "@startuml\n" + prepend.joinToString("\n") + "\n" +
            lineSequence().joinToString("\n") {
                it
                    .removeStartEndDirectives()
                    .replaceUnitReturnValues()
                    .replaceFunctionNWithLambda()

            }.getPlantUmlClasses().run {
                map {
                    it to (classBase.find(it)?.groups["classname"]?.value ?: "Anonymous")
                }.also {
                    it.forEach { (_, className) ->
                        classNames.add(className)
                    }
                }.joinToString("\n") { (string, className) ->
                    val (remaining, found) = string.removeAutomaticCode(className).attributesToAssociations(classNames)
                    edges += found
                    remaining
                }
            } + "\n" + append.apply {addAll(edges)}.joinToString("\n") + "\n@enduml\n"
}

val classBase by lazy {
    "(?:abstract )?(?:class|interface) (?<classname>\\w+)".toRegex()
}
val parameterRegex by lazy {
    "\\w+\\s*: (?:\\w+|\\w+<\\w+>)".toRegex()
}
val attributeRegex by lazy {
    "^ {4}[+~#-](?:\\{\\w+\\} )?(?<name>\\w+): (?<type>.+)\$".toRegex()
}
val classRegex by lazy {
    val genericType = "\\w+(?:\\s*: (?:\\w+|\\w+<\\w+>))?".toRegex()
    val generics = "(?:<$genericType(?:,(?:\\s|\\\\n)*$genericType)*>)?".toRegex()
    val member = "\\s{4}.*\\n".toRegex()

    "$classBase$generics \\{\\n(?:$member)*\\}".toRegex()
}
fun String.getPlantUmlClasses(): Sequence<String> = classRegex.findAll(this).map { it.value }
fun String.removeStartEndDirectives(): String = replace("@startuml", "").replace("@enduml", "")
fun String.replaceUnitReturnValues(): String = "(\\(.*\\)): Unit".toRegex().replace(this) {
    it.groupValues[1]
}
fun String.replaceFunctionNWithLambda(): String = "Function(?<n>\\d+)<(?<types>\\w+(?:, \\w+)*)>".toRegex().replace(this) {
    val params = it.groups["n"]!!.value.toInt()
    val types = it.groups["types"]!!.value.split(", ")
    val paramTypes = types.subList(0, params)
    "(${paramTypes.joinToString()}) -> ${types.last()}"
}
fun String.removeAutomaticCode(className: String): String {
    val classBaseMatch = classBase.find(this)
    if (!(classBaseMatch?.value?.contains("class ") ?: false)) return this
    val componentRegex by lazy {
        "^ {4}\\+component\\d+\\(\\): \\w+\$".toRegex()
    }
    val copyRegex by lazy {
        "^ {4}\\+copy\\((?:$parameterRegex(?:, $parameterRegex)*)?\\): ${Regex.escape(className)}\$".toRegex()
    }
    val equalsRegex by lazy {
        "^ {4}\\+equals\\(other: Any\\): Boolean\$".toRegex()
    }
    val hcRegex by lazy {
        "^ {4}\\+hashCode\\(\\): Int\$".toRegex()
    }
    val toStringRegex by lazy {
        "^ {4}\\+toString\\(\\): String\$".toRegex()
    }
    val writeRegex by lazy {
        "^ {4}[~+]write\\\$Self\\(self: $className, output: CompositeEncoder, serialDesc: SerialDescriptor\\)\$".toRegex()
    }
    return lineSequence()
        .filter { !it.matches(copyRegex) }
        .filter { !it.matches(componentRegex) }
        .filter { !it.matches(equalsRegex) }
        .filter { !it.matches(hcRegex) }
        .filter { !it.matches(toStringRegex) }
        .filter { !it.matches(writeRegex) }
        .joinToString("\n")
}

val multiValuedTypes = setOf(
    "List", "MutableList", "ArrayList",
    "Set", "MutableSet", "HashSet", "LinkedHashSet",
    "Collection", "MutableCollection", "Iterable", "Sequence",
    "Array", "Queue", "Deque", "ArrayDeque",
    "Map", "MutableMap", "HashMap", "LinkedHashMap",
)

fun getMultiplicity(type: String, classNames: Set<String>): Map<String, String?> {
    if ("->" in type) return emptyMap()
    val outer = "\\w+".toRegex().find(type)?.value ?: return emptyMap()
    val multiple = outer in multiValuedTypes
    val nullable = type.endsWith("?")

    return "\\w+".toRegex().findAll(type)
        .map { it.value }
        .filter { it in classNames }
        .distinct()
        .associateWith { id ->
            when {
                multiple -> "0..*"
                id == outer -> if (nullable) "0..1" else "1"
                else -> null
            }
        }
}

fun edge(owner: String, target: String, multiplicity: String?, label: String): String =
    "$owner 1 --> ${if (multiplicity != null) {"\"$multiplicity\" "} else {""}}$target : $label"

/**
 * For one class block: removes every attribute whose type references another known class and
 * returns the trimmed block together with the association edges that replace those attributes.
 * Attributes of plain types (String, Int, lambdas, external classes) are left untouched.
 */
fun String.attributesToAssociations(classNames: Set<String>): Pair<String, List<String>> {
    val owner = classBase.find(this)?.groups?.get("classname")?.value
        ?: return this to emptyList()
    val edges = mutableListOf<String>()

    val remaining = lineSequence().filter { line ->
        val match = attributeRegex.matchEntire(line) ?: return@filter true
        val targets = getMultiplicity(match.groups["type"]!!.value.trim(), classNames)
        if (targets.isEmpty()) return@filter true

        val label = match.groups["name"]!!.value
        targets.forEach { (target, multiplicity) -> edges += edge(owner, target, multiplicity, label) }
        false
    }.joinToString("\n")

    return remaining to edges
}


fun main() {
    Log.registerConsumer(StdoutConsumer())

    //println(classRegex)

    ///*
    val uml = ClassDiagram(
        *(ClasspathScanner.findClasses("au.edu.unimelb.archivetool", requireReflectable = true).toTypedArray()),
        configuration = Configuration(false, false, false, recurse = true, KVisibility.PRIVATE, KVisibility.PRIVATE)
    ).plantUml().let {
        println("Raw: $it")
        processUml(it)
    }

    val f = createTempDirectory().resolve("archivetool.plantuml")
    f.writeText(uml)

    println("Wrote $uml to $f")
    // */
}