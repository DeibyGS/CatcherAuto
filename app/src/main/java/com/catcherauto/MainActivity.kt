package com.catcherauto

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS = "catcherauto_prefs"

        val SWITCHES = mapOf(
            "pref_burger_king_loranca" to R.id.switchBurgerKingLoranca,
            "pref_burger_king_naranjo" to R.id.switchBurgerKingNaranjo,
            "pref_carlos"              to R.id.switchCarlos,
            "pref_papa_john"           to R.id.switchPapaJohn,
            "pref_telepizza"           to R.id.switchTelepizza,
            "pref_popeyes"             to R.id.switchPopeyes,
            "pref_kfc"                 to R.id.switchKfc,
            "pref_lastmile"            to R.id.switchLastmile
        )
    }

    private var pulseAnimator: AnimatorSet? = null
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Botones de permisos
        findViewById<View>(R.id.btnEnable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Botón ON/OFF escaneo
        findViewById<MaterialCardView>(R.id.btnToggleEscaneo).setOnClickListener {
            if (CatcherAutoService.instance == null) {
                Toast.makeText(this, "Servicio desconectado — reactivá la accesibilidad", Toast.LENGTH_SHORT).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                CatcherAutoService.instance?.toggleEscaneo()
            }
            actualizarEstado()
        }

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        for ((key, viewId) in SWITCHES) {
            val sw = findViewById<SwitchMaterial>(viewId)
            sw.isChecked = prefs.getBoolean(key, true)
            sw.setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(key, checked).apply()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        actualizarEstado()
        // Si el proceso acaba de reiniciarse, onServiceConnected puede tardar 1-2s
        // Reintentar la UI por si instance llega tarde
        uiHandler.postDelayed({ actualizarEstado() }, 1000)
        uiHandler.postDelayed({ actualizarEstado() }, 2500)
    }

    override fun onPause() {
        super.onPause()
        pulseAnimator?.cancel()
        uiHandler.removeCallbacksAndMessages(null)
    }

    private fun actualizarEstado() {
        val tvStatus     = findViewById<TextView>(R.id.tvStatus)
        val tvToggle     = findViewById<TextView>(R.id.tvToggleLabel)
        val btnToggle    = findViewById<MaterialCardView>(R.id.btnToggleEscaneo)
        val acces        = servicioAccesibilidadActivo()
        val service      = CatcherAutoService.instance
        val escaneando   = service?.escaneoActivo ?: false

        when {
            !acces -> {
                tvStatus.text = "[ INACTIVO ]\nACTIVAR ACCESIBILIDAD"
                tvStatus.setTextColor(0xFFFF3D57.toInt())
                detenerPulso()
                tvToggle.text = "▶  ACTIVAR ESCANEO"
                tvToggle.setTextColor(0xFFFF3D57.toInt())
                btnToggle.setCardBackgroundColor(0xFF2A1B1B.toInt())
            }
            else -> {
                if (escaneando) {
                    tvStatus.text = "[ ACTIVO ]\nDETECTANDO PEDIDOS"
                    tvStatus.setTextColor(0xFF00E5FF.toInt())
                    iniciarPulso()
                    tvToggle.text = "⏸  PAUSAR ESCANEO"
                    tvToggle.setTextColor(0xFF00E5FF.toInt())
                    btnToggle.setCardBackgroundColor(0xFF1B2A1B.toInt())
                } else {
                    tvStatus.text = "[ PAUSADO ]\nESCANEO DETENIDO"
                    tvStatus.setTextColor(0xFFFF3D57.toInt())
                    detenerPulso()
                    tvToggle.text = "▶  ACTIVAR ESCANEO"
                    tvToggle.setTextColor(0xFFFF3D57.toInt())
                    btnToggle.setCardBackgroundColor(0xFF2A1B1B.toInt())
                }
            }
        }
    }

    // Anillo de pulso: escala y desvanece en bucle cuando el servicio está activo
    private fun iniciarPulso() {
        val pulseRing = findViewById<View>(R.id.pulseRing)
        pulseAnimator?.cancel()

        val scaleX = ObjectAnimator.ofFloat(pulseRing, "scaleX", 1f, 2.2f).apply {
            repeatCount = ValueAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(pulseRing, "scaleY", 1f, 2.2f).apply {
            repeatCount = ValueAnimator.INFINITE
        }
        val alpha  = ObjectAnimator.ofFloat(pulseRing, "alpha", 0.55f, 0f).apply {
            repeatCount = ValueAnimator.INFINITE
        }

        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1600
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun detenerPulso() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        findViewById<View>(R.id.pulseRing).alpha = 0f
    }

    private fun servicioAccesibilidadActivo(): Boolean {
        val habilitados = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return habilitados.contains("com.catcherauto/com.catcherauto.CatcherAutoService")
    }
}
