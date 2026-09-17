package au.edu.unimelb.archivetool.switchboard.jobstate.json

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import au.edu.unimelb.archivetool.switchboard.jobstate.JobStateVariant
import au.edu.unimelb.archivetool.util.map
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class JSONJobState private constructor(val directory: Path, fmap: MutableMap<Path, JobFile?>) :
    JobState(fmap) {
    constructor(dir: Path, el: JsonElement) : this(dir, mutableMapOf()) {
        val obj: JsonElement
        when (el) {
            !is JsonObject -> {
                Log.error("Job State File is malformed.")
                throw RuntimeException()
            }
            else -> {
                obj = el
            }
        }
        this.files =
            mutableMapOf(
                *obj.entries
                    .map { entry ->
                        entry
                            .toPair()
                            .map(
                                { Path(it) },
                                {
                                    when (it) {
                                        is JsonNull -> null
                                        else -> Json.decodeFromJsonElement<JobFile>(it)
                                    }
                                },
                            )
                    }
                    .toTypedArray()
            )
        scan(dir)
    }

    constructor(dir: Path) : this(dir, mutableMapOf()) {
        scan(dir, initial = true)
    }

    override val variant = JobStateVariant.JSON

    fun toJsonElement(): JsonElement {
        return JsonObject(
            files
                .mapKeys { it.key.pathString }
                .mapValues {
                    when (it.value) {
                        null -> JsonNull
                        else -> Json.encodeToJsonElement(it.value!!)
                    }
                }
        )
    }
}
