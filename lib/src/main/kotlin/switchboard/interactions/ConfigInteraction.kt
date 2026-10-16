package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.config.API_KEY_ENV_VAR
import au.edu.unimelb.archivetool.config.Config
import au.edu.unimelb.archivetool.config.OpenAIAPIData
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.logging.LogConsumer
import au.edu.unimelb.archivetool.util.os.Platform
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.notExists
import kotlin.io.path.outputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

class JSONConfigInteraction : ConfigInteraction {
    val json by lazy { Json { prettyPrint = true } }
    val filePath by lazy { Platform.configPath().resolve("config.json") }

    override fun getConfig(): Config {
        writeDefault()

        return filePath.inputStream().use {
            @OptIn(ExperimentalSerializationApi::class) json.decodeFromStream<Config>(it)
        }
    }

    override fun writeDefault() {
        if (filePath.exists()) return

        val hasNoApiKeyInEnv = System.getenv(API_KEY_ENV_VAR) == null

        Log.error(
            "Could not find config file! Without this, we will not be able to perform OCR."
        )
        filePath.outputStream().use {
            @OptIn(ExperimentalSerializationApi::class) json.encodeToStream(Config(), it)
        }
        Log.info(
            "Writing a default configuration to $filePath.\n" +
            "You will need to either ${
                if (hasNoApiKeyInEnv) {
                    "set"
                } else {
                    "continue using"
                }
            } the \"ARCHIVETOOL_API_KEY\" environment variable, or include it as \"api_key\" in the configuration file above."
        )
    }

    override fun createInteraction(): InitInteraction = CreateInteraction

    object CreateInteraction : InitInteraction {
        override fun init(logConsumers: Set<LogConsumer>) = JSONConfigInteraction().writeDefault()

        // This is an interaction that doesn't need files.
        override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean = false
    }
}

class MockConfigInteraction : ConfigInteraction {
    override fun getConfig(): Config =
        Config(ocrApiSettings = OpenAIAPIData(_apiKey = "not_a_real_key"))

    override fun writeDefault() = Unit
}

interface ConfigInteraction : Interaction {
    fun getConfig(): Config

    fun writeDefault()

    fun createInteraction(): InitInteraction? = null

    // We want every file here.
    override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean = true
}
