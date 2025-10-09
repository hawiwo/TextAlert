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
    private lateinit var store: KeywordStore

    private val items = listOf(
        RegexItem("Eurobetrag", "\\b\\d{1,3}(?:\\.\\d{3})*,\\d{2}\\b", "12,34  1.234,56"),
        RegexItem("Datum (TT.MM.JJJJ)", "\\b\\d{2}\\.\\d{2}\\.\\d{4}\\b", "07.10.2025"),
        RegexItem("E-Mail", "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "name@mail.de"),
        RegexItem("IBAN", "\\b[A-Z]{2}\\d{2}(?:\\s?\\w{4}){3,}\\b", "DE12 3456 7890 1234 5678 90"),
        RegexItem("Rechnungsnr.", "\\b(?:RG|RE|RN)[\\s\\-]?\\d{4,}\\b", "RE-123456"),
        RegexItem("PLZ", "\\b\\d{5}\\b", "10115"),
        RegexItem("Zahl mit Komma", "\\b\\d+,\\d+\\b", "5,75"),
        RegexItem("Prozent", "\\b\\d{1,3},\\d{1,2}\\s?%\\b", "19,00 %"),
        RegexItem("Autokennzeichen", "(?<![A-Z0-9])[A-ZÄÖÜ]{1,3}[-\\s]?[A-Z]{1,2}\\s?\\d{1,4}[EH]?(?![A-Z0-9])", "LB DW 2312")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_regex_help)
        store = KeywordStore(this)

        list = findViewById(R.id.regexList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = RegexAdapter(items, this)
    }

    // Wird vom Button in der Liste aufgerufen
    override fun copy(pattern: String) {
        // 1) in KeywordStore anhängen
        val added = store.add(pattern)

        // 2) zusätzlich in die Zwischenablage (optional, praktisch zum schnellen Einfügen woanders)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("regex", pattern))

        // 3) Feedback
        val msg = if (added) "Hinzugefügt & kopiert" else "Schon vorhanden – in Ablage kopiert"
        Snackbar.make(list, msg, Snackbar.LENGTH_SHORT).show()
    }
}

data class RegexItem(val title: String, val pattern: String, val example: String)
