package com.nvm.traplink

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class Inicio : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_inicio)

        val cardTrampa1 = findViewById<LinearLayout>(R.id.cardTrampa1)
        val cardTrampa2 = findViewById<LinearLayout>(R.id.cardTrampa2)

        val btnNavEventos = findViewById<TextView>(R.id.btnNavEventos)
        val btnNavNotificaciones = findViewById<TextView>(R.id.btnNavNotificaciones)

        cardTrampa1.setOnClickListener {
            val intent = Intent(this, Detalles::class.java).apply {
                putExtra("EXTRA_NOMBRE", "Nodo Pasillo Norte")
                putExtra("EXTRA_ESTADO", "Activa/OK")
                putExtra("EXTRA_BATERIA", "95%")
                putExtra("EXTRA_PREDICCION", "240 días estimados")
            }
            startActivity(intent)
        }

        cardTrampa2.setOnClickListener {
            val intent = Intent(this, Detalles::class.java).apply {
                putExtra("EXTRA_NOMBRE", "Nodo Almacén Central")
                putExtra("EXTRA_ESTADO", "Captura Detectada")
                putExtra("EXTRA_BATERIA", "40%")
                putExtra("EXTRA_PREDICCION", "45 días estimados")
            }
            startActivity(intent)
        }

        btnNavEventos.setOnClickListener {
            val intent = Intent(this, Eventos::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0) // Quita la animación por defecto para simular pestañas nativas
            finish()
        }

        btnNavNotificaciones.setOnClickListener {
            val intent = Intent(this, Notificaciones::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }
    }
}