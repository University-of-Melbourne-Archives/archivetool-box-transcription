package au.edu.unimelb.archivetool.config

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val API_KEY_ENV_VAR = "ARCHIVETOOL_API_KEY"

@Serializable
data class Config(
    @EncodeDefault @SerialName("ocr_api") val ocrApiSettings: OpenAIAPIData = OpenAIAPIData()
)

@Serializable
data class OpenAIAPIData(
    @EncodeDefault
    @SerialName("base_url")
    val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta/openai/",
    @EncodeDefault @SerialName("api_key") private val _apiKey: String = "",
    @EncodeDefault val model: String = "gemini-3-flash-preview",
) {
    val apiKey
        get() = System.getenv(API_KEY_ENV_VAR) ?: _apiKey
}
