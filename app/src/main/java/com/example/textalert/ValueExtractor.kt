package com.example.textalert

class ValueExtractor {
    data class Hit(val label: String, val value: String)
    private val patterns = listOf(
        "Eurobetrag" to "\\b\\d{1,3}(?:\\.\\d{3})*,\\d{2}\\b",
        "Datum" to "\\b\\d{2}\\.\\d{2}\\.\\d{4}\\b",
        "Rechnungsnr." to "\\b(?:RG|RE|RN)[\\s\\-]?\\d{4,}\\b",
        "PLZ" to "\\b\\d{5}\\b",
        "IBAN" to "\\b[A-Z]{2}\\d{2}(?:\\s?\\w{4}){3,}\\b",
        "Prozent" to "\\b\\d{1,3},\\d{1,2}\\s?%\\b",
        "Zahl mit Komma" to "\\b\\d+,\\d+\\b"
    )
    fun extract(text: String): List<Hit> {
        val hits = mutableListOf<Hit>()
        for ((label, pat) in patterns) {
            pat.toRegex(setOf(RegexOption.MULTILINE)).findAll(text).forEach { m ->
                hits.add(Hit(label, m.value))
            }
        }
        return hits
    }
}

