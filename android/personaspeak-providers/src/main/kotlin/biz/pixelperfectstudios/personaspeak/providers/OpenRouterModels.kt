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
 * Default HTTPS transport for OpenRouter model catalog endpoint.
 */
class DefaultOpenRouterModelsHttpTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : HttpTransport {
    override fun post(
        endpointUrl: String,
        headers: Map<String, String>,
        bodyUtf8: ByteArray,
    ): HttpResponse {
        require(endpointUrl == OpenRouterModels.ENDPOINT_URL) {
            "Egress violation: endpoint must be strictly ${OpenRouterModels.ENDPOINT_URL}"
        }

        val url = URL(endpointUrl)
        val connection = (url.openConnection() as? HttpsURLConnection)
            ?: error("HTTPS required")

        connection.requestMethod = "GET"
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.instanceFollowRedirects = false

        for ((key, value) in headers) {
            connection.setRequestProperty(key, value)
        }

        val statusCode = connection.responseCode
        val stream = if (statusCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }

        val body = stream?.use { s ->
            BufferedReader(InputStreamReader(s, StandardCharsets.UTF_8)).readText()
        } ?: ""

        return HttpResponse(statusCode, body)
    }
}

/**
 * Parser and fetcher for OpenRouter's public model catalog.
 */
object OpenRouterModels {

    const val ENDPOINT_URL = "https://openrouter.ai/api/v1/models"

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
     * Fetches public models list via [transport] over [endpointUrl].
     */
    suspend fun fetch(
        transport: HttpTransport = DefaultOpenRouterModelsHttpTransport(),
        endpointUrl: String = ENDPOINT_URL,
    ): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        try {
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
            Result.success(parse(response.body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
