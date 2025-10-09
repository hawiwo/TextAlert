package com.example.textalert

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RegexAdapter(private val data: List<RegexItem>, private val onCopy: OnCopy) :
    RecyclerView.Adapter<RegexAdapter.VH>() {

    interface OnCopy { fun copy(pattern: String) }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.rxTitle)
        val pattern: TextView = v.findViewById(R.id.rxPattern)
        val example: TextView = v.findViewById(R.id.rxExample)
        val btn: Button = v.findViewById(R.id.rxCopy)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_regex, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = data[pos]
        h.title.text = item.title
        h.pattern.text = item.pattern
        h.example.text = item.example
        h.pattern.setTextIsSelectable(true)
        h.example.setTextIsSelectable(true)

        // Klick: in die Suchliste hinzufügen (und in Ablage kopieren)
        h.btn.text = "Hinzufügen"
        h.btn.setOnClickListener { onCopy.copy(item.pattern) }

        // Optional: Langer Druck → nur kopieren (kein Hinzufügen)
        h.btn.setOnLongClickListener {
            val cm = it.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("regex", item.pattern))
            true
        }
    }

    override fun getItemCount() = data.size
}
