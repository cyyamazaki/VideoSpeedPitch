package com.example.videospeedpitch

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela de abertura do app: nome, versão e mês/ano de lançamento sobre uma
 * foto de fundo, mostrada brevemente antes de abrir a tela inicial de
 * verdade ([MainActivity]). É esta Activity — não a [MainActivity] — que é
 * o launcher no [AndroidManifest].
 */
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val SPLASH_DURATION_MS = 1800L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
        findViewById<TextView>(R.id.tvSplashVersion).text = getString(
            R.string.splash_version_info,
            versionName ?: "-",
            getString(R.string.splash_build_date)
        )

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, SPLASH_DURATION_MS)
    }
}
