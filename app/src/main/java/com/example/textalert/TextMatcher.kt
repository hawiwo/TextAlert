package com.example.textalert

class TextMatcher {
    fun match(text: String, targets: List<String>): String? {
        val norm = normalize(text)
        for (t in targets) {
            val isRegex = looksLikeRegex(t)
            if (isRegex) {
                val r = t.toRegex(RegexOption.IGNORE_CASE)
                if (r.containsMatchIn(norm)) return t
            } else {
                val needle = normalize(t)
                if (norm.contains(needle)) return t
            }
        }
        return null
    }
    private fun looksLikeRegex(s: String): Boolean = (s.startsWith("^") || s.endsWith("$") || s.contains(".*") || s.contains("\\d") || s.contains("["))
    private fun normalize(s: String): String = s.lowercase().replace("\\s+".toRegex(), " ").trim()
}

