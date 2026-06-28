package com.nvm.traplink

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class Notificaciones : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_notificaciones)

        val btnNavEventos = findViewById<TextView>(R.id.btnNavEventos)
        val btnNavDispositivos = findViewById<TextView>(R.id.btnNavDispositivos)

        btnNavDispositivos.setOnClickListener {
            val intent = Intent(this, Inicio::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0) // Evita parpadeos bruscos de transición
            finish()
        }

        btnNavEventos.setOnClickListener {
            val intent = Intent(this, Eventos::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0) // Quita la animación por defecto para simular pestañas nativas
            finish()
        }

    }
}