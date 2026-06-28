package com.nvm.traplink

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class Detalles : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_detalles)

        val tvDetalleNombre = findViewById<TextView>(R.id.tvDetalleNombre)
        val tvDetalleEstado = findViewById<TextView>(R.id.tvDetalleEstado)
        val tvDetalleBateria = findViewById<TextView>(R.id.tvDetalleBateria)
        val tvDetallePrediccion = findViewById<TextView>(R.id.tvDetallePrediccion)

        val btnLocalizar = findViewById<Button>(R.id.btnLocalizar)
        val btnConfirmarReal = findViewById<Button>(R.id.btnConfirmarReal)
        val btnFalsoPositivo = findViewById<Button>(R.id.btnFalsoPositivo)

        val nombre = intent.getStringExtra("EXTRA_NOMBRE") ?: "Dispositivo"
        val estado = intent.getStringExtra("EXTRA_ESTADO") ?: "OK"
        val bateria = intent.getStringExtra("EXTRA_BATERIA") ?: "100%"
        val prediccion = intent.getStringExtra("EXTRA_PREDICCION") ?: "Estable"

        tvDetalleNombre.text = nombre
        tvDetalleEstado.text = "Estado: $estado"
        tvDetalleBateria.text = "Batería: $bateria"
        tvDetallePrediccion.text = "Predicción de vida: $prediccion"

        btnLocalizar.setOnClickListener {
            Toast.makeText(this, "Comando enviado. Zumbador activado en el nodo.", Toast.LENGTH_SHORT).show()
        }

        btnConfirmarReal.setOnClickListener {
            tvDetalleEstado.text = "Estado: Captura Validada por Técnico"
            Toast.makeText(this, "Evento guardado como CAPTURA REAL.", Toast.LENGTH_SHORT).show()
        }

        btnFalsoPositivo.setOnClickListener {
            tvDetalleEstado.text = "Estado: Falso Positivo Descartado"
            Toast.makeText(this, "Evento archivado como FALSO POSITIVO.", Toast.LENGTH_SHORT).show()
        }
    }
}