package com.nvm.traplink

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
import com.nvm.traplink.data.DesvincularRequestDto
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
            // Solo el bottom recibe padding — el top se queda en 0
            // para que el header siga extendiéndose bajo la barra de estado
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
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

        // Log inicial para verificar si se recibió correctamente el ID de la trampa
        Log.d("TrapLinkDebug", "=== Detalles Activity Iniciada ===")
        Log.d("TrapLinkDebug", "Intent EXTRA_DISPOSITIVO_ID: $dispositivoId")
        Log.d("TrapLinkDebug", "Intent EXTRA_NOMBRE: $nombre")
        Log.d("TrapLinkDebug", "Intent EXTRA_ESTADO: $estadoBase")

        //tvHeaderTitulo.text = nombre
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
                        Log.d("TrapLinkDebug", "Consumiendo getEventosPendientes...")
                        val responseEv = RetrofitClient.trapLinkService.getEventosPendientes(tokenCompleto)

                        withContext(Dispatchers.Main) {
                            if (responseEv.isSuccessful && responseEv.body() != null) {
                                val listaPendientes = responseEv.body()!!
                                Log.d("TrapLinkDebug", "API exitosa. Cantidad de pendientes devueltos: ${listaPendientes.size}")

                                // Pintar en consola el contenido exacto de lo que está regresando el JSON
                                listaPendientes.forEachIndexed { index, ev ->
                                    Log.d("TrapLinkDebug", "Pendiente [$index] -> EventoID: ${ev.eventoID}, DispositivoID: ${ev.dispositivoID}")
                                }

                                // Búsqueda de coincidencia controlando posibles fallos de parseo
                                val eventoDeEstaTrampa = listaPendientes.find {
                                    try {
                                        val match = it.dispositivoID.toInt() == dispositivoId
                                        Log.d("TrapLinkDebug", "Comparando: Evento de Trampa ID [${it.dispositivoID}] con ID Actual [$dispositivoId] -> Resultado match: $match")
                                        match
                                    } catch (e: Exception) {
                                        Log.e("TrapLinkDebug", "Error al parsear dispositivoID: '${it.dispositivoID}' a entero", e)
                                        false
                                    }
                                }

                                if (eventoDeEstaTrampa != null) {
                                    eventoPendienteId = eventoDeEstaTrampa.eventoID
                                    Log.d("TrapLinkDebug", "¡Match exitoso! eventoPendienteId asignado a: $eventoPendienteId")

                                    actualizarEstadoChip("Alerta Activa (Falta Localizar)", R.drawable.bg_chip_capture, R.color.chip_text_capture)
                                    tieneAlertaActiva = true
                                    iniciarPulso()
                                } else {
                                    Log.w("TrapLinkDebug", "No se encontró ningún evento pendiente en el JSON que coincida con el dispositivoId: $dispositivoId")
                                    actualizarEstadoChip("Monitoreando (Sin novedades)", R.drawable.bg_chip_active, R.color.chip_text_active)
                                    tieneAlertaActiva = false
                                    detenerPulso()
                                }
                            } else {
                                Log.e("TrapLinkDebug", "La llamada API no fue exitosa. Código: ${responseEv.code()} o el cuerpo vino nulo.")
                                actualizarEstadoChip("Monitoreando (Sin novedades)", R.drawable.bg_chip_active, R.color.chip_text_active)
                                tieneAlertaActiva = false
                                detenerPulso()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TrapLinkError", "Fallo total en validar estados", e)
                    withContext(Dispatchers.Main) {
                        actualizarEstadoChip("Error al verificar alertas", R.drawable.bg_chip_offline, R.color.chip_text_offline)
                        tieneAlertaActiva = false
                        detenerPulso()
                    }
                }
            }
        } else {
            Log.w("TrapLinkDebug", "No se consumieron las APIs. dispositivoId: $dispositivoId, token vacío: ${tokenGuardado.isEmpty()}")
        }

        // Dentro de onCreate() o la función donde inicializas las vistas de Detalles.kt:
        val btnDesvincular = findViewById<LinearLayout>(R.id.btnDesvincular)

        btnDesvincular.setOnClickListener {
            mostrarDialogoConfirmacionDesvincular()
        }

        // Manejo de Comandos WS
        btnLocalizar.setOnClickListener {
            Toast.makeText(this, "Enviando señal de localización...", Toast.LENGTH_SHORT).show()

            enviarComandoWebSocket(nombre, "LOCALIZAR")
            actualizarEstadoChip("Localizando dispositivo en campo...", R.drawable.bg_chip_battery, R.color.chip_text_battery)
            iniciarPulso()
        }

        btnDetener.setOnClickListener {
            enviarComandoWebSocket(nombre, "DETENER_LOCALIZAR")

            val textoEstadoActual = tvDetalleEstado.text.toString()
            val tieneAlertaVisual = textoEstadoActual.contains("Alerta", ignoreCase = true) ||
                    textoEstadoActual.contains("Localizando", ignoreCase = true)

            Log.d("TrapLinkDebug", "Click en DETENER. tieneAlertaActiva: $tieneAlertaActiva, tieneAlertaVisual: $tieneAlertaVisual, eventoPendienteId: $eventoPendienteId")

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
            Log.d("TrapLinkDebug", "Click Confirmar Real. Mandando dictamen con eventoPendienteId: $eventoPendienteId")
            ejecutarConfirmacionEnAzure(true, nombre)
        }

        btnFalsoPositivo.setOnClickListener {
            Log.d("TrapLinkDebug", "Click Falso Positivo. Mandando dictamen con eventoPendienteId: $eventoPendienteId")
            ejecutarConfirmacionEnAzure(false, nombre)
        }
    }

    private fun actualizarEstadoChip(texto: String, bgRes: Int, colorRes: Int) {
        tvDetalleEstado.text = texto
        tvDetalleEstado.setBackgroundResource(bgRes)
        tvDetalleEstado.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun ejecutarConfirmacionEnAzure(esReal: Boolean, nombreDispositivo: String) {
        Log.d("TrapLinkDebug", "ejecutarConfirmacionEnAzure. esReal: $esReal, ID a procesar: $eventoPendienteId")
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
                        Log.e("TrapLinkDebug", "Error API dictaminar. Código de respuesta: ${response.code()}")
                        Toast.makeText(this@Detalles, "Error al dictaminar: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("TrapLinkDebug", "Fallo de red en confirmación Azure", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@Detalles, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ===================== SKELETON LOADER =====================

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

    // ===================== ANIMACIÓN DE ENTRADA =====================

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

    // ===================== PULSO DEL ÍCONO HERO =====================

    private fun iniciarPulso() {
        detenerPulso()

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
            .url("wss://traplinkapi-abczfsdbc6dfcaap.canadacentral-01.azurewebsites.net/ws")
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
            }
        }
        client.newWebSocket(request, webSocketListener)
    }

    private fun mostrarDialogoConfirmacionDesvincular() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Desvincular Dispositivo")
            .setMessage("¿Estás seguro de que deseas desvincular esta trampa? Dejará de enviar lecturas a tu cuenta.")
            .setPositiveButton("Desvincular") { _, _ ->
                ejecutarDesvinculacion()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ejecutarDesvinculacion() {
        val prefs = getSharedPreferences("TrapLinkPrefs", MODE_PRIVATE)
        val token = prefs.getString("AUTH_TOKEN", "") ?: ""

        // Obtenemos el número de serie cargado previamente en el Intent o variable global
        val numeroSerie = intent.getStringExtra("EXTRA_NOMBRE") ?: ""

        if (token.isEmpty() || numeroSerie.isEmpty()) {
            Toast.makeText(this, "Información inválida para desvincular", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tokenCompleto = "Bearer $token"
                val request = DesvincularRequestDto(numeroSerie = numeroSerie)

                val response = RetrofitClient.trapLinkService.desvincularDispositivo(tokenCompleto, request)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val mensajeExitosa = response.body()?.status ?: "Dispositivo desvinculado con éxito"
                        Toast.makeText(this@Detalles, mensajeExitosa, Toast.LENGTH_LONG).show()

                        // Regresamos a la pantalla de Inicio para que refresque la lista de nodos
                        val intent = Intent(this@Detalles, Inicio::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        val mensajeError = when (response.code()) {
                            400 -> "La trampa no está vinculada."
                            403 -> "No tienes permiso para desvincular esta trampa."
                            404 -> "El número de serie no existe."
                            else -> "Error al desvincular (${response.code()})"
                        }
                        Toast.makeText(this@Detalles, mensajeError, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@Detalles, "Error de red: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}