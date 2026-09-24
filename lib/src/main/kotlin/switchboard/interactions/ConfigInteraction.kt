package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.config.Config
import au.edu.unimelb.archivetool.config.OpenAIAPIData
import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.os.Platform
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.FileOutputStream
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.notExists
import kotlin.io.path.outputStream
import kotlin.io.path.writeText
import kotlin.io.path.writer

class JSONConfigInteraction : ConfigInteraction {
    val json by lazy {
        Json { prettyPrint = true }
    }
    val filePath by lazy {
        Platform.configPath().resolve("config.json")
    }
    override fun getConfig(): Config {
        if (filePath.notExists()) {
            val hasApiKeyInEnv = System.getenv("ARCHIVETOOL_API_KEY") == null
            Log.error("Could not find config file!" +
                    if (hasApiKeyInEnv) {
                        "Without this, we will not be able to perform OCR.\n"
                    } else {""} +
                    "Writing a default configuration to $filePath.\n" +
                    if (hasApiKeyInEnv) {
                        "You will need to either set the \"ARCHIVETOOL_API_KEY\" environment variable,\n" +
                                " or include it as \"api_key\" in the configuration file above."
                    } else {""}
            )
            writeDefault()
        }

        return filePath.inputStream().use {
            @OptIn(ExperimentalSerializationApi::class)
            json.decodeFromStream<Config>(it)
        }
    }

    override fun writeDefault() {
        if (!filePath.notExists()) return

        filePath.outputStream().use {
            @OptIn(ExperimentalSerializationApi::class)
            json.encodeToStream(Config(), it)
        }
    }
}

class MockConfigInteraction : ConfigInteraction {
    override fun getConfig(): Config = Config(
        ocrApiSettings = OpenAIAPIData(
            apiKey = "not_a_real_key"
        )
    )

    override fun writeDefault() = Unit
}

interface ConfigInteraction : Interaction {
    fun getConfig(): Config
    fun writeDefault()

    // We want every file here.
    override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean = true
}
