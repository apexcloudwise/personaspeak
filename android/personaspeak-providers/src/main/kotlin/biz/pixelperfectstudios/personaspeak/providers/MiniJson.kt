package biz.pixelperfectstudios.personaspeak.providers

/**
 * Lightweight, zero-dependency JSON parser and serializer for provider payloads.
 * Pure Kotlin with zero reflection and zero Android platform dependencies.
 */
object MiniJson {

    fun quote(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> {
                    if (c.code < 0x20) {
                        append(String.format("\\u%04x", c.code))
                    } else {
                        append(c)
                    }
                }
            }
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
            i++ // consume '{'
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                i++
                return map
            }
            while (true) {
                skipWhitespace()
                require(peek() == '"') { "expected string key at $i" }
                val key = parseString()
                skipWhitespace()
                require(peek() == ':') { "expected ':' at $i" }
                i++ // consume ':'
                skipWhitespace()
                map[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return map
                    }
                    else -> throw IllegalArgumentException("expected ',' or '}' at $i")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            i++ // consume '['
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                i++
                return list
            }
            while (true) {
                skipWhitespace()
                list.add(parseValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return list
                    }
                    else -> throw IllegalArgumentException("expected ',' or ']' at $i")
                }
            }
        }

        private fun parseString(): String {
            require(peek() == '"') { "expected string at $i" }
            i++ // consume opening '"'
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw IllegalArgumentException("unterminated string")
                val c = s[i]
                when {
                    c == '"' -> {
                        i++
                        return sb.toString()
                    }
                    c == '\\' -> {
                        i++
                        if (i >= s.length) throw IllegalArgumentException("unterminated escape")
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
                                if (i + 4 >= s.length) throw IllegalArgumentException("incomplete unicode escape")
                                val hex = s.substring(i + 1, i + 5)
                                val code = hex.toIntOrNull(16)
                                    ?: throw IllegalArgumentException("invalid unicode escape: $hex")
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("invalid escape character: $e")
                        }
                        i++
                    }
                    else -> {
                        sb.append(c)
                        i++
                    }
                }
            }
        }

        private fun parseNumber(): Any {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i] in '0'..'9' || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) {
                i++
            }
            val raw = s.substring(start, i)
            return if (raw.contains('.') || raw.contains('e') || raw.contains('E')) {
                raw.toDoubleOrNull() ?: throw IllegalArgumentException("invalid number: $raw")
            } else {
                raw.toLongOrNull() ?: throw IllegalArgumentException("invalid integer: $raw")
            }
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
