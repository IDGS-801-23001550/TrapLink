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
        val btnDetener = findViewById<Button>(R.id.btnDetener)
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

        // Botones para localizar y detener localizacón
        btnLocalizar.setOnClickListener {
            // Enviar comando de localización vía WebSocket
            enviarComandoWebSocket(nombre, "LOCALIZAR")
        }

        btnDetener.setOnClickListener {
            // Enviar comando para detener localización vía WebSocket
            enviarComandoWebSocket(nombre, "DETENER_LOCALIZAR")
        }

        // Botones para captura y falso positivo ambos con función de reset a la trampa
        btnConfirmarReal.setOnClickListener {
            tvDetalleEstado.text = "Estado: Captura Validada por Técnico"
            Toast.makeText(this, "Evento guardado como CAPTURA REAL.", Toast.LENGTH_SHORT).show()

            // Enviamos el comando para reiniciar la trampa
            enviarComandoWebSocket(nombre, "RESET_TRAMPA")
        }

        btnFalsoPositivo.setOnClickListener {
            tvDetalleEstado.text = "Estado: Falso Positivo Descartado"
            Toast.makeText(this, "Evento archivado como FALSO POSITIVO.", Toast.LENGTH_SHORT).show()

            // Enviamos el comando para reiniciar la trampa
            enviarComandoWebSocket(nombre, "RESET_TRAMPA")
        }
    } // Aquí termina el onCreate


     //Función conectar al WebSocket y poder enviar comando dinamicos
        fun enviarComandoWebSocket(targetId: String, comando: String) {
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url("wss://traplink20260702232427-gvasf4b8b4h0gdg5.canadacentral-01.azurewebsites.net/ws")
                .build()

            val webSocketListener = object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                    // Json usando las variables de la función
                    val jsonMessage = """
                        {
                          "action": "send_to",
                          "target": "$targetId",
                          "message": "$comando"
                        }
                    """.trimIndent()

                    webSocket.send(jsonMessage)

                    webSocket.close(1000, "Comando enviado")

                    runOnUiThread {
                        Toast.makeText(this@Detalles, "WS: Comando '$comando' enviado a $targetId", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                    runOnUiThread {
                        Toast.makeText(this@Detalles, "Respuesta WS: $text", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                    runOnUiThread {
                        Toast.makeText(this@Detalles, "Error WS: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            client.newWebSocket(request, webSocketListener)
        }
    }
