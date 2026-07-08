package com.nvm.traplink

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.nvm.traplink.data.ConfirmarEventoDto
import com.nvm.traplink.data.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Detalles : AppCompatActivity() {

    private var tokenGuardado: String = ""
    private var dispositivoId: Int = -1
    private var eventoPendienteId: Long = -1
    private var tieneAlertaActiva: Boolean = false // Bandera de validación

    private lateinit var tvDetalleEstado: TextView
    private lateinit var tvTituloAcciones: TextView
    private lateinit var btnConfirmarReal: Button
    private lateinit var btnFalsoPositivo: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_detalles)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 1. Vincular vistas del XML
        val tvDetalleNombre = findViewById<TextView>(R.id.tvDetalleNombre)
        tvDetalleEstado = findViewById<TextView>(R.id.tvDetalleEstado)
        val tvDetalleBateria = findViewById<TextView>(R.id.tvDetalleBateria)
        val tvDetallePrediccion = findViewById<TextView>(R.id.tvDetallePrediccion)

        val btnLocalizar = findViewById<Button>(R.id.btnLocalizar)
        val btnDetener = findViewById<Button>(R.id.btnDetener)

        tvTituloAcciones = findViewById<TextView>(R.id.tvTituloAcciones)
        btnConfirmarReal = findViewById<Button>(R.id.btnConfirmarReal)
        btnFalsoPositivo = findViewById<Button>(R.id.btnFalsoPositivo)

        // 2. Recuperar Token de sesión
        val sharedPreferences = getSharedPreferences("TrapLinkPrefs", MODE_PRIVATE)
        tokenGuardado = sharedPreferences.getString("AUTH_TOKEN", "") ?: ""
        val tokenCompleto = "Bearer $tokenGuardado"

        // 3. Capturar datos del Intent
        dispositivoId = intent.getIntExtra("EXTRA_DISPOSITIVO_ID", -1)
        val nombre = intent.getStringExtra("EXTRA_NOMBRE") ?: "Dispositivo"
        val estadoBase = intent.getStringExtra("EXTRA_ESTADO") ?: "Monitoreando" // "Desconectado", "Monitoreando", etc.

        tvDetalleNombre.text = nombre
        tvDetalleBateria.text = "Carga Física: Cargando..."
        tvDetallePrediccion.text = "Vida útil estimada: Cargando..."

        // Por defecto, nos aseguramos que en código mantengan los estados de deshabilitados
        btnConfirmarReal.isEnabled = false
        btnFalsoPositivo.isEnabled = false

        // 4. Consumir APIs
        if (dispositivoId != -1 && tokenGuardado.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                // Hilo A: Cargar Predicciones de IA
                try {
                    val responsePred = RetrofitClient.trapLinkService.getPrediccionesPorDispositivo(tokenCompleto, dispositivoId)
                    withContext(Dispatchers.Main) {
                        if (responsePred.isSuccessful && !responsePred.body().isNullOrEmpty()) {
                            val ultimaPrediccion = responsePred.body()!!.first()
                            val horasRestantes = ultimaPrediccion.horasBateriaEstimada ?: 0.0
                            val estadoPredicho = ultimaPrediccion.clasePredicha ?: "Estable"
                            tvDetalleBateria.text = "Carga Física: Restan aprox. ${horasRestantes.toInt()} horas"
                            tvDetallePrediccion.text = "Vida útil estimada: $estadoPredicho"
                        } else {
                            tvDetalleBateria.text = "Carga Física: 100%"
                            tvDetallePrediccion.text = "Vida útil estimada: Estable"
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvDetalleBateria.text = "Carga Física: No disponible"
                        tvDetallePrediccion.text = "Vida útil estimada: Error"
                    }
                }

                // Hilo B: Buscar Eventos Pendientes de esta trampa específica evaluando la conexión
                try {
                    // Si desde el inicio el backend sabe que está desconectada, pintamos "Desconectada" de una vez
                    if (estadoBase.equals("Desconectado", ignoreCase = true) || estadoBase.equals("Offline", ignoreCase = true)) {
                        withContext(Dispatchers.Main) {
                            tvDetalleEstado.text = "Estado: Desconectada"
                            tvDetalleEstado.setTextColor(android.graphics.Color.RED) // Opcional: Resaltar en rojo
                            tieneAlertaActiva = false

                            // Nos aseguramos que todo el bloque de acciones se esconda por seguridad
                            tvTituloAcciones.visibility = View.GONE
                            btnConfirmarReal.visibility = View.GONE
                            btnFalsoPositivo.visibility = View.GONE
                        }
                    } else {
                        // SI ESTÁ CONECTADA: Validamos si tiene alertas en la API de Ricardo
                        val responseEv = RetrofitClient.trapLinkService.getEventosPendientes(tokenCompleto)

                        withContext(Dispatchers.Main) {
                            if (responseEv.isSuccessful && responseEv.body() != null) {
                                val listaPendientes = responseEv.body()!!
                                val eventoDeEstaTrampa = listaPendientes.find { it.dispositivoID.toInt() == dispositivoId.toInt() }

                                if (eventoDeEstaTrampa != null) {
                                    eventoPendienteId = eventoDeEstaTrampa.eventoID
                                    tvDetalleEstado.text = "Estado: Alerta Activa (Falta Localizar)"
                                    tieneAlertaActiva = true
                                } else {
                                    // CASO: Conectada pero sin eventos activos
                                    tvDetalleEstado.text = "Estado: Monitoreando (Sin novedades)"
                                    tieneAlertaActiva = false
                                }
                            } else {
                                // Respaldar el estado por si la API falla pero sabemos que está conectada
                                tvDetalleEstado.text = "Estado: Monitoreando (Sin novedades)"
                                tieneAlertaActiva = false
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TrapLinkError", "Fallo total en Hilo B al validar estados", e)
                }

            }
        }

        // --- MANEJO DE COMANDOS WEBSOCKET ---
        btnLocalizar.setOnClickListener {
            enviarComandoWebSocket(nombre, "LOCALIZAR")
            tvDetalleEstado.text = "Estado: Localizando dispositivo en campo..."
        }

        btnDetener.setOnClickListener {
            enviarComandoWebSocket(nombre, "DETENER_LOCALIZAR")

            // Lógica de validación de flujo seguro:
            if (tieneAlertaActiva) {
                tvDetalleEstado.text = "Estado: Alerta Activa (Pendiente de Revisión)"

                // Hacemos visibles los elementos de UI
                tvTituloAcciones.visibility = View.VISIBLE
                btnConfirmarReal.visibility = View.VISIBLE
                btnFalsoPositivo.visibility = View.VISIBLE

                // Los habilitamos para el clic
                btnConfirmarReal.isEnabled = true
                btnFalsoPositivo.isEnabled = true

                Toast.makeText(this, "Trampa localizada. Acciones de apertura desbloqueadas.", Toast.LENGTH_SHORT).show()
            } else {
                tvDetalleEstado.text = "Estado: Monitoreando (Sin novedades)"
            }
        }

        // --- LOGICA DE BOTONES DE ACCIÓN DE CAMPO DE RICARDO ---
        btnConfirmarReal.setOnClickListener {
            ejecutarConfirmacionEnAzure(true, nombre)
        }

        btnFalsoPositivo.setOnClickListener {
            ejecutarConfirmacionEnAzure(false, nombre)
        }
    }

    private fun ejecutarConfirmacionEnAzure(esReal: Boolean, nombreDispositivo: String) {
        if (eventoPendienteId == -1L) {
            Toast.makeText(this, "No hay ningún evento activo por dictaminar.", Toast.LENGTH_SHORT).show()
            return
        }

        val tokenCompleto = "Bearer $tokenGuardado"
        val requestDto = ConfirmarEventoDto(esCapturaReal = esReal)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.trapLinkService.confirmarEvento(tokenCompleto, eventoPendienteId, requestDto)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val mensajeApi = if (esReal) "Marcado como CAPTURA REAL" else "Marcado como FALSO POSITIVO"
                        Toast.makeText(this@Detalles, mensajeApi, Toast.LENGTH_LONG).show()

                        tvDetalleEstado.text = if (esReal) "Estado: Captura Validada" else "Estado: Falso Positivo Descartado"

                        // Volvemos a ocultar el bloque completo para limpiar la interfaz tras finalizar el dictamen
                        tvTituloAcciones.visibility = View.GONE
                        btnConfirmarReal.visibility = View.GONE
                        btnFalsoPositivo.visibility = View.GONE

                        btnConfirmarReal.isEnabled = false
                        btnFalsoPositivo.isEnabled = false
                        tieneAlertaActiva = false

                        enviarComandoWebSocket(nombreDispositivo, "RESET_TRAMPA")
                    } else {
                        Toast.makeText(this@Detalles, "Error al dictaminar: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@Detalles, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun enviarComandoWebSocket(targetId: String, comando: String) {
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url("wss://traplink20260702232427-gvasf4b8b4h0gdg5.canadacentral-01.azurewebsites.net/ws")
            .build()

        val webSocketListener = object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
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
        }
        client.newWebSocket(request, webSocketListener)
    }
}