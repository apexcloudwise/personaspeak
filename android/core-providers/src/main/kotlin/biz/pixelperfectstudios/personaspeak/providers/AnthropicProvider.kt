package biz.pixelperfectstudios.personaspeak.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI

/**
 * [CompletionProvider] for the Anthropic Messages API (`/v1/messages`).
 * The API key travels only in the `x-api-key` header and never appears in
 * failure messages.
 */
class AnthropicProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.anthropic.com",
    private val model: String = "claude-haiku-4-5",
) : CompletionProvider {
    override val id = "anthropic"
    override val displayName = "Claude (Anthropic)"

    override suspend fun rewrite(system: String, text: String): Result<String> = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/v1/messages"
        val body =
            "{\"model\":${MiniJson.quote(model)},\"max_tokens\":1024," +
                "\"system\":${MiniJson.quote(system)}," +
                "\"messages\":[{\"role\":\"user\",\"content\":${MiniJson.quote(text)}}]}"
        try {
            val response = postJson(
                url,
                mapOf(
                    "x-api-key" to apiKey,
                    "anthropic-version" to "2023-06-01",
                ),
                body,
            )
            val content = MiniJson.path(MiniJson.parse(response), "content", 0, "text") as? String
            val trimmed = content?.trim()
            if (trimmed.isNullOrEmpty()) failure(unreadableMessage) else Result.success(trimmed)
        } catch (e: HttpError) {
            failure("$displayName answered HTTP ${e.code} and declined the request. Try again shortly.")
        } catch (e: IOException) {
            failure("Couldn't reach ${hostOf(url)} — check your connection.")
        } catch (e: Exception) {
            failure(unreadableMessage)
        }
    }

    private val unreadableMessage: String
        get() = "$displayName sent a reply we couldn't read. Try again."

    private fun failure(message: String): Result<String> = Result.failure(IllegalStateException(message))

    private fun hostOf(url: String): String = runCatching { URI.create(url).host }.getOrDefault(url)
}
