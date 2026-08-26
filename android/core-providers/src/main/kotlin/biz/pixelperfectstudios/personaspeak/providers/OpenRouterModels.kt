package biz.pixelperfectstudios.personaspeak.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** One entry of OpenRouter's public model catalog. */
data class ModelInfo(val id: String, val name: String, val isFree: Boolean)

/** Fetches OpenRouter's public `/models` list for the setup-time model picker. */
object OpenRouterModels {

    suspend fun fetch(baseUrl: String = "https://openrouter.ai/api/v1"): Result<List<ModelInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val url = baseUrl.trimEnd('/') + "/models"
                val raw = getJson(url)
                try {
                    Result.success(parse(raw))
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "PARSE_FAIL ${e.javaClass.simpleName}: ${e.message} | head=${raw.take(300)}",
                        e,
                    )
                }
            } catch (e: HttpError) {
                failure("OpenRouter answered HTTP ${e.code} and declined the request. Try again shortly.")
            } catch (e: IOException) {
                failure("Couldn't reach openrouter.ai — check your connection.")
            } catch (e: Exception) {
                failure("OpenRouter's model list came back unreadable. Try again. [${e.javaClass.simpleName}: ${e.message}]")
            }
        }

    private fun parse(text: String): List<ModelInfo> {
        val data = MiniJson.path(MiniJson.parse(text), "data") as? List<*>
            ?: throw IllegalArgumentException("missing data array")
        return data.mapNotNull { entry ->
            val id = MiniJson.path(entry, "id") as? String ?: return@mapNotNull null
            val name = MiniJson.path(entry, "name") as? String ?: id
            val promptPrice = MiniJson.path(entry, "pricing", "prompt") as? String
            ModelInfo(id = id, name = name, isFree = promptPrice == "0")
        }.sortedWith(compareByDescending<ModelInfo> { it.isFree }.thenBy { it.id })
    }

    private fun failure(message: String): Result<List<ModelInfo>> =
        Result.failure(IllegalStateException(message))
}

internal fun getJson(url: String): String {
    val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 30_000
    connection.readTimeout = 30_000
    return try {
        val code = connection.responseCode
        if (code !in 200..299) throw HttpError(code)
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
