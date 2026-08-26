package biz.pixelperfectstudios.personaspeak.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * [CompletionProvider] for any OpenAI-compatible `/chat/completions` endpoint
 * (OpenAI, OpenRouter, and self-hosted gateways). API key never leaves the
 * Authorization header and never appears in failure messages.
 */
open class HttpChatCompletionsProvider(
    override val id: String,
    override val displayName: String,
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
) : CompletionProvider {

    override suspend fun rewrite(system: String, text: String): Result<String> = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val body =
            "{\"model\":${MiniJson.quote(model)},\"messages\":[" +
                "{\"role\":\"system\",\"content\":${MiniJson.quote(system)}}," +
                "{\"role\":\"user\",\"content\":${MiniJson.quote(text)}}]," +
                "\"temperature\":0.8}"
        try {
            val response = postJson(url, mapOf("Authorization" to "Bearer $apiKey"), body)
            val content = MiniJson.path(MiniJson.parse(response), "choices", 0, "message", "content") as? String
            val trimmed = content?.trim()
            if (trimmed.isNullOrEmpty()) failure(unreadableMessage) else Result.success(trimmed)
        } catch (e: HttpError) {
            failure("$displayName answered HTTP ${e.code} and declined the request. Try again shortly.")
        } catch (e: IOException) {
            failure("Couldn't reach ${host(url)} — check your connection.")
        } catch (e: Exception) {
            failure(unreadableMessage)
        }
    }

    private val unreadableMessage: String
        get() = "$displayName sent a reply we couldn't read. Try again."

    private fun failure(message: String): Result<String> = Result.failure(IllegalStateException(message))
}

internal class HttpError(val code: Int) : Exception()

internal fun postJson(url: String, headers: Map<String, String>, body: String): String {
    val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = 30_000
    connection.readTimeout = 30_000
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "application/json")
    for ((name, value) in headers) connection.setRequestProperty(name, value)
    return try {
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        if (code !in 200..299) throw HttpError(code)
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private fun host(url: String): String = runCatching { URI.create(url).host }.getOrDefault(url)

internal object MiniJson {

    fun quote(s: String): String = buildString {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }

    fun parse(text: String): Any? = Parser(text).parseDocument()

    fun path(root: Any?, vararg segments: Any): Any? {
        var current: Any? = root
        for (segment in segments) {
            current = when (segment) {
                is String -> (current as? Map<*, *>)?.get(segment)
                is Int -> (current as? List<*>)?.getOrNull(segment)
                else -> null
            } ?: return null
        }
        return current
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parseDocument(): Any? {
            skipWhitespace()
            val value = parseValue()
            skipWhitespace()
            require(i == s.length) { "trailing characters at $i" }
            return value
        }

        private fun parseValue(): Any? = when (peek()) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            else -> when {
                match("true") -> true
                match("false") -> false
                match("null") -> null
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            i++
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') { i++; return map }
            while (true) {
                skipWhitespace()
                require(peek() == '"') { "expected key at $i" }
                val key = parseString()
                skipWhitespace()
                require(peek() == ':') { "expected ':' at $i" }
                i++
                skipWhitespace()
                map[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> i++
                    '}' -> { i++; return map }
                    else -> throw IllegalArgumentException("expected ',' or '}' at $i")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            i++
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') { i++; return list }
            while (true) {
                skipWhitespace()
                list.add(parseValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> i++
                    ']' -> { i++; return list }
                    else -> throw IllegalArgumentException("expected ',' or ']' at $i")
                }
            }
        }

        private fun parseString(): String {
            require(peek() == '"') { "expected string at $i" }
            i++
            val sb = StringBuilder()
            while (true) {
                val c = s[i]
                when {
                    c == '"' -> { i++; return sb.toString() }
                    c == '\\' -> {
                        i++
                        when (val e = s[i]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                i++
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 3
                            }
                            else -> throw IllegalArgumentException("bad escape at $i")
                        }
                        i++
                    }
                    else -> { sb.append(c); i++ }
                }
            }
        }

        private fun parseNumber(): Any {
            val start = i
            while (i < s.length && s[i] in "-+.eE0123456789") i++
            val raw = s.substring(start, i)
            return if (raw.none { it == '.' || it == 'e' || it == 'E' }) raw.toLong() else raw.toDouble()
        }

        private fun skipWhitespace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun peek(): Char {
            if (i >= s.length) throw IllegalArgumentException("unexpected end of input")
            return s[i]
        }

        private fun match(literal: String): Boolean {
            if (!s.startsWith(literal, i)) return false
            i += literal.length
            return true
        }
    }
}
