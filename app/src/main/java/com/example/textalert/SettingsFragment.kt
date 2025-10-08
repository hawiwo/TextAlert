package com.example.textalert

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs, rootKey)

        // "Suchtexte verwalten"
        findPreference<Preference>("manage_keywords")?.setOnPreferenceClickListener {
            try {
                startActivity(Intent(requireContext(), KeywordsActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Kann KeywordsActivity nicht starten: ${e.message}", Toast.LENGTH_LONG).show()
            }
            true
        }

        // "Regex-Kurzhilfe"
        findPreference<Preference>("regex_help")?.setOnPreferenceClickListener {
            try {
                startActivity(Intent(requireContext(), RegexHelpActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Kann RegexHelpActivity nicht starten: ${e.message}", Toast.LENGTH_LONG).show()
            }
            true
        }
    }

    // Insets fix (damit nichts unter der Statusleiste liegt)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = listView
        ViewCompat.setOnApplyWindowInsetsListener(rv) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, top, v.paddingRight, v.paddingBottom)
            insets
        }
        rv.clipToPadding = false
        ViewCompat.requestApplyInsets(rv)
    }
}
