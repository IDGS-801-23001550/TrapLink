package com.nvm.traplink

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
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
    private var tieneAlertaActiva: Boolean = false

    private lateinit var tvDetalleEstado: TextView
    private lateinit var tvTituloAcciones: TextView
    private lateinit var btnConfirmarReal: Button
    private lateinit var btnFalsoPositivo: Button

    // Instancia de SharedPreferences compartida con el archivo centralizado
    private val prefs by lazy { getSharedPreferences("TrapLinkPrefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Inyectar tema activo ANTES del super.onCreate
        val isDark = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_detalles)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 2. Controladores del Toggle del Tema e Icono
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

        // 3. Botón de retroceso a Inicio
        findViewById<FrameLayout>(R.id.btnAtras).setOnClickListener {
            finish() // Finaliza esta actividad de subpestaña y regresa de forma limpia
        }

        // Vincular vistas restantes
        val tvDetalleNombre = findViewById<TextView>(R.id.tvDetalleNombre)
        tvDetalleEstado = findViewById<TextView>(R.id.tvDetalleEstado)
        val tvDetalleBateria = findViewById<TextView>(R.id.tvDetalleBateria)
        val tvDetallePrediccion = findViewById<TextView>(R.id.tvDetallePrediccion)

        val btnLocalizar = findViewById<Button>(R.id.btnLocalizar)
        val btnDetener = findViewById<Button>(R.id.btnDetener)

        tvTituloAcciones = findViewById<TextView>(R.id.tvTituloAcciones)
        btnConfirmarReal = findViewById<Button>(R.id.btnConfirmarReal)
        btnFalsoPositivo = findViewById<Button>(R.id.btnFalsoPositivo)

        // Recuperar Token utilizando la referencia lazy unificada
        tokenGuardado = prefs.getString("AUTH_TOKEN", "") ?: ""
        val tokenCompleto = "Bearer $tokenGuardado"

        // Capturar datos recibidos por el Intent
        dispositivoId = intent.getIntExtra("EXTRA_DISPOSITIVO_ID", -1)
        val nombre = intent.getStringExtra("EXTRA_NOMBRE") ?: "Dispositivo"
        val estadoBase = intent.getStringExtra("EXTRA_ESTADO") ?: "Monitoreando"

        tvDetalleNombre.text = nombre
        tvDetalleBateria.text = "Carga Física: Cargando..."
        tvDetallePrediccion.text = "Vida útil estimada: Cargando..."

        btnConfirmarReal.isEnabled = false
        btnFalsoPositivo.isEnabled = false

        // Consumir APIs con validación de color dinámico
        if (dispositivoId != -1 && tokenGuardado.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
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

                // Hilo B: Buscar Eventos Pendientes evaluando la conexión
                try {
                    if (estadoBase.equals("Desconectado", ignoreCase = true) || estadoBase.equals("Offline", ignoreCase = true)) {
                        withContext(Dispatchers.Main) {
                            tvDetalleEstado.text = "Estado: Desconectada"
                            // Cambiado a Gris Semántico de tus recursos para concordar con item_trampa
                            tvDetalleEstado.setTextColor(ContextCompat.getColor(this@Detalles, R.color.status_disconnected))
                            tieneAlertaActiva = false

                            tvTituloAcciones.visibility = View.GONE
                            btnConfirmarReal.visibility = View.GONE
                            btnFalsoPositivo.visibility = View.GONE
                        }
                    } else {
                        val responseEv = RetrofitClient.trapLinkService.getEventosPendientes(tokenCompleto)

                        withContext(Dispatchers.Main) {
                            if (responseEv.isSuccessful && responseEv.body() != null) {
                                val listaPendientes = responseEv.body()!!
                                val eventoDeEstaTrampa = listaPendientes.find { it.dispositivoID.toInt() == dispositivoId.toInt() }

                                if (eventoDeEstaTrampa != null) {
                                    eventoPendienteId = eventoDeEstaTrampa.eventoID
                                    tvDetalleEstado.text = "Estado: Alerta Activa (Falta Localizar)"
                                    // Rojo de captura
                                    tvDetalleEstado.setTextColor(ContextCompat.getColor(this@Detalles, R.color.status_capture))
                                    tieneAlertaActiva = true
                                } else {
                                    tvDetalleEstado.text = "Estado: Monitoreando (Sin novedades)"
                                    // Verde activo
                                    tvDetalleEstado.setTextColor(ContextCompat.getColor(this@Detalles, R.color.status_active))
                                    tieneAlertaActiva = false
                                }
                            } else {
                                tvDetalleEstado.text = "Estado: Monitoreando (Sin novedades)"
                                tvDetalleEstado.setTextColor(ContextCompat.getColor(this@Detalles, R.color.status_active))
                                tieneAlertaActiva = false
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TrapLinkError", "Fallo total en validar estados", e)
                }
            }
        }

        // Manejo de Comandos WS
        btnLocalizar.setOnClickListener {
            enviarComandoWebSocket(nombre, "LOCALIZAR")
            tvDetalleEstado.text = "Estado: Localizando dispositivo en campo..."
            tvDetalleEstado.setTextColor(ContextCompat.getColor(this, R.color.secondary))
        }

        btnDetener.setOnClickListener {
            enviarComandoWebSocket(nombre, "DETENER_LOCALIZAR")

            if (tieneAlertaActiva) {
                tvDetalleEstado.text = "Estado: Alerta Activa (Pendiente de Revisión)"
                tvDetalleEstado.setTextColor(ContextCompat.getColor(this, R.color.status_capture)) // Rojo

                tvTituloAcciones.visibility = View.VISIBLE
                btnConfirmarReal.visibility = View.VISIBLE
                btnFalsoPositivo.visibility = View.VISIBLE

                btnConfirmarReal.isEnabled = true
                btnFalsoPositivo.isEnabled = true

                Toast.makeText(this, "Trampa localizada. Acciones de apertura desbloqueadas.", Toast.LENGTH_SHORT).show()
            } else {
                tvDetalleEstado.text = "Estado: Monitoreando (Sin novedades)"
                tvDetalleEstado.setTextColor(ContextCompat.getColor(this, R.color.status_active)) // ¡Verde!
            }
        }

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

                        if (esReal) {
                            tvDetalleEstado.text = "Estado: Captura Validada"
                            tvDetalleEstado.setTextColor(ContextCompat.getColor(this@Detalles, R.color.status_capture))
                        } else {
                            tvDetalleEstado.text = "Estado: Falso Positivo Descartado"
                            tvDetalleEstado.setTextColor(ContextCompat.getColor(this@Detalles, R.color.status_disconnected))
                        }

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