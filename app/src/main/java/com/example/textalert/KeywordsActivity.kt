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

class KeywordsActivity : AppCompatActivity() {
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

        adapter = KeywordsAdapter(store.getAll().sorted())
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.clipToPadding = false

        // Insets: Statusleiste oben als Padding, Navigationsleiste unten als Padding/Margin
        ViewCompat.setOnApplyWindowInsetsListener(list) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, bars.bottom)
            val lp = fab.layoutParams as ViewGroup.MarginLayoutParams
            if (lp.bottomMargin != bars.bottom + 16.dp()) {
                lp.bottomMargin = bars.bottom + 16.dp() // 16dp zusätzlich über der Nav-Bar
                fab.layoutParams = lp
            }
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
            val input = TextInputEditText(this)
            MaterialAlertDialogBuilder(this)
                .setTitle("Suchtext hinzufügen")
                .setView(input)
                .setPositiveButton("OK") { _, _ ->
                    val t = input.text?.toString()?.trim().orEmpty()
                    if (t.isNotEmpty()) {
                        store.add(t)
                        adapter.setData(store.getAll().sorted())
                    }
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
