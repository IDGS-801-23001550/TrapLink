package com.nvm.traplink

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class DetallesCuenta : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("TrapLinkPrefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {

        // 1. Aplicar el tema guardado ANTES de inflar vistas
        val isDark = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_detalles_cuenta)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Solo el bottom recibe padding — el top se queda en 0
            // para que el header siga extendiéndose bajo la barra de estado
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        val btnEditarPerfil = findViewById<LinearLayout>(R.id.btnEditarPerfil)
        val btnRegistrar = findViewById<LinearLayout>(R.id.btnRegistrar)
        val btnNotificaciones = findViewById<LinearLayout>(R.id.btnNotificaciones)
        val btnCerrarSesion = findViewById<LinearLayout>(R.id.btnCerrarSesion)

        // Configurar el icono del Toggle de Tema según el modo actual
        val ivToggleIcon = findViewById<ImageView>(R.id.ivToggleTemaIcon)
        val btnToggleTema = findViewById<FrameLayout>(R.id.btnToggleTema)
        ivToggleIcon.setImageResource(if (isDark) R.drawable.ic_sun else R.drawable.ic_moon)

        btnToggleTema.setOnClickListener {
            val nuevoModoOscuro = !prefs.getBoolean("dark_mode", false)
            prefs.edit().putBoolean("dark_mode", nuevoModoOscuro).apply()

            AppCompatDelegate.setDefaultNightMode(
                if (nuevoModoOscuro) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            recreate()
        }

        btnEditarPerfil.setOnClickListener {
            val intent = Intent(this, EditarPerfil::class.java)
            startActivity(intent)
        }

        btnRegistrar.setOnClickListener {
            val intent = Intent(this, Registro::class.java)
            startActivity(intent)
        }

        btnCerrarSesion.setOnClickListener { view ->
            CerrarSesion.cerrarSesion(this)
        }
    }
}