import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.logging.consumers.StdoutConsumer
import io.github.kelvindev15.kotlin2plantuml.plantuml.ClassDiagram
import io.github.kelvindev15.kotlin2plantuml.plantuml.Configuration
import io.github.kelvindev15.kotlin2plantuml.utils.ReflectUtils.Companion.isInterface
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
import kotlinx.serialization.Serializable
import kotlin.collections.filter

fun main() {
    Log.registerConsumer(StdoutConsumer())

    val root = "au.edu.unimelb.archivetool"

    // Build a global map of SimpleName -> FullyQualifiedName from all scanned classes
    val allClasses = ClasspathScanner.findClasses(root, requireReflectable = true)
    val globalTypeMap = mutableMapOf<String, String>()
    val externalSupertypes =
        mutableMapOf<String, MutableSet<String>>() // childSimpleName -> parentNames
    val classAnnotations =
        mutableMapOf<String, MutableSet<String>>() // className -> annotationNames

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

    val runs =
        allClasses
            .groupBy { it.java.packageName }
            .toSortedMap()
            .mapNotNull { (pkg, classes) ->
                runCatching {
                        PackageRun(
                            packageName = pkg,
                            classNames = classes.map { it.normalisedQualifiedName() }.toSet(),
                            declarations =
                                classes.map { kClass ->
                                    val javaClass = kClass.java
                                    val enclosing = javaClass.enclosingClass
                                    DeclarationMetadata(
                                        qualifiedName = kClass.normalisedQualifiedName(),
                                        simpleName = javaClass.simpleName,
                                        enclosingQualifiedName = enclosing?.name?.replace('$', '.'),
                                        isObject =
                                            runCatching { kClass.objectInstance != null }
                                                .getOrDefault(false),
                                        isInner = kClass.isInner,
                                        companionMembers = kClass.synthesiseCompanionMembers(),
                                        companionAssociations =
                                            kClass.synthesiseCompanionSupertypes(),
                                        annotations =
                                            kClass.annotations.map {
                                                it.annotationClass.java.simpleName
                                            },
                                        isSerializable =
                                            kClass.annotations.any { it is Serializable },
                                        visibility = when (kClass.visibility) {
                                            KVisibility.PROTECTED -> "#"
                                            KVisibility.INTERNAL -> "~"
                                            KVisibility.PRIVATE -> "-"
                                            else -> ""
                                        }
                                    )
                                },
                            uml =
                                ClassDiagram(
                                        *classes.toTypedArray(),
                                        configuration =
                                            Configuration(
                                                false,
                                                false,
                                                false,
                                                recurse = true,
                                                KVisibility.PRIVATE,
                                                KVisibility.PRIVATE,
                                            ),
                                    )
                                    .plantUml(),
                        )
                    }
                    .onFailure { println("Skipping package $pkg: $it") }
                    .getOrNull()
            }

    val uml =
        UmlCombiner.combine(
            runs,
            rootPackage = root,
            globalTypeMap,
            externalSupertypes,
            classAnnotations,
        )

    val f = createTempDirectory().resolve("archivetool.plantuml")
    val basef = f.resolveSibling("archivetool.base.plantuml")
    basef.writeText(uml)

    println("Wrote ${runs.size} packages to $basef")

    println("Applying Manual Patches:")

    val patched = uml.let { uml ->
        val end = "@enduml"
        uml.lineSequence().filter { !it.contains(end) }
            .patchHideMethodUses().patchInteractionDI().patchLogConsumerDupeProps().patchMissingCLIInheritances().patchRemoveCompanionMemberProperty().patchAddConfigJson().patchAddUoMCSV().patchAddJobJson()
            .let { it + end }
            .joinToString("\n", postfix = "\n")
    }

    f.writeText(patched)
    println("Wrote patched plantuml to $f")
}

private fun KClass<*>.normalisedQualifiedName(): String =
    (qualifiedName ?: java.name).replace('$', '.')

private fun KClass<*>.synthesiseCompanionMembers(): List<String> {
    val companion = companionObject ?: return emptyList()
    return companion.declaredMembers
        .filterNot { it.isSyntheticMember() }
        .mapNotNull { member ->
            runCatching {
                    val line = member.toPlantUmlLine() ?: return@runCatching null
                    // Filter out serializer() method from companion members
                    if (line.contains("serializer()")) return@runCatching null
                    line
                }
                .getOrNull()
        }
        .distinct()
}

private fun KClass<*>.synthesiseCompanionSupertypes(): List<UmlCombiner.Relation> {
    val companion = companionObject ?: return emptyList()
    return companion.supertypes
        .map { it.classifier as KClass<*> }
        .map {
            UmlCombiner.Relation(
                parent = it.java.simpleName,
                child = this.java.simpleName,
                dashed = it.isInterface,
                label = "(via. companion)",
            )
        }
}

private fun Sequence<String>.patchInteractionDI(): Sequence<String> {
    println("- Replace aggregation of Interaction subclasses with injection of interaction.")
    val pat = "au_edu_unimelb_archivetool_switchboard_AbstractSwitchboard\"1\" --> \"1\" "
    return filter { !it.contains(pat) } + "au_edu_unimelb_archivetool_switchboard_AbstractSwitchboard #-- au_edu_unimelb_archivetool_switchboard_interactions_Interaction : < injected into"
}

private fun Sequence<String>.patchLogConsumerDupeProps(): Sequence<String> {
    println("- Remove duplicated properties on LogConsumers with properties on the superclass.")
    return filter {
        !(it.contains("LogLevel : maxLogLevel") || it.contains("ConsumerSettings : settings")) || it.contains("au_edu_unimelb_archivetool_util_logging_LogConsumer")
    }
}

/* Adds the following:

au_edu_unimelb_archivetool_app_cli_helpers_Command <|-- au_edu_unimelb_archivetool_app_cli_BaseCommand
au_edu_unimelb_archivetool_app_cli_helpers_Command <|-- au_edu_unimelb_archivetool_app_cli_FinaliseCommand
au_edu_unimelb_archivetool_app_cli_helpers_Command <|-- au_edu_unimelb_archivetool_app_cli_InfoCommand
au_edu_unimelb_archivetool_app_cli_helpers_Command <|-- au_edu_unimelb_archivetool_app_cli_OCRCommand
au_edu_unimelb_archivetool_app_cli_helpers_Command <|-- au_edu_unimelb_archivetool_app_cli_PreviewCommand
au_edu_unimelb_archivetool_app_cli_helpers_Command <|-- au_edu_unimelb_archivetool_app_cli_RunCommand
au_edu_unimelb_archivetool_app_cli_helpers_Command <|-- au_edu_unimelb_archivetool_app_cli_StatusCommand
au_edu_unimelb_archivetool_app_cli_helpers_Command <|-- au_edu_unimelb_archivetool_app_cli_ocr_OCRCreateCommand
au_edu_unimelb_archivetool_app_cli_helpers_Command <|-- au_edu_unimelb_archivetool_app_cli_ocr_OCRReviewCommand
au_edu_unimelb_archivetool_app_cli_helpers_Command <|-- au_edu_unimelb_archivetool_app_cli_preview_PreviewCreateCommand
au_edu_unimelb_archivetool_app_cli_helpers_Command <|-- au_edu_unimelb_archivetool_app_cli_preview_PreviewReviewCommand

I genuinely have no clue why these aren't detected but it may be something to do with excluding random stuff (including this UML gen) from the diagram
*/
private fun Sequence<String>.patchMissingCLIInheritances(): Sequence<String> {
    println("- Remove inherited methods from CLI and add missing inheritances.")
    return this.filterNot { it.contains("+run()") || it.contains("+help(context: Context): String") } + sequenceOf(
        "BaseCommand",
        "FinaliseCommand",
        "InfoCommand",
        "OCRCommand",
        "PreviewCommand",
        "RunCommand",
        "StatusCommand",
        "ocr_OCRCreateCommand",
        "ocr_OCRReviewCommand",
        "preview_PreviewCreateCommand",
        "preview_PreviewReviewCommand",
    ).map {
        "au_edu_unimelb_archivetool_app_cli_helpers_Command <|-- au_edu_unimelb_archivetool_app_cli_$it"
    }
}

//todo do we have tthe jobstateinteraction inheritance borken???
private fun Sequence<String>.patchRemoveCompanionMemberProperty(): Sequence<String> {
    println("- Remove 'companion' member from JobStateInteraction and subtypes.")
    return this.filterNot { it.contains(": companion") }
}

private fun Sequence<String>.patchHideMethodUses(): Sequence<String> {
    println("- Hide the 'uses' method associations that I am no longer using.")
    val useLines = this.filter { it.endsWith(" : uses") }
    val deadExts = useLines.filter { it.contains("ext_") }.flatMap {
        "(ext_(?:[_]|[a-z]|[A-Z])+) : uses".toRegex()
            .findAll(it)
            .mapNotNull { match -> match.groupValues.getOrNull(1) }
            .filterNot { alias -> this.any { line -> line.startsWith("$alias <|-- ") } }
    }.distinct()
    return this.filterNot { line -> useLines.contains(line) || deadExts.any { line.contains(it) } }
}

private fun Sequence<String>.patchAddConfigJson(): Sequence<String> {
    println("- Add a relationship for Config reading the json file, and between Config and ConfigInteraction")
    val jsonShape = buildString {
        appendLine("json \".config/archivetool/config.json\" as config_json {")
        appendLine("\t\"ocr_api\": {")
        appendLine("\t\"base_url\": \"https://generativelanguage.googleapis.com/v1beta/openai/\",")
        appendLine("\t\"api_key\": \"\",")
        appendLine("\t\"model\": \"gemini-3-flash-preview\",")
		appendLine("\t\"effort\": \"low\"")
        appendLine("\t}")
        appendLine("}")
    }
    val assocs = sequenceOf(
        "au_edu_unimelb_archivetool_config_Config <-- au_edu_unimelb_archivetool_switchboard_interactions_ConfigInteraction : reads",
        "config_json <-- au_edu_unimelb_archivetool_config_Config : reads/generates",
        "au_edu_unimelb_archivetool_switchboard_interactions_InitInteraction <-- au_edu_unimelb_archivetool_switchboard_interactions_ConfigInteraction : generates"
    )
    val note = buildString {
        appendLine("note top of config_json")
        appendLine("Approximately represents the arguments given to an Open-AI compatible API.")
        appendLine("end note")
    }
    return this + jsonShape + assocs + note
}
private fun Sequence<String>.patchAddUoMCSV(): Sequence<String> {
    println("- Add Table representing CSV output")
    val table = buildString {
        appendLine("object \"\" as output_csv {")
        appendLine("= \"\"output.csv\"\"\n")
        appendLine("<#transparent,#black>|= Node ID |= Node Type |= Node Title |= Description |= Date Range|")
        appendLine("<#transparent,#black>| ????? | Item | \"\"<id number> Unit <unit number>\"\" | \"\"<remaining transcription>\"\" | \"\"<year range>;<start date>;<end date>\"\"|")
        appendLine("}")
    }
    val assoc = "output_csv <-- au_edu_unimelb_archivetool_switchboard_interactions_export_UoMCSV : generates"

    return this.filterNot { it.contains("au_edu_unimelb_archivetool_switchboard_interactions_export_CSV_Companion o-- ") } + table + assoc
}

private fun Sequence<String>.patchAddJobJson(): Sequence<String> {
    println("- Add a relationship for job state management")
    val jsonShape = buildString {
        appendLine("json \".archivetool_generated/job.json\" as job_json {")
        val metaJson = """
        {
            "id[]?": ["string"],
            "unitNumber?": "number",
            "dateRange?": {
                "startDate": "ISO8601 date string",
                "endDate?": "ISO8601 date string"
            },
            "description": "string"
        }
        """.trimIndent()
        val boundaryJson = """
            {
                "points[4]": [
                    {"x": "number", "y": "number"}
                ],
                "confidence": "number",
                "rawMetadata?": $metaJson
            }
        """.trimIndent()
        appendLine("""
        "<path>*": {
            "vertexes?": {
                "<box-id>*": {
                    "box": $boundaryJson,
                    "sticker[]": [$boundaryJson],
                    "meta?": $metaJson
                }
            }
        }
        """.trimIndent())
        appendLine("}")
    }
    val assocs = sequenceOf(
        "job_json <-- au_edu_unimelb_archivetool_switchboard_jobstate_json_JSONJobState : reads/generates",
    )
    val note = buildString {
        appendLine("note top of job_json")
        appendLine("Internal representation of processing results and state.")
        appendLine("end note")
    }
    return this + jsonShape + assocs + note
}

/**
 * `KCallable` has no `isSynthetic`. The backing Java member does, and kotlin-reflect exposes it
 * through `javaField` / `javaMethod` / `javaConstructor`. Any of those that is a
 * `java.lang.reflect.Member` carries the flag.
 */
private fun KCallable<*>.isSyntheticMember(): Boolean {
    val member: java.lang.reflect.Member? =
        when (this) {
            is KProperty<*> -> runCatching { javaField }.getOrNull()
            is KFunction<*> -> runCatching { javaMethod }.getOrNull()
            else -> null
        }
    return member?.isSynthetic ?: false
}

private fun KCallable<*>.toPlantUmlLine(): String? {
    val visibility =
        when (visibility) {
            KVisibility.PUBLIC -> "+"
            KVisibility.PROTECTED -> "#"
            KVisibility.INTERNAL -> "~"
            KVisibility.PRIVATE -> "-"
            null -> "+"
        }
    val name = name
    return when (this) {
        is KProperty<*> -> {
            val isConstant =
                isFinal && (this.visibility == KVisibility.PUBLIC) // heuristic for constants
            val prefix = if (isConstant) "\t$visibility{static} const " else "$visibility"
            val type = returnType.jvmErasure.simpleName ?: return null
            "$prefix$name: $type"
        }
        is KFunction<*> -> {
            // Drop the instance / extension-receiver slots; keep declared parameters only.
            val params =
                parameters
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
