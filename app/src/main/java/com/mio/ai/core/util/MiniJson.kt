package com.mio.ai.core.util

/**
 * Dependency-free, allocation-light JSON reader/writer for Mio's constrained
 * LLM payloads. Pure Kotlin (works in JVM unit tests — no org.json/Android).
 *
 * Supports objects, arrays, strings (with escapes), numbers, booleans, null.
 */
object MiniJson {

    fun parse(text: String): Any? {
        val parser = Parser(text.trim())
        parser.skipWs()
        val value = parser.readValue()
        parser.skipWs()
        return value
    }

    /** Extract the first top-level JSON object/array from free-form LLM output. */
    fun extractFirstJson(text: String): String? {
        val startObj = text.indexOf('{')
        val startArr = text.indexOf('[')
        val start = when {
            startObj == -1 -> startArr
            startArr == -1 -> startObj
            else -> minOf(startObj, startArr)
        }
        if (start == -1) return null
        val open = text[start]
        val close = if (open == '{') '}' else ']'
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
            } else {
                if (c == '"') inString = true
                else if (c == open) depth++
                else if (c == close) {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    fun stringify(value: Any?): String = buildString { writeValue(this, value) }

    private fun writeValue(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> {
                sb.append('"')
                for (c in value) {
                    when (c) {
                        '"' -> sb.append("\\\"")
                        '\\' -> sb.append("\\\\")
                        '\n' -> sb.append("\\n")
                        '\r' -> sb.append("\\r")
                        '\t' -> sb.append("\\t")
                        else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
                    }
                }
                sb.append('"')
            }
            is Number, is Boolean -> sb.append(value.toString())
            is Map<*, *> -> {
                sb.append('{')
                value.entries.forEachIndexed { i, e ->
                    if (i > 0) sb.append(',')
                    writeValue(sb, e.key.toString())
                    sb.append(':')
                    writeValue(sb, e.value)
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) sb.append(',')
                    writeValue(sb, v)
                }
                sb.append(']')
            }
            else -> writeValue(sb, value.toString())
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun asMap(value: Any?): Map<String, Any?> = value as? Map<String, Any?> ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    fun asList(value: Any?): List<Any?> = value as? List<Any?> ?: emptyList()

    fun asString(value: Any?): String? = value as? String

    fun asInt(value: Any?): Int? = when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    fun asLong(value: Any?): Long? = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }

    fun asBoolean(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is String -> value.toBooleanStrictOrNull()
        else -> null
    }

    private class Parser(val s: String) {
        var i = 0

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun readValue(): Any? {
            if (i >= s.length) return null
            return when (s[i]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> readNumber()
            }
        }

        private fun readObject(): Map<String, Any?> {
            i++ // {
            val map = LinkedHashMap<String, Any?>()
            skipWs()
            if (i < s.length && s[i] == '}') {
                i++
                return map
            }
            while (i < s.length) {
                skipWs()
                if (i >= s.length || s[i] != '"') break
                val key = readString()
                skipWs()
                if (i < s.length && s[i] == ':') i++
                skipWs()
                map[key] = readValue()
                skipWs()
                if (i < s.length && s[i] == ',') {
                    i++
                    continue
                }
                break
            }
            if (i < s.length && s[i] == '}') i++
            return map
        }

        private fun readArray(): List<Any?> {
            i++ // [
            val list = ArrayList<Any?>()
            skipWs()
            if (i < s.length && s[i] == ']') {
                i++
                return list
            }
            while (i < s.length) {
                skipWs()
                // Tolerate trailing commas / empty slots.
                if (i < s.length && (s[i] == ']' || s[i] == ',')) {
                    if (s[i] == ',') {
                        i++
                        skipWs()
                    }
                    if (i < s.length && s[i] == ']') {
                        i++
                    }
                    break
                }
                list += readValue()
                skipWs()
                if (i < s.length && s[i] == ',') {
                    i++
                    continue
                }
                break
            }
            if (i < s.length && s[i] == ']') i++
            return list
        }

        private fun readString(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                when (c) {
                    '"' -> break
                    '\\' -> {
                        if (i >= s.length) break
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = s.substring(i, (i + 4).coerceAtMost(s.length))
                                i += 4
                                sb.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun readLiteral(word: String, value: Any?): Any? {
            if (s.startsWith(word, i)) i += word.length
            return value
        }

        private fun readNumber(): Number {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-eE.")) i++
            val raw = s.substring(start, i)
            return raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: 0
        }
    }
}
