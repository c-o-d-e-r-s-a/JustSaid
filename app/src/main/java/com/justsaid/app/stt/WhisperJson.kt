package com.justsaid.app.stt

/**
 * Parser for the fixed JSON shape emitted by `justsaid_whisper_jni.cpp`:
 * `{"segments":[{"t0":<ms>,"t1":<ms>,"text":"..."}]}`.
 *
 * Hand-rolled because we control both ends of the wire: org.json is stubbed out in
 * JVM unit tests and pulling a JSON library for one internal message is not worth
 * the dependency (Prime Directive: minimal surface).
 */
internal object WhisperJson {

    data class RawSegment(val t0Ms: Long, val t1Ms: Long, val text: String)

    /** Returns parsed segments; empty list for blank/malformed input (STT treats it as silence). */
    fun parseSegments(json: String): List<RawSegment> {
        val out = mutableListOf<RawSegment>()
        var i = json.indexOf('{', startIndex = json.indexOf("\"segments\"").let { if (it < 0) return out else it })
        while (i in json.indices) {
            val objEnd = findObjectEnd(json, i) ?: return out
            val obj = json.substring(i, objEnd + 1)
            val t0 = longField(obj, "t0")
            val t1 = longField(obj, "t1")
            val text = stringField(obj, "text")
            if (t0 != null && t1 != null && text != null) out += RawSegment(t0, t1, text)
            i = json.indexOf('{', objEnd + 1)
        }
        return out
    }

    private fun findObjectEnd(s: String, start: Int): Int? {
        var inString = false
        var escaped = false
        for (j in start until s.length) {
            val c = s[j]
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '}' -> return j
            }
        }
        return null
    }

    private fun longField(obj: String, name: String): Long? {
        val m = Regex("\"$name\"\\s*:\\s*(-?\\d+)").find(obj) ?: return null
        return m.groupValues[1].toLongOrNull()
    }

    private fun stringField(obj: String, name: String): String? {
        val keyIdx = obj.indexOf("\"$name\"")
        if (keyIdx < 0) return null
        val colon = obj.indexOf(':', keyIdx + name.length + 2)
        if (colon < 0) return null
        val open = obj.indexOf('"', colon + 1)
        if (open < 0) return null
        val sb = StringBuilder()
        var j = open + 1
        while (j < obj.length) {
            when (val c = obj[j]) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (j + 1 >= obj.length) return null
                    when (val esc = obj[j + 1]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> {
                            val hex = obj.substring(j + 2, (j + 6).coerceAtMost(obj.length))
                            val code = hex.toIntOrNull(16) ?: return null
                            sb.append(code.toChar())
                            j += 4
                        }
                        else -> sb.append(esc)
                    }
                    j++
                }
                else -> sb.append(c)
            }
            j++
        }
        return null
    }
}
