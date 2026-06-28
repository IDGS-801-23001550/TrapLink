package com.nvm.traplink

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class Eventos : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_eventos)
        val viewGrafica = findViewById<View>(R.id.viewGrafica)
        val btnNavDispositivos = findViewById<TextView>(R.id.btnNavDispositivos)
        val btnNavNotificaciones = findViewById<TextView>(R.id.btnNavNotificaciones)

        viewGrafica.setOnClickListener {
            Toast.makeText(
                this,
                "Actualizando telemetría... (Datos analíticos)",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnNavDispositivos.setOnClickListener {
            val intent = Intent(this, Inicio::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0) // Evita parpadeos bruscos de transición
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