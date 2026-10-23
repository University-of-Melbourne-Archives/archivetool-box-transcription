/** The raw PlantUML produced by one generator run over the classes of a single package. */
class PackageRun(
    val packageName: String,
    /** Qualified names (normalised) of the classes handed to the generator for this package. */
    val classNames: Set<String>,
    val uml: String,
    val declarations: List<DeclarationMetadata> = emptyList(),
)

data class DeclarationMetadata(
    val qualifiedName: String,
    val simpleName: String,
    val enclosingQualifiedName: String? = null,
    val isObject: Boolean = false,
    val isInner: Boolean = false,
    val isSerializable: Boolean = false,
    val companionMembers: List<String> = emptyList(),
    val companionAssociations: List<UmlCombiner.Relation> = emptyList(),
    val annotations: List<String> = emptyList(),
    val visibility: String = "",
    val isSealed: Boolean = false,
)

object UmlCombiner {

    /**
     * Merges several per-package runs into one diagram.
     *
     * Identity is the normalised qualified class name throughout. Simple names survive only in
     * [resolveSimple], which maps the generator's own simple-name output back to a qualified key
     * using the scanner's class list for the run's package. There is no cross-package guessing.
     *
     * Rendering:
     * - one `package` box per package, named relative to [rootPackage];
     * - every class gets an alias derived from its qualified name, so same-named classes in
     *   different packages never merge;
     * - blocks are only trusted from the run of the package that owns the class (per the scanner),
     *   which also discards anonymous objects the generator discovers by itself;
     * - nested classes get a circle node between them and their direct parent (UML nesting);
     * - `inner` classes additionally get a composition edge to their parent;
     * - companion members are appended to their owner's block, marked `{static}`;
     * - attributes pointing at other diagram classes become association edges;
     * - inheritance arrows are resolved globally and kept.
     */
    fun combine(
        runs: List<PackageRun>,
        rootPackage: String = "",
        externalTypeMap: Map<String, String> = emptyMap(),
        externalSupertypes: Map<String, Set<String>> = emptyMap(),
        classAnnotations: Map<String, Set<String>> = emptyMap(),
    ): String {
        val parsed = runs.map { it to parse(it.uml) }

        val metadataByQualifiedName =
            runs.flatMap { run -> run.declarations.map { it.qualifiedName to it } }.toMap()

        val globalBySimpleName: Map<String, List<String>> =
            runs
                .flatMap { run -> run.declarations.map { it.simpleName to it.qualifiedName } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, v) -> v.distinct() }

        val localBySimpleName: Map<String, Map<String, String>> = runs.associate { run ->
            run.packageName to run.declarations.associate { it.simpleName to it.qualifiedName }
        }

        fun resolveSimple(name: String, fromPackage: String): String? {
            localBySimpleName[fromPackage]?.get(name)?.let {
                return it
            }
            val candidates = globalBySimpleName[name] ?: return null
            return candidates.singleOrNull()
        }

        val definitions = linkedMapOf<String, MutableList<Version>>()
        val versionAt = mutableMapOf<Pair<String, Int>, Version>()

        for ((run, items) in parsed) {
            items.forEachIndexed { index, item ->
                if (item !is ClassBlock) return@forEachIndexed
                val qualified = resolveSimple(item.name, run.packageName) ?: return@forEachIndexed
                if (qualified !in run.classNames) return@forEachIndexed
                val metadata = qualified.let { metadataByQualifiedName[it] }
                val body = clean(item.lines, item.name, metadata?.isSerializable == true)
                val versions = definitions.getOrPut(qualified) { mutableListOf() }
                val version =
                    versions.firstOrNull { Version -> Version.body == body }
                        ?: Version(aliasFor(qualified, versions.size), body).also { versions += it }
                version.positions += index
                versionAt[run.packageName to index] = version
            }
        }

        val packageBlocks = linkedMapOf<String, MutableList<String>>()
        val inheritance = linkedSetOf<String>()
        val associations = linkedSetOf<String>()
        val externalClasses = mutableSetOf<String>()
        val externalAnnotations = mutableSetOf<String>() // Separate set for annotations

        // 1. Inject external or missing supertype relationships
        externalSupertypes.forEach { (childSimple, parents) ->
            val childQual = globalBySimpleName[childSimple]?.singleOrNull() ?: return@forEach
            val childAlias = aliasFor(childQual)

            parents.forEach { parentSimple ->
                val parentQual = externalTypeMap[parentSimple] ?: parentSimple
                val parentAlias =
                    if (parentQual in globalBySimpleName.values.flatten()) {
                        aliasFor(parentQual)
                    } else {
                        if (isAllowedExternal(parentSimple)) {
                            externalClasses += parentSimple
                            "ext_${aliasFor(parentSimple)}"
                        } else null
                    }

                if (parentAlias != null && !parentQual.contains("au.edu.unimelb")) {
                    inheritance += "$parentAlias <|-- $childAlias"
                }
            }
        }

        // 2. Inject Annotation relationship lines (Class ..> Annotation)
        classAnnotations.forEach { (classSimple, annotations) ->
            val classQual = globalBySimpleName[classSimple]?.singleOrNull() ?: return@forEach
            val classAlias = aliasFor(classQual)

            annotations.forEach { annSimple ->
                val annQual = externalTypeMap[annSimple] ?: annSimple
                if (isAllowedExternal(annSimple)) {
                    externalAnnotations += annSimple
                    val annAlias = "ext_${aliasFor(annSimple)}"
                    // associations += "$classAlias ..> $annAlias : <<annotation>>"
                }
            }
        }

        runs.forEach { run ->
            run.declarations.forEach { decl ->
                decl.companionAssociations.forEach { rel ->
                    val parent = resolveSimple(rel.parent, run.packageName)
                    val child = resolveSimple(rel.child, run.packageName)

                    val parentAlias =
                        if (parent != null) aliasFor(parent)
                        else {
                            if (isAllowedExternal(rel.parent)) {
                                externalClasses += rel.parent
                                "ext_${aliasFor(rel.parent)}"
                            } else null
                        }
                    val childAlias = child?.let { aliasFor(it) }

                    if (parentAlias != null && childAlias != null) {
                        inheritance +=
                            "$parentAlias <|${if (rel.dashed) ".." else "--"} $childAlias" +
                                if (rel.label != "") " : \"${rel.label}\"" else rel.label
                    }
                }
            }
        }

        for ((run, items) in parsed) {
            val blocks = packageBlocks.getOrPut(run.packageName) { mutableListOf() }
            items.forEachIndexed { index, item ->
                when (item) {
                    is Relation -> {
                        val parent = resolveSimple(item.parent, run.packageName)
                        val child = resolveSimple(item.child, run.packageName)

                        val parentAlias =
                            if (parent != null) aliasFor(parent)
                            else {
                                if (isAllowedExternal(item.parent)) {
                                    externalClasses += item.parent
                                    "ext_${aliasFor(item.parent)}"
                                } else null
                            }
                        val childAlias = child?.let { aliasFor(it) }

                        if (parentAlias != null && childAlias != null) {
                            inheritance +=
                                "$parentAlias <|${if (item.dashed) ".." else "--"} $childAlias" +
                                    if (item.label != "") " : \"${item.label}\"" else item.label
                        }
                    }
                    is ClassBlock -> {
                        val version = versionAt[run.packageName to index] ?: return@forEachIndexed
                        if (version.positions.first() != index) return@forEachIndexed

                        val (kept, edges) =
                            extractAssociations(
                                ownerAlias = version.alias,
                                body = version.body,
                                knownNames = globalBySimpleName.keys,
                                // includeMethodUses = true,
                                externalClasses = externalClasses,
                                resolveTarget = { resolveSimple(it, run.packageName) },
                                onExternalFound = { extName ->
                                    if (isAllowedExternal(extName)) {
                                        externalClasses += extName
                                        "ext_${aliasFor(extName)}"
                                    } else null
                                },
                            )
                        associations += edges

                        val qualified = resolveSimple(item.name, run.packageName)
                        val metadata = qualified?.let { metadataByQualifiedName[it] }
                        val owner = metadata?.enclosingQualifiedName

                        val memberLines = buildList {
                            addAll(kept.dropLast(1))
                            metadata?.companionMembers?.forEach { add(markStatic(it)) }
                            add(kept.last())
                        }

                        blocks += renderBlock(memberLines, version.alias, metadata)

                        if (owner != null) {
                            val ownerAlias = aliasFor(owner)
                            if (metadata.isInner) {
                                blocks += "$ownerAlias +-- ${version.alias}"
                            } else {
                                // Non-inner static nested classes use a clean association or
                                // aggregation link
                                blocks += "$ownerAlias +.. ${version.alias}"
                            }
                        }
                    }
                }
            }
        }

        return buildString {
            appendLine("@startuml")
            appendLine("!pragma layout elk")
            appendLine("allowmixing")
            appendLine("top to bottom direction")
            appendLine("hide empty members")
            // appendLine("hide circle")
            appendLine("skinparam linetype ortho")
            appendLine("skinparam defaultFontName Liberation Sans")
            appendLine("skinparam nodesep 15")
            appendLine("skinparam ranksep 25")
            appendLine("skinparam node {\n\tBackgroundColor #AAAAAA\n}")
            appendLine("skinparam class {\n\tBackgroundColor<<sealed>> Moccasin\n}")

            // Render external classes and external annotations cleanly in their own package
            if (externalClasses.isNotEmpty() || externalAnnotations.isNotEmpty()) {
                appendLine("package \"External\" <<Node>> {")
                externalClasses.sorted().forEach { ext ->
                    val safeAlias = "ext_${aliasFor(ext)}"
                    val displayName = externalTypeMap[ext] ?: ext
                    appendLine("    class \"$displayName\" as $safeAlias <<external>>")
                }
                externalAnnotations.sorted().forEach { ann ->
                    val safeAlias = "ext_${aliasFor(ann)}"
                    val displayName = externalTypeMap[ann] ?: ann
                    appendLine("    annotation \"$displayName\" as $safeAlias <<external>>")
                }
                appendLine("}")
                appendLine()
            }

            for ((pkg, blocks) in packageBlocks) {
                if (blocks.isEmpty()) continue
                appendLine("package \"${displayName(pkg, rootPackage)}\" {")
                blocks.forEach { block -> block.lines().forEach { appendLine("    $it") } }
                appendLine("}")
                appendLine()
            }

            inheritance.forEach { appendLine(it) }
            associations.forEach { appendLine(it) }
            appendLine("@enduml")
        }
    }

    // ---- model
    // ---------------------------------------------------------------------------------------------

    private sealed interface Item

    private class ClassBlock(val name: String, val lines: List<String>) : Item

    class Relation(
        val parent: String,
        val child: String,
        val dashed: Boolean,
        val label: String = "",
    ) : Item

    /** One distinct definition of a class. */
    private class Version(val alias: String, val body: List<String>) {
        val positions = mutableListOf<Int>()
    }

    // ---- regexes
    // -------------------------------------------------------------------------------------------

    private val headerRegex =
        Regex(
            """^(?<kw>(?:abstract )?(?:class|interface)) (?<name>\w+)(?<generics><.*>)?\s*\{\s*$"""
        )

    private val attributeRegex = Regex("""^ {4}[+~#-](?:\{\w+\} )?(?<name>\w+): (?<type>.+)$""")

    private val identifierRegex = Regex("""\w+""")
    private val unitReturnRegex = Regex("""(\(.*\)): Unit""")
    private val functionNRegex = Regex("""Function(?<n>\d+)<(?<types>\w+\??(?:, \w+\??)*)>""")

    private fun nameGroup(group: String) = """["]?(?<$group>[\w.]+)["]?"""

    private val parentFirstRegex =
        Regex(
            """^\s*${nameGroup("parent")}\s+(?<arrow><\|[-.]+\w*[-.]*)\s+${nameGroup("child")}\s*(?::.*)?$"""
        )
    private val childFirstRegex =
        Regex(
            """^\s*${nameGroup("child")}\s+(?<arrow>[-.]+\w*[-.]*\|>)\s+${nameGroup("parent")}\s*(?::.*)?$"""
        )

    private val genericMembers =
        listOf(
            Regex("""^ {4}[+~#-]component\d+\(\).*$"""),
            Regex("""^ {4}[+~#-]valueOf\(value: String\): \w+$"""),
            Regex("""^ {4}[+~#-]values\(\): Array<\w+>$"""),
            Regex("""^ {4}[+~#-]entries: EnumEntries<\w+>$"""),
        )

    private fun classMembers(className: String): List<Regex> {
        val name = Regex.escape(className)
        return listOf(
            Regex("""^ {4}\+copy\(.*\): $name$"""),
            Regex("""^ {4}\+equals\(other: Any\): Boolean$"""),
            Regex("""^ {4}\+hashCode\(\): Int$"""),
            Regex("""^ {4}\+toString\(\): String$"""),
            Regex("""^ {4}[~+]write\${'$'}Self\(self: $name, .*$"""),
        )
    }

    private val multiValuedTypes =
        setOf(
            "List",
            "MutableList",
            "ArrayList",
            "Set",
            "MutableSet",
            "HashSet",
            "LinkedHashSet",
            "Collection",
            "MutableCollection",
            "Iterable",
            "Sequence",
            "Array",
            "Queue",
            "Deque",
            "ArrayDeque",
            "Map",
            "MutableMap",
            "HashMap",
            "LinkedHashMap",
        )

    // ---- parsing
    // -------------------------------------------------------------------------------------------

    private fun normalise(line: String): String =
        line
            .replace("@startuml", "")
            .replace("@enduml", "")
            .let { unitReturnRegex.replace(it) { m -> m.groupValues[1] } }
            .let {
                val matches = functionNRegex.findAll(it)
                if (matches.any()) {
                    matches.fold(it) { s, match ->
                        val params = match.groups["n"]!!.value.toInt()
                        val types = match.groups["types"]!!.value.split(", ")
                        s.replace(
                            match.value,
                            "(${types.take(params).joinToString()}) -> ${types.last()}",
                        )
                    } + " {field}"
                } else it
            }

    private fun String.toRelation(): Relation? {
        val match =
            parentFirstRegex.matchEntire(this) ?: childFirstRegex.matchEntire(this) ?: return null
        return Relation(
            parent = match.groups["parent"]!!.value.substringAfterLast('.'),
            child = match.groups["child"]!!.value.substringAfterLast('.'),
            dashed = '.' in match.groups["arrow"]!!.value,
        )
    }

    private fun parse(uml: String): List<Item> {
        val items = mutableListOf<Item>()
        var lines: MutableList<String>? = null
        var name = ""

        for (raw in uml.lineSequence()) {
            val line = normalise(raw).trimEnd()
            val open = lines
            if (open != null) {
                open += line
                if (line == "}") {
                    items += ClassBlock(name, open)
                    lines = null
                }
                continue
            }
            val header = headerRegex.matchEntire(line)
            if (header != null) {
                lines = mutableListOf(line)
                name = header.groups["name"]!!.value
            } else {
                line.toRelation()?.let { items += it }
            }
        }
        return items
    }

    private fun clean(
        lines: List<String>,
        className: String,
        isSerializable: Boolean = false,
    ): List<String> {
        val keyword = headerRegex.matchEntire(lines.first())?.groups?.get("kw")?.value.orEmpty()
        val patterns =
            genericMembers + if (keyword.endsWith("class")) classMembers(className) else emptyList()
        return lines.filterIndexed { i, line ->
            if (i == 0) return@filterIndexed true
            // Filter out the automatic serializer() function if the class is serializable
            if (isSerializable && line.contains("serializer()")) return@filterIndexed false
            patterns.none { line.matches(it) }
        }
    }

    // ---- associations
    // --------------------------------------------------------------------------------------

    private fun associationTargets(
        type: String,
        isKnown: (String) -> Boolean,
    ): Map<String, String?> {
        if ("->" in type) return emptyMap()
        val outer = identifierRegex.find(type)?.value ?: return emptyMap()
        if (outer.startsWith("Function") || outer.startsWith("KFunction")) return emptyMap()
        val multiple = outer in multiValuedTypes
        val nullable = type.endsWith("?")

        return identifierRegex
            .findAll(type)
            .map { it.value }
            .filter(isKnown)
            .distinct()
            .associateWith { id ->
                when {
                    multiple -> "0..*"
                    id == outer -> if (nullable) "0..1" else "1"
                    else -> null
                }
            }
    }

    private fun edge(
        owner: String,
        target: String,
        multiplicity: String?,
        label: String? = null,
        qualifier: String? = null,
    ): String = buildString {
        append(owner)
        qualifier?.let { append(" [$qualifier] ") } ?: append("\"1\"")
        append(" -->")
        multiplicity?.let { append(" \"").append(multiplicity).append('"') }
        append(' ').append(target)
        label?.let { append(" : ").append(label) }
    }

    private val methodRegex = Regex("""^ {4}[+~#-]\w+\((?<params>.*?)\): (?<ret>.+)$""")

    private fun extractAssociations(
        ownerAlias: String,
        body: List<String>,
        knownNames: Set<String>,
        externalClasses: MutableSet<String>,
        resolveTarget: (String) -> String?,
        onExternalFound: (String) -> String?,
    ): Pair<List<String>, List<String>> {
        val edges = mutableListOf<String>()
        val isKnown: (String) -> Boolean = { it in knownNames }
        val kept = mutableListOf<String>()

        for (line in body) {
            val attrMatch = attributeRegex.matchEntire(line)
            if (attrMatch != null) {
                val typeStr = attrMatch.groups["type"]!!.value.trim()
                val targets =
                    associationTargets(typeStr, isKnown).mapNotNull { (name, multiplicity) ->
                        val resolved = resolveTarget(name) ?: onExternalFound(name)
                        resolved?.let { aliasFor(it) to multiplicity }
                    }

                if (targets.isNotEmpty()) {
                    val label = attrMatch.groups["name"]!!.value
                    targets.forEach { (target, multiplicity) ->
                        edges +=
                            edge(
                                ownerAlias,
                                target,
                                multiplicity,
                                label = label,
                                qualifier = "$label: $typeStr",
                            )
                    }
                    continue
                }
            }

            // 2. Process Method Usages safely by parsing parameter types after colons and return
            // types
            val methodMatch = methodRegex.matchEntire(line)
            if (methodMatch != null) {
                val paramsStr = methodMatch.groups["params"]!!.value
                val retStr = methodMatch.groups["ret"]!!.value

                // Extract only types (parts coming after colon in parameters, plus the return
                // type)
                val typeTokens = mutableSetOf<String>()

                // Parse parameters like "job: BoxMetadata, index: Int" -> take "BoxMetadata",
                // "Int"
                paramsStr.split(',').forEach { param ->
                    val parts = param.split(':')
                    if (parts.size > 1) {
                        identifierRegex.findAll(parts[1]).forEach { typeTokens += it.value }
                    }
                }
                // Parse return type
                identifierRegex.findAll(retStr).forEach { typeTokens += it.value }

                typeTokens
                    .filter { it !in multiValuedTypes }
                    .distinct()
                    .forEach { typeName ->
                        val targetQualified = resolveTarget(typeName)
                        val targetAlias =
                            when {
                                targetQualified != null -> aliasFor(targetQualified)
                                isAllowedExternal(typeName) -> {
                                    externalClasses += typeName
                                    "ext_${aliasFor(typeName)}"
                                }

                                else -> null
                            }

                        if (targetAlias != null && targetAlias != ownerAlias) {
                            edges += $$"$$ownerAlias ..> $$targetAlias : uses"
                        }
                    }
            }

            kept += line
        }

        return kept to edges
    }

    private fun isAllowedExternal(name: String): Boolean {
        // Ignore single-letter generic type parameters (e.g., T, E, K, V, S)
        if (name.length == 1 && name[0].isUpperCase()) return false

        // Explicitly block primitives, standard library types, collections, and functions
        val ignoredTypes =
            setOf(
                // Primitives & Basic Types
                "Int",
                "Long",
                "Double",
                "Float",
                "Boolean",
                "Byte",
                "Char",
                "Short",
                "String",
                "Unit",
                "Any",
                "Nothing",
                "CharSequence",
                "Number",
                "Enum",
                "UByte",
                "UShort",
                "UInt",
                "ULong",
                // Arrays & Collections & Tuples
                "Pair",
                "Triple",
                "Array",
                "List",
                "Set",
                "Map",
                "MutableList",
                "MutableSet",
                "MutableMap",
                "Collection",
                "Iterable",
                "Sequence",
                "Iterator",
                // Reflection & Lambdas / Functions
                "KProperty",
                "KFunction",
                "KCallable",
                "KClass",
                "Function",
                "Function0",
                "Function1",
                "Function2",
                "Function3",
                "Function4",
                "Function5",
                // Common exceptions / base classes
                "Throwable",
                "Exception",
                "Error",
                "Object",
            )

        if (name in ignoredTypes || name.startsWith("Function") || name.startsWith("KFunction")) {
            return false
        }

        val lower = name.lowercase()
        val ignoredPrefixes =
            listOf(
                "kotlin",
                "java",
                "scala",
                "groovy",
                "android",
                "sun",
                "com.sun",
                "jdk",
                "kotlinx",
            )

        return ignoredPrefixes.none { lower.startsWith(it) }
    }

    // ---- rendering helpers
    // ---------------------------------------------------------------------------------

    private fun renderBlock(
        lines: List<String>,
        alias: String,
        metadata: DeclarationMetadata? = null,
    ): String {
        val header = headerRegex.matchEntire(lines.first()) ?: return lines.joinToString("\n")
        val rawName = (header.groups["name"]!!.value + (header.groups["generics"]?.value ?: ""))
        val qualifiedDisplay =
            metadata?.enclosingQualifiedName?.substringAfterLast('.')?.let { "$it.$rawName" }
                ?: rawName
        val display =
            qualifiedDisplay
                .replace("\\n", " ")
                .replace("\u200B", "")
                .replace(Regex("\\s+"), " ")
                .trim()
                .let { if (metadata?.isObject == true) "<u>$it</u>" else it }

        val stereotypes = buildList {
            if (metadata?.isObject == true) add("object")
            if (metadata?.isSealed == true) add("sealed")
            if (header.groups["kw"]!!.value.endsWith("interface")) add("interface")
            // Include external or custom annotations as stereotypes if present
            metadata?.annotations?.forEach { add("@$it") }
        }

        val stereotypePrefix =
            stereotypes
                .filter { it.startsWith("@") }
                .takeUnless { it.isEmpty() }
                ?.joinToString(" ", prefix = "<i>", postfix = "</i>\\n") {
                    it
                } ?: ""

        val stereotypeSuffix =
            (stereotypes
                .filterNot { it == "object" }
                .filterNot { it.startsWith("@") }
                .takeUnless { it.isEmpty() }
                ?.joinToString(" ") {
                        "<<$it>>"
                } ?: "") + if (stereotypes.contains("object")) {
                "<< (O${if (!stereotypes.contains("sealed")) {",#FF7700"} else {""}}) object >>"
            } else {
                ""
            }

        val match = "(\\w+)\\s*(<\\w+(?:,\\s*\\w+)*>)".toRegex().find(display)
        val name =
            if (match != null) {
                "<b>${match.groupValues[1]}</b>${match.groupValues[2]}\""
            } else {
                display + '"'
            }
        val first =
            "${metadata?.visibility}${header.groups["kw"]!!.value} \"$stereotypePrefix$name as $alias $stereotypeSuffix"
        val members =
            lines.drop(1).map { line -> if (metadata?.isObject == true) markStatic(line) else line }
        if (members.filterNot { it.trim() == "}" }.isEmpty()) {return first + "\n"}
        return (listOf(first + " {") + members + "").joinToString("\n")
    }

    private fun markStatic(line: String): String {
        val member = Regex("""^(\s*[+~#-])(.*\S)\s*$""").matchEntire(line) ?: return line
        val content = member.groupValues[2]
        if (content.startsWith("{static} ") || content.endsWith(" {static}")) return line
        return member.groupValues[1] + content + " {static}"
    }

    /** Alias derived from the qualified name, unique by construction. */
    private fun aliasFor(qualifiedName: String, version: Int = 0): String {
        val base = qualifiedName.replace(Regex("\\W"), "_")
        return if (version == 0) base else "${base}_${version + 1}"
    }

    private fun displayName(packageName: String, rootPackage: String): String =
        packageName.removePrefix(rootPackage).removePrefix(".").ifEmpty {
            packageName.substringAfterLast('.')
        }
}
