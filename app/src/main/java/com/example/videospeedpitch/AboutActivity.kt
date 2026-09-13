package com.example.videospeedpitch

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela "Sobre": mostra a versão instalada e um histórico resumido do que já
 * foi implementado no app, como referência rápida sem precisar consultar o
 * histórico do git. A lista de histórico vem de [R.array.about_history_entries].
 */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        title = getString(R.string.about_title)

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
        findViewById<TextView>(R.id.tvVersion).text =
            getString(R.string.about_version_format, versionName ?: "-")

        val entries = resources.getStringArray(R.array.about_history_entries)
        findViewById<TextView>(R.id.tvHistory).text =
            entries.joinToString(separator = "\n\n") { "• $it" }
    }
}
