package au.edu.unimelb.archivetool.config

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config (
    @EncodeDefault @SerialName("ocr_api") val ocrApiSettings: OpenAIAPIData = OpenAIAPIData()
)

@Serializable
data class OpenAIAPIData (
    @EncodeDefault @SerialName("base_url") val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta/openai/",
    @EncodeDefault @SerialName("api_key") val apiKey: String = "",
    @EncodeDefault val model: String = "gemini-3-flash-preview",
)
