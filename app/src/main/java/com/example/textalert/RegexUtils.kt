package com.example.textalert

object RegexUtils {
    fun looksLikeRegex(s: String): Boolean =
        s.startsWith("^") || s.endsWith("$") || s.contains(".*") || s.contains("\\d") ||
        s.contains("[") || s.contains("(?i)")

    fun compileOrNull(pattern: String, ignoreCase: Boolean): Regex? = try {
        val opts = if (ignoreCase)
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        else
            setOf(RegexOption.DOT_MATCHES_ALL)
        pattern.toRegex(opts)
    } catch (_: Throwable) {
        null
    }

    fun normalize(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
            .trim()
}

