package com.example.textalert

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar

class RegexHelpActivity : AppCompatActivity(), RegexAdapter.OnCopy {
    private lateinit var list: RecyclerView
    private val items = listOf(
        RegexItem("Eurobetrag", "\\b\\d{1,3}(?:\\.\\d{3})*,\\d{2}\\b", "12,34  1.234,56"),
        RegexItem("Datum (TT.MM.JJJJ)", "\\b\\d{2}\\.\\d{2}\\.\\d{4}\\b", "07.10.2025"),
        RegexItem("E-Mail", "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "name@mail.de"),
        RegexItem("IBAN", "\\b[A-Z]{2}\\d{2}(?:\\s?\\w{4}){3,}\\b", "DE12 3456 7890 1234 5678 90"),
        RegexItem("Rechnungsnr.", "\\b(?:RG|RE|RN)[\\s\\-]?\\d{4,}\\b", "RE-123456"),
        RegexItem("PLZ", "\\b\\d{5}\\b", "10115"),
        RegexItem("Zahl mit Komma", "\\b\\d+,\\d+\\b", "5,75"),
        RegexItem("Prozent", "\\b\\d{1,3},\\d{1,2}\\s?%\\b", "19,00 %")
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_regex_help)
        list = findViewById(R.id.regexList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = RegexAdapter(items, this)
    }
    override fun copy(pattern: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("regex", pattern))
        Snackbar.make(list, "Regex kopiert", Snackbar.LENGTH_SHORT).show()
    }
}

data class RegexItem(val title: String, val pattern: String, val example: String)

