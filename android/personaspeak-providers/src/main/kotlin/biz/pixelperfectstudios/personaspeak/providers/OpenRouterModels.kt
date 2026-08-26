package biz.pixelperfectstudios.personaspeak.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

/**
 * Model metadata entry parsed from OpenRouter's public `/models` catalog.
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val isFree: Boolean,
)

/**
 * Parser and fetcher for OpenRouter's public model catalog.
 */
object OpenRouterModels {

    const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"

    /**
     * Parses a `/models` JSON response body into a list of [ModelInfo] sorted free-first.
     */
    fun parse(json: String): List<ModelInfo> {
        val root = MiniJson.parse(json)
        val data = MiniJson.path(root, "data") as? List<*>
            ?: throw IllegalArgumentException("Missing or invalid 'data' array in models payload")

        return data.mapNotNull { entry ->
            val id = MiniJson.path(entry, "id") as? String ?: return@mapNotNull null
            val name = MiniJson.path(entry, "name") as? String ?: id
            val promptPrice = MiniJson.path(entry, "pricing", "prompt") as? String
            val isFree = promptPrice == "0" || promptPrice == "0.0"
            ModelInfo(id = id, name = name, isFree = isFree)
        }.sortedWith(compareByDescending<ModelInfo> { it.isFree }.thenBy { it.id })
    }

    /**
     * Fetches public models list via [transport] or HTTPS connection.
     */
    suspend fun fetch(
        baseUrl: String = DEFAULT_BASE_URL,
        transport: HttpTransport? = null,
    ): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        val endpointUrl = baseUrl.trimEnd('/') + "/models"
        try {
            val json = if (transport != null) {
                val response = transport.post(
                    endpointUrl = endpointUrl,
                    headers = mapOf("Accept" to "application/json"),
                    bodyUtf8 = ByteArray(0),
                )
                if (response.statusCode !in 200..299) {
                    return@withContext Result.failure(
                        IllegalStateException("OpenRouter returned HTTP ${response.statusCode}"),
                    )
                }
                response.body
            } else {
                fetchHttps(endpointUrl)
            }
            Result.success(parse(json))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchHttps(urlStr: String): String {
        val url = URL(urlStr)
        val connection = (url.openConnection() as? HttpsURLConnection)
            ?: error("HTTPS required")
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        val code = connection.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("OpenRouter returned HTTP $code")
        }
        val stream = connection.inputStream
        return stream.use { s ->
            BufferedReader(InputStreamReader(s, StandardCharsets.UTF_8)).readText()
        }
    }
}
