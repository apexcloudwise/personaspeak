package biz.pixelperfectstudios.personaspeak.providers

import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.NetworkErrorCode
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

/**
 * Pluggable HTTP transport interface for testing without real network egress.
 */
interface HttpTransport {
    fun post(
        endpointUrl: String,
        headers: Map<String, String>,
        bodyUtf8: ByteArray,
    ): HttpResponse
}

data class HttpResponse(
    val statusCode: Int,
    val body: String,
)

/**
 * Default HTTPS transport enforcing SSL, timeouts, and single approved endpoint.
 */
class DefaultHttpTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : HttpTransport {
    override fun post(
        endpointUrl: String,
        headers: Map<String, String>,
        bodyUtf8: ByteArray,
    ): HttpResponse {
        require(endpointUrl == AnthropicMessagesAdapter.ENDPOINT_URL) {
            "Egress violation: endpoint must be strictly ${AnthropicMessagesAdapter.ENDPOINT_URL}"
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
 * Adapter implementation for the Anthropic Messages API.
 */
class AnthropicMessagesAdapter(
    private val transport: HttpTransport = DefaultHttpTransport(),
    private val model: String = "claude-3-5-haiku-20241022",
    private val maxTokens: Int = 1024,
) : ProviderAdapter {

    override val providerId: String = "anthropic"
    override val displayName: String = "Anthropic (Claude)"

    override suspend fun rewrite(
        system: String,
        text: String,
        secret: SecretBytes,
    ): AdapterResult = withContext(Dispatchers.IO) {
        val apiKeyString = String(secret.value, StandardCharsets.UTF_8)
        try {
            val headers = mapOf(
                HEADER_API_KEY to apiKeyString,
                HEADER_ANTHROPIC_VERSION to ANTHROPIC_VERSION_VALUE,
                HEADER_CONTENT_TYPE to CONTENT_TYPE_JSON,
            )

            val jsonBody = buildRequestBody(system = system, text = text)
            val bodyBytes = jsonBody.toByteArray(StandardCharsets.UTF_8)

            val response = transport.post(
                endpointUrl = ENDPOINT_URL,
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
            // Defense-in-depth: zeroes the mutable ByteArray in SecretBytes.
            // The transient String copy required by HttpURLConnection's header API is immutable
            // and reclaimed by JVM garbage collection.
            secret.value.fill(0)
        }
    }

    private fun buildRequestBody(system: String, text: String): String {
        return buildString {
            append("{")
            append("\"model\":\"").append(escapeJson(model)).append("\",")
            append("\"max_tokens\":").append(maxTokens).append(",")
            append("\"system\":\"").append(escapeJson(system)).append("\",")
            append("\"messages\":[{\"role\":\"user\",\"content\":\"").append(escapeJson(text)).append("\"}]")
            append("}")
        }
    }

    companion object {
        const val ENDPOINT_URL = "https://api.anthropic.com/v1/messages"
        const val HEADER_API_KEY = "x-api-key"
        const val HEADER_ANTHROPIC_VERSION = "anthropic-version"
        const val ANTHROPIC_VERSION_VALUE = "2023-06-01"
        const val HEADER_CONTENT_TYPE = "content-type"
        const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"

        fun escapeJson(value: String): String {
            val sb = StringBuilder()
            for (c in value) {
                when (c) {
                    '\\' -> sb.append("\\\\")
                    '"' -> sb.append("\\\"")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> {
                        if (c.code < 0x20) {
                            sb.append(String.format("\\u%04x", c.code))
                        } else {
                            sb.append(c)
                        }
                    }
                }
            }
            return sb.toString()
        }

        fun extractTextFromResponse(json: String): String? {
            // Find "text":"..." inside "content"
            val textKeyIndex = json.indexOf("\"text\"")
            if (textKeyIndex == -1) return null

            val colonIndex = json.indexOf(':', textKeyIndex)
            if (colonIndex == -1) return null

            val firstQuoteIndex = json.indexOf('"', colonIndex)
            if (firstQuoteIndex == -1) return null

            val sb = StringBuilder()
            var i = firstQuoteIndex + 1
            var escaped = false
            while (i < json.length) {
                val c = json[i]
                if (escaped) {
                    when (c) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (i + 4 < json.length) {
                                val hex = json.substring(i + 1, i + 5)
                                val code = hex.toIntOrNull(16)
                                if (code != null) {
                                    sb.append(code.toChar())
                                    i += 4
                                } else {
                                    sb.append("u")
                                }
                            } else {
                                sb.append("u")
                            }
                        }
                        else -> sb.append(c)
                    }
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    return sb.toString()
                } else {
                    sb.append(c)
                }
                i++
            }
            return null
        }
    }
}
