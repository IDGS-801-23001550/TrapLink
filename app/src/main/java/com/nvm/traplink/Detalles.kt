package com.nvm.traplink

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nvm.traplink.data.RequestComandoDto
import com.nvm.traplink.data.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Detalles : AppCompatActivity() {

    private var tokenGuardado: String = ""
    private var dispositivoId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_detalles)

        // 1. Vincular vistas del XML
        val tvDetalleNombre = findViewById<TextView>(R.id.tvDetalleNombre)
        val tvDetalleEstado = findViewById<TextView>(R.id.tvDetalleEstado)
        val tvDetalleBateria = findViewById<TextView>(R.id.tvDetalleBateria)
        val tvDetallePrediccion = findViewById<TextView>(R.id.tvDetallePrediccion)

        val btnLocalizar = findViewById<Button>(R.id.btnLocalizar)
        val btnConfirmarReal = findViewById<Button>(R.id.btnConfirmarReal)
        val btnFalsoPositivo = findViewById<Button>(R.id.btnFalsoPositivo)

        // 2. Recuperar Token de sesión para las peticiones
        val sharedPreferences = getSharedPreferences("TrapLinkPrefs", MODE_PRIVATE)
        tokenGuardado = sharedPreferences.getString("AUTH_TOKEN", "") ?: ""
        val tokenCompleto = "Bearer $tokenGuardado"

        // 3. Capturar los datos iniciales que mandó el RecyclerView
        dispositivoId = intent.getIntExtra("EXTRA_DISPOSITIVO_ID", -1)
        val nombre = intent.getStringExtra("EXTRA_NOMBRE") ?: "Dispositivo"
        val estado = intent.getStringExtra("EXTRA_ESTADO") ?: "Desconocido"

        // Asignación inicial en la interfaz
        tvDetalleNombre.text = nombre
        tvDetalleEstado.text = "Estado: $estado"
        tvDetalleBateria.text = "Carga Física: Cargando..."
        tvDetallePrediccion.text = "Vida útil estimada: Cargando..."

        // 4. Consumir las predicciones reales del modelo de IA en Azure
        if (dispositivoId != -1 && tokenGuardado.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val response = RetrofitClient.trapLinkService.getPrediccionesPorDispositivo(tokenCompleto, dispositivoId)

                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful && response.body() != null && response.body()!!.isNotEmpty()) {
                            // Suponiendo que el API nos regresa una lista, tomamos la última predicción generada
                            // Cambia lo que está dentro de response.isSuccessful por esto:
                            val ultimaPrediccion = response.body()!!.first()

                            val horasRestantes = ultimaPrediccion.horasBateriaEstimada ?: 0.0
                            val estadoPredicho = ultimaPrediccion.clasePredicha ?: "Estable"

                            tvDetalleBateria.text = "Carga Física: Restan aprox. ${horasRestantes.toInt()} horas"
                            tvDetallePrediccion.text = "Vida útil estimada: $estadoPredicho"
                        } else {
                            // Datos de respaldo si la trampa aún no genera telemetría de IA
                            tvDetalleBateria.text = "Carga Física: 100%"
                            tvDetallePrediccion.text = "Vida útil estimada: Estable (Sin alertas)"
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvDetalleBateria.text = "Carga Física: No disponible"
                        tvDetallePrediccion.text = "Vida útil estimada: Error al conectar"
                    }
                }
            }
        }

        // 5. EVENTO: Enviar comando de localización al hardware ESP32 real
        btnLocalizar.setOnClickListener {
            if (dispositivoId == -1) return@setOnClickListener

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val requestComando = RequestComandoDto(
                        targetId = dispositivoId.toString(),
                        comando = "LOCALIZAR"
                    )
                    val response = RetrofitClient.trapLinkService.enviarComando(tokenCompleto, requestComando)

                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@Detalles, "¡Comando enviado con éxito! Buscando nodo...", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@Detalles, "Azure rechazó el comando de localización.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@Detalles, "Error de red: No se pudo enviar el comando.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Acciones locales informativas por el momento
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