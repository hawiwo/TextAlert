package com.example.textalert

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText

class KeywordsActivity : AppCompatActivity() {
    private lateinit var clearFab: FloatingActionButton
    private lateinit var list: RecyclerView
    private lateinit var fab: FloatingActionButton
    private lateinit var store: KeywordStore
    private lateinit var adapter: KeywordsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keywords)

        store = KeywordStore(this)
        list = findViewById(R.id.kwList)
        fab = findViewById(R.id.kwAdd)
        clearFab = findViewById(R.id.kwClear)

        adapter = KeywordsAdapter(store.getAll().sorted())
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))
        list.clipToPadding = false

        ViewCompat.setOnApplyWindowInsetsListener(list) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, bars.bottom)
            fun bumpBottomMargin(fabBtn: FloatingActionButton) {
                val lp = fabBtn.layoutParams as ViewGroup.MarginLayoutParams
                val target = bars.bottom + 16.dp()
                if (lp.bottomMargin != target) {
                    lp.bottomMargin = target
                    fabBtn.layoutParams = lp
                }
            }
            bumpBottomMargin(fab)
            bumpBottomMargin(clearFab)
            insets
        }
        ViewCompat.requestApplyInsets(list)

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val s = adapter.current[vh.bindingAdapterPosition]
                store.remove(s)
                adapter.setData(store.getAll().sorted())
            }
        }).attachToRecyclerView(list)

        fab.setOnClickListener {
            val container = layoutInflater.inflate(R.layout.dialog_add_keyword, null)
            val input = container.findViewById<TextInputEditText>(R.id.editKeyword)
            MaterialAlertDialogBuilder(this)
                .setTitle("Suchtext hinzufügen")
                .setView(container)
                .setPositiveButton("OK") { _, _ ->
                    val t = input.text?.toString()?.trim().orEmpty()
                    if (t.isEmpty()) {
                        Snackbar.make(list, "Bitte Text eingeben", Snackbar.LENGTH_SHORT).show()
                    } else {
                        val looksRegex = RegexUtils.looksLikeRegex(t)
                        val validRegex = if (looksRegex) RegexUtils.compileOrNull(t, ignoreCase = true) != null else true
                        if (!validRegex) {
                            Snackbar.make(list, "Ungültiges Regex-Muster – nicht gespeichert", Snackbar.LENGTH_LONG).show()
                        } else {
                            val added = store.add(t)
                            if (added) {
                                adapter.setData(store.getAll().sorted())
                                Snackbar.make(list, "Hinzugefügt: $t", Snackbar.LENGTH_SHORT).show()
                            } else {
                                Snackbar.make(list, "Schon vorhanden: $t", Snackbar.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        clearFab.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Alle Suchtexte löschen?")
                .setMessage("Diese Aktion kann nicht rückgängig gemacht werden.")
                .setPositiveButton("Löschen") { _, _ ->
                    store.clear()
                    adapter.setData(store.getAll().sorted())
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
