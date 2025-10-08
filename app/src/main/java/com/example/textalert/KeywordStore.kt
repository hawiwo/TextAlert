package com.example.textalert

import android.content.Context
import androidx.core.content.edit

class KeywordStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("kw", Context.MODE_PRIVATE)

    fun getAll(): List<String> =
        prefs.getStringSet("list", emptySet())!!.toList()

    fun add(s: String): Boolean {
        val cur = prefs.getStringSet("list", emptySet()) ?: emptySet()
        if (cur.contains(s)) return false
        val copy = HashSet(cur)
        copy.add(s)
        prefs.edit { putStringSet("list", copy) }
        return true
    }

    fun remove(s: String) {
        val cur = prefs.getStringSet("list", emptySet()) ?: emptySet()
        if (!cur.contains(s)) return
        val copy = HashSet(cur)
        copy.remove(s)
        prefs.edit { putStringSet("list", copy) }
    }

    fun clear() {
        prefs.edit { putStringSet("list", emptySet()) }
    }
}
