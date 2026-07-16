package com.nvm.traplink

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.nvm.traplink.data.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Eventos : AppCompatActivity() {

    private lateinit var pieChartAnalisis: PieChart
    private lateinit var rvAnalisisTrampas: RecyclerView
    private lateinit var swipeRefreshEventos: SwipeRefreshLayout
    private lateinit var skeletonEventos: LinearLayout
    private lateinit var emptyStateEventos: LinearLayout
    private lateinit var tvKpiReales: TextView
    private lateinit var tvKpiFalsos: TextView
    private lateinit var tvKpiPendientes: TextView
    private lateinit var tvUltimaActualizacion: TextView
    private lateinit var cardInsight: LinearLayout
    private lateinit var tvInsightText: TextView

    private var isDarkThemeActive: Boolean = false
    private var primeraCargaCompleta = false

    // Cache de dispositivos reales (numeroSerie/estado) para cruzar por ID al navegar a Detalles
    private var cacheDispositivos: List<com.nvm.traplink.data.MisDispositivosResponseDto> = emptyList()

    private val prefs by lazy { getSharedPreferences("TrapLinkPrefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        isDarkThemeActive = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkThemeActive) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_eventos)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val ivToggleIcon = findViewById<ImageView>(R.id.ivToggleTemaIcon)
        ivToggleIcon.setImageResource(if (isDarkThemeActive) R.drawable.ic_sun else R.drawable.ic_moon)

        findViewById<FrameLayout>(R.id.btnToggleTema).setOnClickListener {
            val nuevoModoOscuro = !prefs.getBoolean("dark_mode", false)
            prefs.edit().putBoolean("dark_mode", nuevoModoOscuro).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (nuevoModoOscuro) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            recreate()
        }

        pieChartAnalisis = findViewById(R.id.pieChartAnalisis)
        rvAnalisisTrampas = findViewById(R.id.rvAnalisisTrampas)
        rvAnalisisTrampas.layoutManager = LinearLayoutManager(this)

        swipeRefreshEventos = findViewById(R.id.swipeRefreshEventos)
        skeletonEventos = findViewById(R.id.skeletonEventos)
        emptyStateEventos = findViewById(R.id.emptyStateEventos)
        tvKpiReales = findViewById(R.id.tvKpiReales)
        tvKpiFalsos = findViewById(R.id.tvKpiFalsos)
        tvKpiPendientes = findViewById(R.id.tvKpiPendientes)
        tvUltimaActualizacion = findViewById(R.id.tvUltimaActualizacion)
        cardInsight = findViewById(R.id.cardInsight)
        tvInsightText = findViewById(R.id.tvInsightText)

        // Vincular los tres botones de la barra inferior unificada
        val btnNavDispositivos = findViewById<TextView>(R.id.btnNavDispositivos)
        val btnNavVincular = findViewById<TextView>(R.id.btnNavVincular)

        // Skeleton mientras llega la primera respuesta del API
        iniciarShimmerSkeleton()

        configurarGrafica()
        cargarResumenAnalitico()

        // Botón de refresh explícito en la tarjeta de la gráfica
        findViewById<FrameLayout>(R.id.btnRefreshChart).setOnClickListener {
            cargarResumenAnalitico()
            Toast.makeText(this, "Actualizando telemetría...", Toast.LENGTH_SHORT).show()
        }

        // Pull-to-refresh de pantalla completa
        swipeRefreshEventos.setOnRefreshListener {
            cargarResumenAnalitico()
        }

        // Navegación fluida entre actividades
        btnNavDispositivos.setOnClickListener {
            startActivity(Intent(this, Inicio::class.java))
            overridePendingTransition(0, 0)
            finish()
        }

        btnNavVincular.setOnClickListener {
            startActivity(Intent(this, VincularActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }

        // Vinculamos el contenedor del botón de usuario
        val btnLogout = findViewById<FrameLayout>(R.id.btnLogout)

        btnLogout.setOnClickListener { view ->
            // Creamos el PopupMenu anclado a la vista del botón
            val popup = androidx.appcompat.widget.PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.menu_usuario, popup.menu)

            // Configuramos las acciones de los clics de cada opción
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_detalles -> {
                        // Ir a la pantalla de Detalles
                        val intent = Intent(this, DetallesCuenta::class.java)
                        startActivity(intent)
                        true
                    }
                    R.id.menu_cerrar_sesion -> {
                        // ¡Llamada directa de una sola línea!
                        CerrarSesion.cerrarSesion(this)
                        true
                    }
                    else -> false
                }
            }
            // Mostramos el menú
            popup.show()
        }
    }

    private fun configurarGrafica() {
        val labelColor = if (isDarkThemeActive) Color.WHITE else Color.parseColor("#333333")

        pieChartAnalisis.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            setEntryLabelColor(labelColor)
            setEntryLabelTextSize(11f)
            legend.isEnabled = false
            animateY(900)
        }
    }

    private fun cargarResumenAnalitico() {
        val tokenGuardado = prefs.getString("AUTH_TOKEN", "") ?: ""
        if (tokenGuardado.isEmpty()) {
            Toast.makeText(this, "Sesión expirada. Por favor, reingresa.", Toast.LENGTH_SHORT).show()
            ocultarSkeletonSiCorresponde()
            return
        }

        val tokenCompleto = "Bearer $tokenGuardado"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Solicitamos de forma paralela los KPIs globales, el desglose del backend y tus dispositivos reales
                val responseKpis = RetrofitClient.trapLinkService.getResumenKpis(tokenCompleto)
                val responseDesglose = RetrofitClient.trapLinkService.getFalsosPositivos(tokenCompleto)
                val responseDispositivos = RetrofitClient.trapLinkService.getMisDispositivos(tokenCompleto)

                withContext(Dispatchers.Main) {
                    swipeRefreshEventos.isRefreshing = false
                    ocultarSkeletonSiCorresponde()

                    // 2. Guardamos la lista de dispositivos reales vinculados que ve el usuario
                    if (responseDispositivos.isSuccessful && responseDispositivos.body() != null) {
                        cacheDispositivos = responseDispositivos.body()!!
                    }

                    var realesGlobales = 0
                    var pendientesGlobales = 0
                    var falsosGlobales = 0

                    // 3. Procesamos los contadores de la gráfica
                    if (responseKpis.isSuccessful && responseKpis.body() != null) {
                        val kpis = responseKpis.body()!!
                        realesGlobales = kpis.capturasReales
                        pendientesGlobales = kpis.eventosSinRevisar
                        falsosGlobales = kpis.falsosPositivos

                        tvKpiReales.text = realesGlobales.toString()
                        tvKpiFalsos.text = falsosGlobales.toString()
                        tvKpiPendientes.text = pendientesGlobales.toString()

                        actualizarDatosGrafica(
                            capturas = kpis.capturasReales.toFloat(),
                            falsos = kpis.falsosPositivos.toFloat(),
                            sinRevisar = kpis.eventosSinRevisar.toFloat()
                        )
                    }

                    tvUltimaActualizacion.text = "Actualizado a las ${horaActual()}"

                    // 4. Cruzamos los datos del desglose con tus dispositivos REALES vinculados
                    val listaDesgloseBackend = if (responseDesglose.isSuccessful && responseDesglose.body() != null) {
                        responseDesglose.body()!!
                    } else {
                        emptyList()
                    }

                    val listaFinalNodos = ArrayList<com.nvm.traplink.data.FalsosPositivosResponseDto>()

                    if (cacheDispositivos.isNotEmpty()) {
                        // Para cada dispositivo que realmente tiene el usuario...
                        for (dispositivo in cacheDispositivos) {
                            // Buscamos si tiene estadísticas registradas en el backend
                            val estadistica = listaDesgloseBackend.find { it.dispositivoID == dispositivo.dispositivoID }

                            if (estadistica != null) {
                                // Si tiene datos, añadimos su DTO correspondiente
                                listaFinalNodos.add(estadistica)
                            } else {
                                // Si es un nodo recién vinculado o sin eventos, le creamos su DTO en ceros para que no desaparezca
                                listaFinalNodos.add(
                                    com.nvm.traplink.data.FalsosPositivosResponseDto(
                                        dispositivoID = dispositivo.dispositivoID,
                                        totalEventos = 0,
                                        falsosPositivos = 0,
                                        pctFalsosPositivos = 0.0,
                                        capturasReales = 0,
                                        eventosSinRevisar = 0
                                    )
                                )
                            }
                        }
                    }

                    // 5. Pintamos el RecyclerView basándonos en los dispositivos reales del usuario
                    val listaVacia = listaFinalNodos.isEmpty()

                    if (!listaVacia) {
                        rvAnalisisTrampas.adapter = AnalisisTrampasAdapter(listaFinalNodos) { nodo ->
                            irADetalles(nodo.dispositivoID)
                        }
                        rvAnalisisTrampas.scheduleLayoutAnimation()

                        cardInsight.visibility = View.VISIBLE
                        tvInsightText.text = generarInsight(listaFinalNodos)
                    } else {
                        cardInsight.visibility = View.GONE
                    }

                    emptyStateEventos.visibility = if (listaVacia) View.VISIBLE else View.GONE
                    rvAnalisisTrampas.visibility = if (listaVacia) View.GONE else View.VISIBLE

                    if (!primeraCargaCompleta) {
                        animarEntradaKpis()
                        primeraCargaCompleta = true
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    swipeRefreshEventos.isRefreshing = false
                    ocultarSkeletonSiCorresponde()
                    Toast.makeText(this@Eventos, "Error de red al actualizar telemetría.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun horaActual(): String {
        val formato = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formato.format(Date())
    }

    /**
     * Genera una frase de análisis automático a partir del desglose por nodo,
     * destacando cuál necesita atención en vez de dejar que el usuario lo deduzca solo.
     */
    private fun generarInsight(lista: List<com.nvm.traplink.data.FalsosPositivosResponseDto>): String {
        if (lista.isEmpty()) return "Aún no hay suficientes datos para generar un análisis."

        val buenos = lista.count { it.pctFalsosPositivos < 15.0 }
        val total = lista.size
        val peor = lista.maxByOrNull { it.pctFalsosPositivos }

        return when {
            buenos == total -> "Todos tus nodos están funcionando bien. Buen trabajo."
            peor != null -> {
                val pctTexto = String.format(Locale.getDefault(), "%.0f", peor.pctFalsosPositivos)
                "$buenos de $total nodos tienen buen desempeño. El Nodo #${peor.dispositivoID} tiene $pctTexto% de falsos positivos, revísalo."
            }
            else -> "Revisa el rendimiento de tus nodos en la lista de abajo."
        }
    }

    /**
     * Navega a la pantalla de detalle del nodo. Cruza el ID con la lista de dispositivos
     * reales (traída en paralelo con el resumen) para pasar el número de serie y estado
     * reales, igual que hace Inicio. Si por alguna razón no se encuentra en cache
     * (ej. dispositivo eliminado recientemente), cae a un nombre genérico como respaldo.
     */
    private fun irADetalles(dispositivoId: Int) {
        val dispositivoReal = cacheDispositivos.find { it.dispositivoID == dispositivoId }

        val intent = Intent(this, Detalles::class.java).apply {
            putExtra("EXTRA_DISPOSITIVO_ID", dispositivoId)
            putExtra("EXTRA_NOMBRE", dispositivoReal?.numeroSerie ?: "Dispositivo #$dispositivoId")
            putExtra("EXTRA_ESTADO", dispositivoReal?.estado ?: "Monitoreando")
        }
        startActivity(intent)
    }

    private fun actualizarDatosGrafica(capturas: Float, falsos: Float, sinRevisar: Float) {
        val entries = ArrayList<PieEntry>()

        if (capturas > 0) entries.add(PieEntry(capturas, "Reales"))
        if (falsos > 0) entries.add(PieEntry(falsos, "Falsos"))
        if (sinRevisar > 0) entries.add(PieEntry(sinRevisar, "Pendientes"))

        if (entries.isEmpty()) entries.add(PieEntry(1f, "Sin eventos"))

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                ContextCompat.getColor(this@Eventos, R.color.status_active),
                ContextCompat.getColor(this@Eventos, R.color.status_capture),
                Color.parseColor("#FFB300"),
                ContextCompat.getColor(this@Eventos, R.color.status_disconnected)
            ).take(entries.size)

            valueTextSize = 13f
            valueTextColor = Color.WHITE
        }

        pieChartAnalisis.data = PieData(dataSet)
        pieChartAnalisis.centerText = (capturas + falsos + sinRevisar).toInt().toString()
        pieChartAnalisis.setCenterTextSize(22f)
        pieChartAnalisis.setCenterTextColor(
            ContextCompat.getColor(this, R.color.text_title_dark)
        )
        pieChartAnalisis.invalidate()
    }

    // ===================== SKELETON LOADER =====================

    private fun iniciarShimmerSkeleton() {
        skeletonEventos.animate()
            .alpha(0.4f)
            .setDuration(700)
            .withEndAction {
                if (skeletonEventos.visibility == View.VISIBLE) {
                    skeletonEventos.animate()
                        .alpha(1f)
                        .setDuration(700)
                        .withEndAction { iniciarShimmerSkeleton() }
                        .start()
                }
            }
            .start()
    }

    private fun ocultarSkeletonSiCorresponde() {
        if (skeletonEventos.visibility == View.VISIBLE) {
            skeletonEventos.animate().cancel()
            skeletonEventos.visibility = View.GONE
            swipeRefreshEventos.visibility = View.VISIBLE
        }
    }

    // ===================== ANIMACIÓN DE ENTRADA (KPIs) =====================

    private fun animarEntradaKpis() {
        val kpiRow = findViewById<LinearLayout>(R.id.kpiRow)
        for (i in 0 until kpiRow.childCount) {
            val card = kpiRow.getChildAt(i)
            card.alpha = 0f
            card.translationY = 24f
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setStartDelay((i * 90).toLong())
                .start()
        }
    }
}