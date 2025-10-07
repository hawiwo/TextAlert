package com.example.textalert

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs, rootKey)

        findPreference<Preference>("manage_keywords")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), KeywordsActivity::class.java))
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = listView   // RecyclerView der Preference-Liste
        // Statusbar-Inset als Top-Padding anwenden
        ViewCompat.setOnApplyWindowInsetsListener(rv) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, top, v.paddingRight, v.paddingBottom)
            insets
        }
        rv.clipToPadding = false
        ViewCompat.requestApplyInsets(rv)
    }
}
