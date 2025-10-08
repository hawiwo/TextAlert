package com.example.textalert

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import androidx.recyclerview.widget.DividerItemDecoration

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
        adapter = KeywordsAdapter(store.getAll().sorted())
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))
        list.clipToPadding = false
        list.clipToPadding = false

        // Insets: Statusleiste oben als Padding, Navigationsleiste unten als Padding/Margin
        ViewCompat.setOnApplyWindowInsetsListener(list) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, bars.bottom)

            fun bumpBottomMargin(fab: FloatingActionButton) {
                val lp = fab.layoutParams as ViewGroup.MarginLayoutParams
                val target = bars.bottom + 16.dp()
                if (lp.bottomMargin != target) {
                    lp.bottomMargin = target
                    fab.layoutParams = lp
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
            val input = container.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editKeyword)
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Suchtext hinzufügen")
                .setView(container)
                .setPositiveButton("OK") { _, _ ->
                    val t = input.text?.toString()?.trim().orEmpty()
                    if (t.isNotEmpty()) {
                        val added = store.add(t)
                        if (added) {
                            adapter.setData(store.getAll().sorted())
                            com.google.android.material.snackbar.Snackbar.make(list, "Hinzugefügt: $t", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                        } else {
                            com.google.android.material.snackbar.Snackbar.make(list, "Schon vorhanden: $t", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }


        clearFab.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
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
