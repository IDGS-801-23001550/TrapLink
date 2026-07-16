package com.nvm.traplink

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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
    private lateinit var cardAccionesCampo: LinearLayout
    private lateinit var btnConfirmarReal: Button
    private lateinit var btnFalsoPositivo: Button

    // Vistas del ícono hero con pulso (ringDetalleOuter/Inner)
    private lateinit var ringDetalleOuter: View
    private lateinit var ringDetalleInner: View
    private var pulsoAnimators: List<AnimatorSet> = emptyList()

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
            finish()
        }

        // Vincular vistas restantes
        val tvHeaderTitulo = findViewById<TextView>(R.id.tvHeaderTitulo)
        val tvDetalleNombre = findViewById<TextView>(R.id.tvDetalleNombre)
        tvDetalleEstado = findViewById(R.id.tvDetalleEstado)
        val tvDetalleBateria = findViewById<TextView>(R.id.tvDetalleBateria)
        val tvDetallePrediccion = findViewById<TextView>(R.id.tvDetallePrediccion)
        val skeletonBateria = findViewById<View>(R.id.skeletonBateria)
        val skeletonPrediccion = findViewById<View>(R.id.skeletonPrediccion)

        val btnLocalizar = findViewById<Button>(R.id.btnLocalizar)
        val btnDetener = findViewById<Button>(R.id.btnDetener)

        cardAccionesCampo = findViewById(R.id.cardAccionesCampo)
        btnConfirmarReal = findViewById(R.id.btnConfirmarReal)
        btnFalsoPositivo = findViewById(R.id.btnFalsoPositivo)

        ringDetalleOuter = findViewById(R.id.ringDetalleOuter)
        ringDetalleInner = findViewById(R.id.ringDetalleInner)

        // Recuperar Token utilizando la referencia lazy unificada
        tokenGuardado = prefs.getString("AUTH_TOKEN", "") ?: ""
        val tokenCompleto = "Bearer $tokenGuardado"

        // Capturar datos recibidos por el Intent
        dispositivoId = intent.getIntExtra("EXTRA_DISPOSITIVO_ID", -1)
        val nombre = intent.getStringExtra("EXTRA_NOMBRE") ?: "Dispositivo"
        val estadoBase = intent.getStringExtra("EXTRA_ESTADO") ?: "Monitoreando"

        tvHeaderTitulo.text = nombre
        tvDetalleNombre.text = nombre

        iniciarShimmerSkeleton(skeletonBateria)
        iniciarShimmerSkeleton(skeletonPrediccion)
        animarEntradaDetalles()

        btnConfirmarReal.isEnabled = false
        btnFalsoPositivo.isEnabled = false
        cardAccionesCampo.visibility = View.GONE

        // Consumir APIs con validación de color dinámico
        if (dispositivoId != -1 && tokenGuardado.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val responsePred = RetrofitClient.trapLinkService.getPrediccionesPorDispositivo(tokenCompleto, dispositivoId)
                    withContext(Dispatchers.Main) {
                        detenerShimmerSkeleton(skeletonBateria)
                        detenerShimmerSkeleton(skeletonPrediccion)
                        tvDetalleBateria.visibility = View.VISIBLE
                        tvDetallePrediccion.visibility = View.VISIBLE

                        if (responsePred.isSuccessful && !responsePred.body().isNullOrEmpty()) {
                            val ultimaPrediccion = responsePred.body()!!.first()
                            val horasRestantes = ultimaPrediccion.horasBateriaEstimada ?: 0.0
                            val estadoPredicho = ultimaPrediccion.clasePredicha ?: "Estable"
                            tvDetalleBateria.text = "Restan ${horasRestantes.toInt()}h"
                            tvDetallePrediccion.text = estadoPredicho
                        } else {
                            tvDetalleBateria.text = "100%"
                            tvDetallePrediccion.text = "Estable"
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        detenerShimmerSkeleton(skeletonBateria)
                        detenerShimmerSkeleton(skeletonPrediccion)
                        tvDetalleBateria.visibility = View.VISIBLE
                        tvDetallePrediccion.visibility = View.VISIBLE
                        tvDetalleBateria.text = "No disponible"
                        tvDetallePrediccion.text = "Error"
                    }
                }

                // Hilo B: Buscar Eventos Pendientes evaluando la conexión
                try {
                    if (estadoBase.equals("Desconectado", ignoreCase = true) || estadoBase.equals("Offline", ignoreCase = true)) {
                        withContext(Dispatchers.Main) {
                            actualizarEstadoChip("Desconectada", R.drawable.bg_chip_offline, R.color.chip_text_offline)
                            tieneAlertaActiva = false
                            cardAccionesCampo.visibility = View.GONE
                            detenerPulso()
                        }
                    } else {
                        val responseEv = RetrofitClient.trapLinkService.getEventosPendientes(tokenCompleto)

                        withContext(Dispatchers.Main) {
                            if (responseEv.isSuccessful && responseEv.body() != null) {
                                val listaPendientes = responseEv.body()!!
                                val eventoDeEstaTrampa = listaPendientes.find { it.dispositivoID.toInt() == dispositivoId.toInt() }

                                if (eventoDeEstaTrampa != null) {
                                    eventoPendienteId = eventoDeEstaTrampa.eventoID
                                    actualizarEstadoChip("Alerta Activa (Falta Localizar)", R.drawable.bg_chip_capture, R.color.chip_text_capture)
                                    tieneAlertaActiva = true
                                    iniciarPulso()
                                } else {
                                    actualizarEstadoChip("Monitoreando (Sin novedades)", R.drawable.bg_chip_active, R.color.chip_text_active)
                                    tieneAlertaActiva = false
                                    detenerPulso()
                                }
                            } else {
                                actualizarEstadoChip("Monitoreando (Sin novedades)", R.drawable.bg_chip_active, R.color.chip_text_active)
                                tieneAlertaActiva = false
                                detenerPulso()
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TrapLinkError", "Fallo total en validar estados", e)
                    // IMPORTANTE: Si falla la red, aseguramos el hilo principal para no romper la UI
                    withContext(Dispatchers.Main) {
                        actualizarEstadoChip("Error al verificar alertas", R.drawable.bg_chip_offline, R.color.chip_text_offline)
                        tieneAlertaActiva = false
                        detenerPulso()
                    }
                }


            }
        }

        // Manejo de Comandos WS
        btnLocalizar.setOnClickListener {
            enviarComandoWebSocket(nombre, "LOCALIZAR")
            actualizarEstadoChip("Localizando dispositivo en campo...", R.drawable.bg_chip_battery, R.color.chip_text_battery)
            iniciarPulso()
        }

        btnDetener.setOnClickListener {
            enviarComandoWebSocket(nombre, "DETENER_LOCALIZAR")

            // Validamos si la variable es true O si el chip de estado actual ya indicaba una alerta o localización activa
            val textoEstadoActual = tvDetalleEstado.text.toString()
            val tieneAlertaVisual = textoEstadoActual.contains("Alerta", ignoreCase = true) ||
                    textoEstadoActual.contains("Localizando", ignoreCase = true)

            if (tieneAlertaActiva || tieneAlertaVisual) {
                actualizarEstadoChip("Alerta Activa (Pendiente de Revisión)", R.drawable.bg_chip_capture, R.color.chip_text_capture)

                cardAccionesCampo.visibility = View.VISIBLE
                btnConfirmarReal.isEnabled = true
                btnFalsoPositivo.isEnabled = true

                Toast.makeText(this, "Trampa localizada. Acciones de apertura desbloqueadas.", Toast.LENGTH_SHORT).show()
            } else {
                actualizarEstadoChip("Monitoreando (Sin novedades)", R.drawable.bg_chip_active, R.color.chip_text_active)
                detenerPulso()
            }
        }

        btnConfirmarReal.setOnClickListener {
            ejecutarConfirmacionEnAzure(true, nombre)
        }

        btnFalsoPositivo.setOnClickListener {
            ejecutarConfirmacionEnAzure(false, nombre)
        }
    }

    /**
     * Actualiza el chip de estado (texto + fondo + color de texto) en una sola llamada,
     * reutilizando los mismos drawables/colores de chip que ya usa item_trampa.
     */
    private fun actualizarEstadoChip(texto: String, bgRes: Int, colorRes: Int) {
        tvDetalleEstado.text = texto
        tvDetalleEstado.setBackgroundResource(bgRes)
        tvDetalleEstado.setTextColor(ContextCompat.getColor(this, colorRes))
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
                            actualizarEstadoChip("Captura Validada", R.drawable.bg_chip_capture, R.color.chip_text_capture)
                        } else {
                            actualizarEstadoChip("Falso Positivo Descartado", R.drawable.bg_chip_offline, R.color.chip_text_offline)
                        }

                        cardAccionesCampo.visibility = View.GONE
                        btnConfirmarReal.isEnabled = false
                        btnFalsoPositivo.isEnabled = false
                        tieneAlertaActiva = false
                        detenerPulso()

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

    // ===================== SKELETON LOADER (tiles de info) =====================

    /**
     * Aplica un shimmer (pulso de opacidad en loop) a cualquier View skeleton
     * mientras se espera la respuesta del API. Reutilizable para ambos tiles.
     */
    private fun iniciarShimmerSkeleton(view: View) {
        view.animate()
            .alpha(0.4f)
            .setDuration(700)
            .withEndAction {
                if (view.visibility == View.VISIBLE) {
                    view.animate()
                        .alpha(1f)
                        .setDuration(700)
                        .withEndAction { iniciarShimmerSkeleton(view) }
                        .start()
                }
            }
            .start()
    }

    private fun detenerShimmerSkeleton(view: View) {
        view.animate().cancel()
        view.visibility = View.GONE
        view.alpha = 1f
    }

    // ===================== ANIMACIÓN DE ENTRADA (hero card + tiles) =====================

    /**
     * Anima la tarjeta hero y los tiles de info con fade + slide sutil al
     * abrir la pantalla, para que no aparezcan "de golpe".
     */
    private fun animarEntradaDetalles() {
        val heroCard = findViewById<View>(R.id.heroCard)
        val infoTilesRow = findViewById<View>(R.id.infoTilesRow)

        listOf(heroCard, infoTilesRow).forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 30f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setStartDelay((index * 100).toLong())
                .start()
        }
    }

    // ===================== PULSO DEL ÍCONO HERO (alerta activa / localizando) =====================

    private fun iniciarPulso() {
        detenerPulso() // evita solapar animadores si ya estaba corriendo

        ringDetalleOuter.visibility = View.VISIBLE
        ringDetalleInner.visibility = View.VISIBLE

        val rings = listOf(ringDetalleOuter, ringDetalleInner)
        val delays = listOf(0L, 500L)

        pulsoAnimators = rings.mapIndexed { index, ring ->
            ring.scaleX = 0.5f
            ring.scaleY = 0.5f
            ring.alpha = 0f

            val scaleX = ObjectAnimator.ofFloat(ring, "scaleX", 0.5f, 1f)
            val scaleY = ObjectAnimator.ofFloat(ring, "scaleY", 0.5f, 1f)
            val alpha = ObjectAnimator.ofFloat(ring, "alpha", 0.6f, 0f)

            AnimatorSet().apply {
                playTogether(scaleX, scaleY, alpha)
                duration = 1400
                startDelay = delays[index]
                interpolator = LinearInterpolator()
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (ringDetalleOuter.visibility == View.VISIBLE) {
                            start()
                        }
                    }
                })
                start()
            }
        }
    }

    private fun detenerPulso() {
        pulsoAnimators.forEach { it.cancel() }
        if (::ringDetalleOuter.isInitialized) {
            ringDetalleOuter.visibility = View.INVISIBLE
            ringDetalleInner.visibility = View.INVISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detenerPulso()
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