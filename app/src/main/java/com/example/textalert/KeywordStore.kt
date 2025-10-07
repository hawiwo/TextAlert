package com.example.textalert

import android.content.Context
import androidx.core.content.edit

class KeywordStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("kw", Context.MODE_PRIVATE)
    fun getAll(): List<String> = prefs.getStringSet("list", setOf())!!.toList()
    fun add(s: String) {
        val set = prefs.getStringSet("list", setOf())!!.toMutableSet()
        set.add(s)
        prefs.edit { putStringSet("list", set) }
    }
    fun remove(s: String) {
        val set = prefs.getStringSet("list", setOf())!!.toMutableSet()
        set.remove(s)
        prefs.edit { putStringSet("list", set) }
    }
    fun clear() {
        prefs.edit { putStringSet("list", setOf()) }
    }
}