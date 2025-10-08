package com.example.textalert

import java.text.Normalizer
import kotlin.math.ceil
import kotlin.math.min

class TextMatcher(
    private val caseInsensitive: () -> Boolean = { true },
    private val fuzzyEnabled: () -> Boolean = { true },
    private val fuzzyStrength: () -> Int = { 60 }
) {
    fun match(text: String, targets: List<String>): String? {
        val useCI = caseInsensitive()
        val useFuzzy = fuzzyEnabled()
        val hay = if (useCI) normalize(text) else text
        for (t in targets) {
            val isRegex = looksLikeRegex(t)
            if (isRegex) {
                val opts = if (useCI)
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                else
                    setOf(RegexOption.DOT_MATCHES_ALL)
                val r = t.toRegex(opts)
                if (r.containsMatchIn(hay)) return t
                continue
            }
            val needle = if (useCI) normalize(t) else t.trim()
            if (needle.isEmpty()) continue
            if (hay.contains(needle)) return t
            if (useFuzzy && fuzzyContains(hay, needle)) return t
        }
        return null
    }

    private fun looksLikeRegex(s: String): Boolean =
        s.startsWith("^") || s.endsWith("$") || s.contains(".*") || s.contains("\\d") || s.contains("[") || s.contains("(?i)")

    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private fun fuzzyContains(hay: String, needle: String): Boolean {
        val n = needle.length
        if (n == 0 || hay.isEmpty()) return false
        val k = maxEditsFor(n)
        if (k == 0) return false
        val minLen = maxOf(1, n - 1)
        val maxLen = n + 1
        var i = 0
        val limit = hay.length
        while (i + minLen <= limit) {
            val len = min(maxLen, limit - i)
            val sub = hay.substring(i, i + len)
            val d = boundedLevenshtein(sub, needle, k)
            if (d <= k) return true
            i++
        }
        return false
    }

    private fun maxEditsFor(n: Int): Int {
        val s = fuzzyStrength().coerceIn(0, 100) / 100.0
        val base = when {
            n <= 4 -> 1
            n <= 8 -> 2
            else -> 3
        }
        return ceil(base * s).toInt()
    }

    private fun boundedLevenshtein(a: String, b: String, k: Int): Int {
        val n = a.length
        val m = b.length
        if (k == 0) return if (a == b) 0 else k + 1
        if (k < kotlin.math.abs(n - m)) return k + 1
        val inf = k + 1
        var prev = IntArray(m + 1) { if (it <= k) it else inf }
        var cur = IntArray(m + 1)
        for (i in 1..n) {
            val ai = a[i - 1]
            cur[0] = if (i <= k) i else inf
            val from = maxOf(1, i - k)
            val to = min(m, i + k)
            if (from > 1) cur[from - 1] = inf
            for (j in from..to) {
                val cost = if (ai == b[j - 1]) 0 else 1
                val del = prev[j] + 1
                val ins = cur[j - 1] + 1
                val sub = prev[j - 1] + cost
                cur[j] = minOf(del, ins, sub)
            }
            for (j in 0..m) {
                prev[j] = if (cur[j] <= k) cur[j] else inf
            }
        }
        val res = prev[m]
        return if (res <= k) res else k + 1
    }
}
