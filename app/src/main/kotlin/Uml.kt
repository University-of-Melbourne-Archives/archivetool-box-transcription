package au.edu.unimelb.archivetool.app

import au.edu.unimelb.archivetool.util.Git
import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.logging.consumers.StdoutConsumer
import io.github.kelvindev15.kotlin2plantuml.plantuml.ClassDiagram
import io.github.kelvindev15.kotlin2plantuml.plantuml.Configuration
import io.github.kelvindev15.kotlin2plantuml.utils.ReflectUtils.Companion.isInterface
import kotlin.collections.filter
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
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

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
                                        visibility =
                                            when (kClass.visibility) {
                                                KVisibility.PROTECTED -> "#"
                                                KVisibility.INTERNAL -> "~"
                                                KVisibility.PRIVATE -> "-"
                                                else -> ""
                                            },
                                        isSealed = kClass.isSealed,
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
        uml.lineSequence()
            .filter { !it.contains(end) }
            .patchHideMethodUses()
            .patchInteractionDI()
            .patchLogConsumerDupeProps()
            .patchMissingInheritances()
            .patchRemoveCompanionMemberProperty()
            .patchJobStateVariantToEnums()
            .patchClarifyFunctionMethodParameters()
            .patchAddConfigJson()
            .patchAddUoMCSV()
            .patchAddJobJson()
            .patchNestedRelationships()
            .patchGroupSerializationSurrogates()
            .patchAddLegend()
            .patchAddWatermark()
            .let { it + "remove \$hide" + end }
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
    return filter { !it.contains(pat) } +
        "au_edu_unimelb_archivetool_switchboard_AbstractSwitchboard #-- au_edu_unimelb_archivetool_switchboard_interactions_Interaction : < injected into"
}

private fun Sequence<String>.patchStrayChars(): Sequence<String> {
    println("- Remove stray */.")
    return map { it.replace("*/", "") }
}

private fun Sequence<String>.patchLogConsumerDupeProps(): Sequence<String> {
    println("- Remove duplicated properties on LogConsumers with properties on the superclass.")
    return filter {
        !(it.contains("LogLevel : maxLogLevel") || it.contains("ConsumerSettings : settings")) ||
            it.contains("au_edu_unimelb_archivetool_util_logging_LogConsumer")
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
private fun Sequence<String>.patchMissingInheritances(): Sequence<String> {
    println("- Remove inherited methods and add missing inheritances.")
    return this.filterNot {
        it.contains("+run()") || it.contains("+help(context: Context): String")
    } +
        (sequenceOf(
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
            )
            .map {
                "au_edu_unimelb_archivetool_app_cli_helpers_Command <|-- au_edu_unimelb_archivetool_app_cli_$it"
            } +
            sequenceOf("_memory_MemoryJobStateInteraction", "_json_JSONJobStateInteraction").map {
                "au_edu_unimelb_archivetool_switchboard_interactions_JobStateInteraction <|.. au_edu_unimelb_archivetool_switchboard_jobstate$it "
            } +
            "au_edu_unimelb_archivetool_ocr_OCRService\"1\" --> \"0..*\"au_edu_unimelb_archivetool_ocr_OCRResult : Produces" +
            "au_edu_unimelb_archivetool_switchboard_jobstate_JobState\"0..*\" <-- \"1\" au_edu_unimelb_archivetool_switchboard_jobstate_JobStateCache : caches")
}

// todo do we have tthe jobstateinteraction inheritance borken???
private fun Sequence<String>.patchRemoveCompanionMemberProperty(): Sequence<String> {
    println("- Remove 'companion' member from JobStateInteraction and subtypes.")
    return this.filterNot { it.contains(": companion") }
}

private fun Sequence<String>.patchHideMethodUses(): Sequence<String> {
    println("- Hide the 'uses' method associations that I am no longer using.")
    val useLines = this.filter { it.endsWith(" : uses") }
    val deadExts =
        useLines
            .filter { it.contains("ext_") }
            .flatMap {
                "(ext_(?:[_]|[a-z]|[A-Z])+) : uses"
                    .toRegex()
                    .findAll(it)
                    .mapNotNull { match -> match.groupValues.getOrNull(1) }
                    .filterNot { alias -> this.any { line -> line.startsWith("$alias <|-- ") } }
            }
            .distinct()
    return this.filterNot { line -> useLines.contains(line) || deadExts.any { line.contains(it) } }
}

private fun Sequence<String>.patchAddConfigJson(): Sequence<String> {
    println(
        "- Add a relationship for Config reading the json file, and between Config and ConfigInteraction"
    )
    val jsonShape = buildString {
        appendLine("json \".config/archivetool/config.json\" as config_json {")
        appendLine("\t\"ocr_api\": {")
        appendLine("\t\"base_url\": \"https://generativelanguage.googleapis.com/v1beta/openai/\",")
        appendLine("\t\"api_key\": \"null\",")
        appendLine("\t\"model\": \"gemini-3-flash-preview\",")
        appendLine("\t\"effort\": \"low\"")
        appendLine("\t}")
        appendLine("}")
    }
    val assocs =
        sequenceOf(
            "au_edu_unimelb_archivetool_config_Config <-- au_edu_unimelb_archivetool_switchboard_interactions_ConfigInteraction : reads",
            "config_json <-- au_edu_unimelb_archivetool_config_Config : reads/generates",
            "au_edu_unimelb_archivetool_switchboard_interactions_InitInteraction <-- au_edu_unimelb_archivetool_switchboard_interactions_ConfigInteraction : generates",
        )
    val note = buildString {
        appendLine("note top of config_json")
        appendLine("Approximately represents the arguments given to an Open-AI compatible API.")
        appendLine("end note")
    }
    return this + "package \"config\" {" + jsonShape + assocs + note + "}"
}

private fun Sequence<String>.patchAddUoMCSV(): Sequence<String> {
    println("- Add Table representing CSV output")
    val table = buildString {
        appendLine("object \"\" as output_csv {")
        appendLine("= \"\"output.csv\"\"\n")
        appendLine(
            "<#transparent,#black>|= Node ID |= Node Type |= Node Title |= Description |= Date Range|"
        )
        appendLine(
            "<#transparent,#black>| ????? | Item | \"\"<id number> Unit <unit number>\"\" | \"\"<remaining transcription>\"\" | \"\"<year range>;<start date>;<end date>\"\"|"
        )
        appendLine("}")
    }
    val assoc =
        "output_csv <-- au_edu_unimelb_archivetool_switchboard_interactions_export_UoMCSV : generates"

    return this.filterNot {
        it.contains("au_edu_unimelb_archivetool_switchboard_interactions_export_CSV_Companion +.. ")
    } +
        "au_edu_unimelb_archivetool_switchboard_interactions_export_CSV +.. au_edu_unimelb_archivetool_switchboard_interactions_export_UoMCSV" +
        "package \"switchboard.interactions.export\" {" +
        table +
        assoc +
        "}"
}

private fun Sequence<String>.patchAddJobJson(): Sequence<String> {
    println("- Add a relationship for job state management")
    val jsonShape = buildString {
        appendLine("json \".archivetool_generated/job.json\" as job_json {")
        val metaJson =
            """
            {
                "id[*]?": ["string"],
                "unitNumber?": "number",
                "dateRange?": {
                    "startDate": "ISO8601 date string",
                    "endDate?": "ISO8601 date string"
                },
                "description": "string"
            }
            """
                .trimIndent()
        val boundaryJson =
            """
            {
                "points[4]": [
                    {"x": "number", "y": "number"}
                ],
                "confidence": "number",
                "rawMetadata?": $metaJson
            }
        """
                .trimIndent()
        appendLine(
            """
        "<path>[*]": {
            "vertexes?": {
                "<box-id>[*]": {
                    "box": $boundaryJson,
                    "sticker[*]": [$boundaryJson],
                    "meta?": $metaJson
                }
            }
        }
        """
                .trimIndent()
        )
        appendLine("}")
    }
    val assocs =
        sequenceOf(
            "job_json <-- au_edu_unimelb_archivetool_switchboard_jobstate_json_JSONJobState : reads/generates"
        )
    val note = buildString {
        appendLine("note top of job_json")
        appendLine("Internal representation of processing results and state.")
        appendLine("end note")
    }
    return this + "package \"switchboard.jobstate.json\" {" + jsonShape + assocs + note + "}"
}

private fun Sequence<String>.patchJobStateVariantToEnums(): Sequence<String> {
    println("- Replace Sealed `JobStateVariant` with an enum")

    val substrings =
        setOf(
            "interface \"JobStateVariant<T : JobState>\" as au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant <<sealed>> <<interface>>",
            "class \"<u>JobStateVariant.JSON</u>\" as au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant_JSON << (O,#FF7700) object >>",
            "au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant +.. au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant_JSON",
            "class \"<u>JobStateVariant.Memory</u>\" as au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant_Memory << (O,#FF7700) object >>",
            "au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant +.. au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant_Memory",
            "au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant <|.. au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant_JSON",
            "au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant <|.. au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant_Memory",
            "au_edu_unimelb_archivetool_switchboard_jobstate_JobState [variant: JobStateVariant<*>]  --> \"1\" au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant : variant", // NOTE: We dont return this one because ti doesnt properly link to the new enum
            "au_edu_unimelb_archivetool_switchboard_jobstate_json_JSONJobState [variant: JSON]  --> \"1\" au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant_JSON : variant",
            "au_edu_unimelb_archivetool_switchboard_jobstate_json_JSONJobStateInteraction [variant: JSON]  --> \"1\" au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant_JSON : variant",
            "au_edu_unimelb_archivetool_switchboard_jobstate_memory_ConcreteJobState [variant: Memory]  --> \"1\" au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant_Memory : variant",
            "au_edu_unimelb_archivetool_switchboard_jobstate_memory_MemoryJobStateInteraction [variant: Memory]  --> \"1\" au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant_Memory : variant",
        )

    val newStrings =
        sequenceOf(
            "package switchboard.jobstate {",
            "\tenum \"JobStateVariant<T: JobState>\" as au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant {",
            "\t\tJSON",
            "\t\tMemory",
            "\t}",
            // NOTE: I would love to use ::JSON and ::Memory to link directly to the right member
            // but that doesnt work properly lol
            "au_edu_unimelb_archivetool_switchboard_jobstate_json_JSONJobState  --> au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant::JSON : \"variant = JSON\"",
            "au_edu_unimelb_archivetool_switchboard_jobstate_json_JSONJobStateInteraction  --> au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant::JSON : \"variant = JSON\"",
            "au_edu_unimelb_archivetool_switchboard_jobstate_memory_ConcreteJobState --> au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant::Memory : \"variant = Memory\"",
            "au_edu_unimelb_archivetool_switchboard_jobstate_memory_MemoryJobStateInteraction --> au_edu_unimelb_archivetool_switchboard_jobstate_JobStateVariant::Memory : \"variant = Memory\"",
            "}",
        )

    return this.filterNot { line -> substrings.any { line.contains(it) } } + newStrings
}

private fun Sequence<String>.patchNestedRelationships(): Sequence<String> {
    println("- Add additional relationships to better classify nested archetypes")
    val clarifications =
        sequenceOf(
            "au_edu_unimelb_archivetool_util_ProgressBar <--{ au_edu_unimelb_archivetool_util_ProgressBar_ProgressBarScope : < controls",
            "au_edu_unimelb_archivetool_segmentation_BoxSegmentation_Model -right-> au_edu_unimelb_archivetool_segmentation_BoxSegmentation_Outputs : produces",
            "au_edu_unimelb_archivetool_segmentation_BoxSegmentation_Letterboxed -right-> au_edu_unimelb_archivetool_segmentation_BoxSegmentation_Model : processed by",
            "au_edu_unimelb_archivetool_segmentation_BoxSegmentation_Outputs\"1\" -right-> \"0..*\" au_edu_unimelb_archivetool_segmentation_BoxSegmentation_Detection : decodes into",
            "au_edu_unimelb_archivetool_switchboard_interactions_JSONConfigInteraction --> au_edu_unimelb_archivetool_switchboard_interactions_JSONConfigInteraction_CreateInteraction : generates",
            "au_edu_unimelb_archivetool_switchboard_interactions_JSONConfigInteraction_CreateInteraction ---- au_edu_unimelb_archivetool_config_Config  : ensures presence",
            "au_edu_unimelb_archivetool_switchboard_interactions_KotlinOCRInteraction \"1\" --> \"1\" au_edu_unimelb_archivetool_ocr_OCRService",
            "package ocr {\n(au_edu_unimelb_archivetool_switchboard_interactions_KotlinOCRInteraction, au_edu_unimelb_archivetool_ocr_OCRService) . au_edu_unimelb_archivetool_switchboard_interactions_KotlinOCRInteraction_ServiceContainer\n}",
            "package util.concurrency{\n(au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockGuard, au_edu_unimelb_archivetool_util_Ref) - au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockTryGuard_InnerDelegate\n (au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockTryGuard, au_edu_unimelb_archivetool_util_Ref) - au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockTryGuard_InnerDelegate_2\n}",
        )

    val replacements =
        mapOf(
            "au_edu_unimelb_archivetool_util_concurrency_RWLock +.. au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockGuard" to
                "au_edu_unimelb_archivetool_util_concurrency_RWLock +-- au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockGuard",
            "au_edu_unimelb_archivetool_util_concurrency_RWLock +.. au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockReadGuard" to
                "au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockGuard +-- au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockReadGuard",
            "au_edu_unimelb_archivetool_util_concurrency_RWLock +.. au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockWriteGuard" to
                "au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockGuard +-- au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockWriteGuard",
            "au_edu_unimelb_archivetool_util_concurrency_RWLock +.. au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockTryGuard" to
                "au_edu_unimelb_archivetool_util_concurrency_RWLock +-- au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockTryGuard",
            "au_edu_unimelb_archivetool_util_concurrency_RWLock +.. au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockTryReadGuard" to
                "au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockTryGuard +-- au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockTryReadGuard",
            "au_edu_unimelb_archivetool_util_concurrency_RWLock +.. au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockTryWriteGuard" to
                "au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockTryGuard +-- au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockTryWriteGuard",
            "au_edu_unimelb_archivetool_switchboard_interactions_KotlinOCRInteraction +.. au_edu_unimelb_archivetool_switchboard_interactions_KotlinOCRInteraction_ServiceContainer" to
                "au_edu_unimelb_archivetool_switchboard_interactions_KotlinOCRInteraction +--- au_edu_unimelb_archivetool_switchboard_interactions_KotlinOCRInteraction_ServiceContainer",
            "au_edu_unimelb_archivetool_switchboard_interactions_KotlinOCRInteraction +.. au_edu_unimelb_archivetool_switchboard_interactions_KotlinOCRInteraction_ServiceContainer" to
                "au_edu_unimelb_archivetool_switchboard_interactions_KotlinOCRInteraction +--- au_edu_unimelb_archivetool_switchboard_interactions_KotlinOCRInteraction_ServiceContainer",
            "au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockTryGuard +.. au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockTryGuard_InnerDelegate" to
                "au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockGuard +.. au_edu_unimelb_archivetool_util_concurrency_RWLock_RWLockTryGuard_InnerDelegate",
            "au_edu_unimelb_archivetool_util_file_Image +.. au_edu_unimelb_archivetool_util_file_Image_FileType" to
                "au_edu_unimelb_archivetool_util_file_Image +-- au_edu_unimelb_archivetool_util_file_Image_FileType",
            "au_edu_unimelb_archivetool_util_wrap_CV_Contours +.. au_edu_unimelb_archivetool_util_wrap_CV_Contours_Node" to
                "au_edu_unimelb_archivetool_util_wrap_CV_Contours +-- au_edu_unimelb_archivetool_util_wrap_CV_Contours_Node",
        )
    return this.filterNot {
            it.contains(
                "au_edu_unimelb_archivetool_switchboard_interactions_KotlinOCRInteraction_ServiceContainer [inner: OCRService]"
            ) ||
                it.contains(
                    "au_edu_unimelb_archivetool_switchboard_interactions_KotlinOCRInteraction [container: ServiceContainer]"
                )
        }
        .map { line ->
            replacements.keys
                .find { line.contains(it) }
                ?.let { replace -> replace to replacements[replace]!! }
                ?.let { (replace, replaceWith) -> line.replace(replace, replaceWith) } ?: line
        } + clarifications
}

private fun Sequence<String>.patchAddLegend(): Sequence<String> {
    println("- Generate a Legend for the Diagram")
    val legend =
        """
        legend left
        {{
          !pragma layout smetana
          allowmixing
          hide empty members
          skinparam class {
            BackgroundColor<<sealed>> Moccasin
          }
          skinparam node {
            BackgroundColor #AAAAAA
          }
          
          title Legend
          center footer 
          <i>Anything not listed here can be assumed to be equivalent to a standard UML Class Diagram</i>
          end footer

          class Derived
          class Base
          Derived -right-|> Base : extends

          class Impl
          interface Iface
          Impl .right.|> Iface : implements

          class CImpl
          CImpl .left.|> Iface : (via. companion)
          CImpl -left-|> Base : (via. companion)
          note right of CImpl : This class's companion object implements the interface, not the class itself.

          class Outer
          class Inner
          Outer +-right- Inner : nested inside
          note right of Inner : A Plus like this implies nesting.\n A dashed line has no further implication, where a solid line implies that Inner is nested and depends on Outer.\nThis is similar to Aggregation vs. Composition

          class Owner
          class One
          class Many
          Owner -right-> "1" One : property
          Owner -right-> "0..*" Many : property
          note right of Many : Numbers show how many values the\nproperty holds. A Map links to both\nits key type and its value type.

          class Host
          class Injected
          Host #-right- Injected : < injected into

          class Controlled
          class Controller
          Controlled <-right-{ Controller : < controls

          class P
          class Q
          class Assoc
          P -right-> Q
          (P, Q) .down. Assoc
          note right of Assoc : A helper tied to the link between P and Q.\nUsed for Delegates and Serializers.

          class Left
          class Right
          Left -right-> Right : any other text
          note right of Right : Arrows with free text and no multiplicity are\ndescribed by that text.

          class "Sealed" as Sealed <<sealed>>
          note top of Sealed : Orange: ""sealed"". Every subtype is declared within the same package.\nOften used for Tagged Unions and similar

          class "<u>Singleton</u>" as Singleton << (O,#FF7700) object >>
          note top of Singleton : A Kotlin ""object"". Acts as a Singleton Instance of a class.\nSometimes used to group member classes/functions into a "namespace".

          class "<i>@Serializable</i>\nAnnotated" as Annotated {
            +callback: () -> Unit {field}
            +items: List
            +{static} const LIMIT: Int
            +helper() {static}
          }
          note bottom of Annotated : - Italic text above the name: an annotation applied to the class.\n- const means a fixed constant.\n- Type arguments on members are sometimes left out by the generator, and I am too lazy to put them back manually. (<i>eg. List rather than List of Strings</i>).\n- Static Items represent members of the Companion Object, since traditional static members do not exist in Kotlin.

          package "Package Visibility" <<Rectangle>> {
              -class "Private" as PrivateNested
              ~class "Internal" as InternalNested
              #class "Protected" as ProtectedNested
              note right of ProtectedNested : Nested types can have a visibility. 
              
              PrivateNested -[hidden]right-> InternalNested
              InternalNested -[hidden]right-> ProtectedNested
          }

          package "External" <<Node>> {
          class "lib.SomeType" as Ext <<external>>
          note right of Ext : Defined in a library, not in this project.\nLimited to one level of Annotation or Inheritance.
          }

          Derived -[hidden]down-> Impl
          Impl -[hidden]right-> CImpl
          Impl -[hidden]down-> Outer
          Outer -[hidden]down-> Owner
          Owner -[hidden]down-> Host
          Injected -[hidden]right-> Controlled
          Controlled -[hidden]down-> P
          P -[hidden]left-> Right
          Annotated -[hidden]right-> Singleton
          Singleton -[hidden]right-> Sealed
          Controller ---[hidden]right---> Ext
        }}
        end legend
        """
            .trimIndent()
    return this + legend
}

private fun Sequence<String>.patchClarifyFunctionMethodParameters(): Sequence<String> {
    println("- Replace certain method parameters of type `FunctionN` with their actual type.")

    val methods =
        mapOf(
            // Methods with FunctionN where the generics need to be added manually.
            "+withProgressBar(total: Int, implementation: ProgressBar, operation: Function1): Unit {static}" to
                "+withProgressBar(total: Int, implementation: ProgressBar, operation: ProgressBarScope.() -> Unit): Unit {static}",
            "+validFilesChunkedWithProgressBar(type: Interaction, nullsAreValid: Boolean, runInteraction: Function2)" to
                "+validFilesChunkedWithProgressBar<T: Interaction>(type: Interaction, nullsAreValid: Boolean, runInteraction: (T, Set<Pair<JobRelativePath, JobFile>>) -> Map<JobRelativePath, JobFile>)",
            "+validFilesSingleWithProgressBar(type: Interaction, nullsAreValid: Boolean, runInteraction: Function2)" to
                "+validFilesSingleWithProgressBar<T: Interaction>(type: Interaction, nullsAreValid: Boolean, runInteraction: (T, Pair<JobRelativePath, JobFile>) -> Map<JobRelativePath, JobFile>)",
            "+blankLabels(img: Mat, labels: List, transform: Function1): Mat {static}" to
                "+blankLabels(img: Mat, labels: List, transform: Quad.() -> Quad): Mat {static}",
            "+getOrTry(idx: Path, or: Function0): JobState" to
                "+getOrTry(idx: Path, or: () -> S?): JobState",
            "+run(operation: Function1)" to "+run(operation: ProgressBarScope.() -> Unit)",
            "+withReadLock(body: Function2): Any" to "+withReadLock<R>(body: suspend (T) -> R): R",
            "+withWriteLock(body: Function2): Any" to
                "+withWriteLock<R>(body: suspend (T) -> R): R",
            "+use(block: Function2): Any" to "+use(block: suspend (this) -> R): R",
            "+use(block: Function1): Any" to "+use(block: (this) -> R): R",
            "+mapPoints(mapper: Function1):" to "+mapPoints(mapper: (Point) -> Point):",

            // These two are private so IDGAF losing them from the diagram
            "-readAction(action: Function2): Any" to "",
            "-writeAction(action: Function2): Any" to "",

            // Lambda Fields where it got something wrong.
            "#{abstract} unlock: (Unit) -> Unit {field}" to
                "#{abstract} unlock: suspend () -> Unit {field}",
            "#unlock: (Unit) -> Unit {field}" to "#unlock: suspend () -> Unit {field}",
            "+readLock(): (Unit) -> Unit {field}" to "+readLock(): suspend () -> Unit {field}",
            "+writeLock(): (Unit) -> Unit {field}" to "+writeLock(): suspend () -> Unit {field}",
            "+constructor: (Point) -> Point, T> {field}" to
                "+constructor: (List<Point>) -> T {field}",

            // These classes are full of broken lambdas and are not exactly pivotal to function so I
            // just nuke them too
            "class \"<u>PairExt</u>\" as au_edu_unimelb_archivetool_util_extension_PairExt << (O,#FF7700) object >>" to
                "class \"<u>PairExt</u>\" as au_edu_unimelb_archivetool_util_extension_PairExt << (O,#FF7700) object >> \$hide",
            "class \"<u>TripleExt</u>\" as au_edu_unimelb_archivetool_util_extension_TripleExt << (O,#FF7700) object >>" to
                "class \"<u>TripleExt</u>\" as au_edu_unimelb_archivetool_util_extension_TripleExt << (O,#FF7700) object >> \$hide",
            "package util.extension " to "package util.extension \$hide ",
        )

    return this.map { line ->
        methods.keys
            .find { line.contains(it) }
            ?.let { replace -> replace to methods[replace]!! }
            ?.let { (replace, replaceWith) -> line.replace(replace, replaceWith) } ?: line
    }
}

private fun Sequence<String>.patchGroupSerializationSurrogates(): Sequence<String> {
    println("- Group Together Classes with their serializers and surrogates.")
    return this +
        sequenceOf(
            "au_edu_unimelb_archivetool_util_DateRange <- au_edu_unimelb_archivetool_util_DateRangeSerializer : serializes",
            "(au_edu_unimelb_archivetool_util_DateRange, au_edu_unimelb_archivetool_util_DateRangeSerializer) .. au_edu_unimelb_archivetool_util_DateRangeSurrogate",
            "au_edu_unimelb_archivetool_util_math_geometry_Point - au_edu_unimelb_archivetool_util_math_geometry_PointSerializer : serializes",
            "(au_edu_unimelb_archivetool_util_math_geometry_Point, au_edu_unimelb_archivetool_util_math_geometry_PointSerializer) .. au_edu_unimelb_archivetool_util_math_geometry_PointSurrogate",
            "au_edu_unimelb_archivetool_util_math_geometry_Quad - au_edu_unimelb_archivetool_util_math_geometry_QuadSerializer : serializes",
            "au_edu_unimelb_archivetool_util_math_geometry_Rectangle - au_edu_unimelb_archivetool_util_math_geometry_RectangleSerializer : serializes",
        )
}

private fun Sequence<String>.patchAddWatermark(): Sequence<String> {
    println("- Add a \"Generated By:\" Watermark.")
    return this +
        sequenceOf(
            "right footer Generated by: <i>archivetool/app/src/main/kotlin/Uml.kt</i> at ${Clock.System.now()} from commit ${Git.HASH}"
        )
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
