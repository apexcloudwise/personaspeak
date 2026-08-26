package biz.pixelperfectstudios.personaspeak.providers

import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.NetworkErrorCode
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

/**
 * Default HTTPS transport for OpenRouter enforcing SSL, timeouts, and pinned endpoint.
 */
class DefaultOpenRouterHttpTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : HttpTransport {
    override fun post(
        endpointUrl: String,
        headers: Map<String, String>,
        bodyUtf8: ByteArray,
    ): HttpResponse {
        require(endpointUrl == OpenRouterAdapter.ENDPOINT_URL) {
            "Egress violation: endpoint must be strictly ${OpenRouterAdapter.ENDPOINT_URL}"
        }

        val url = URL(endpointUrl)
        val connection = (url.openConnection() as? HttpsURLConnection)
            ?: error("HTTPS required")

        connection.requestMethod = "POST"
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.doInput = true
        connection.doOutput = true
        connection.instanceFollowRedirects = false

        for ((key, value) in headers) {
            connection.setRequestProperty(key, value)
        }

        connection.outputStream.use { os ->
            os.write(bodyUtf8)
            os.flush()
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
 * Adapter implementation for the OpenRouter Chat Completions API.
 * Uses OpenAI-compatible wire format with pure Kotlin MiniJson parsing and strict memory zeroing.
 */
class OpenRouterAdapter(
    private val transport: HttpTransport = DefaultOpenRouterHttpTransport(),
    private val model: String = DEFAULT_MODEL,
    private val temperature: Double = 0.8,
    private val endpointUrl: String = ENDPOINT_URL,
) : ProviderAdapter {

    override val providerId: String = "openrouter"
    override val displayName: String = "OpenRouter"

    override suspend fun rewrite(
        system: String,
        text: String,
        secret: SecretBytes,
    ): AdapterResult = withContext(Dispatchers.IO) {
        val apiKeyString = String(secret.value, StandardCharsets.UTF_8)
        try {
            val headers = mapOf(
                HEADER_AUTHORIZATION to "Bearer $apiKeyString",
                HEADER_REFERER to REFERER_VALUE,
                HEADER_TITLE to TITLE_VALUE,
                HEADER_CONTENT_TYPE to CONTENT_TYPE_JSON,
            )

            val jsonBody = buildRequestBody(system = system, text = text)
            val bodyBytes = jsonBody.toByteArray(StandardCharsets.UTF_8)

            val response = transport.post(
                endpointUrl = endpointUrl,
                headers = headers,
                bodyUtf8 = bodyBytes,
            )

            when (response.statusCode) {
                200 -> {
                    val extractedText = extractTextFromResponse(response.body)
                    if (extractedText != null) {
                        AdapterResult.Success(extractedText)
                    } else {
                        AdapterResult.NetworkFailure(NetworkErrorCode.HTTP_SERVER_ERROR)
                    }
                }
                401, 403 -> AdapterResult.AuthFailure
                in 400..499 -> AdapterResult.NetworkFailure(NetworkErrorCode.HTTP_CLIENT_ERROR)
                in 500..599 -> AdapterResult.NetworkFailure(NetworkErrorCode.HTTP_SERVER_ERROR)
                else -> AdapterResult.NetworkFailure(NetworkErrorCode.IO_ERROR)
            }
        } catch (e: SocketTimeoutException) {
            AdapterResult.NetworkFailure(NetworkErrorCode.TIMEOUT)
        } catch (e: Exception) {
            AdapterResult.NetworkFailure(NetworkErrorCode.IO_ERROR)
        } finally {
            // Defense-in-depth: zeroes the mutable ByteArray in SecretBytes immediately.
            secret.value.fill(0)
        }
    }

    private fun buildRequestBody(system: String, text: String): String {
        return buildString {
            append("{")
            append("\"model\":").append(MiniJson.quote(model)).append(",")
            append("\"messages\":[")
            append("{\"role\":\"system\",\"content\":").append(MiniJson.quote(system)).append("},")
            append("{\"role\":\"user\",\"content\":").append(MiniJson.quote(text)).append("}")
            append("],")
            append("\"temperature\":").append(temperature)
            append("}")
        }
    }

    companion object {
        const val ENDPOINT_URL = "https://openrouter.ai/api/v1/chat/completions"
        const val DEFAULT_MODEL = "nvidia/nemotron-3-super-120b-a12b:free"
        const val HEADER_AUTHORIZATION = "Authorization"
        const val HEADER_REFERER = "HTTP-Referer"
        const val REFERER_VALUE = "https://pixelperfectstudios.biz"
        const val HEADER_TITLE = "X-Title"
        const val TITLE_VALUE = "PersonaSpeak"
        const val HEADER_CONTENT_TYPE = "Content-Type"
        const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"

        fun extractTextFromResponse(json: String): String? {
            return try {
                val parsed = MiniJson.parse(json)
                val content = MiniJson.path(parsed, "choices", 0, "message", "content") as? String
                val trimmed = content?.trim()
                if (trimmed.isNullOrEmpty()) null else trimmed
            } catch (e: Exception) {
                null
            }
        }
    }
}
