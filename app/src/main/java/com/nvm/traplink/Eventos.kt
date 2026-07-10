package com.nvm.traplink

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.nvm.traplink.data.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Eventos : AppCompatActivity() {

    private lateinit var pieChartAnalisis: PieChart
    private lateinit var rvAnalisisTrampas: RecyclerView
    private var isDarkThemeActive: Boolean = false

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

        // Vincular los tres botones de la barra inferior unificada
        val btnNavDispositivos = findViewById<TextView>(R.id.btnNavDispositivos)
        val btnNavEventos = findViewById<TextView>(R.id.btnNavEventos)
        val btnNavVincular = findViewById<TextView>(R.id.btnNavVincular)

        configurarGrafica()
        cargarResumenAnalitico()

        pieChartAnalisis.setOnClickListener {
            cargarResumenAnalitico()
            Toast.makeText(this, "Actualizando telemetría en tiempo real...", Toast.LENGTH_SHORT).show()
        }

        // Navegación fluida entre actividades
        btnNavDispositivos.setOnClickListener {
            startActivity(Intent(this, Inicio::class.java))
            overridePendingTransition(0, 0)
            finish()
        }

        btnNavVincular.setOnClickListener {
            startActivity(Intent(this, VincularActivity::class.java)) // Asegúrate de que coincida con el nombre de tu clase
            overridePendingTransition(0, 0)
            finish()
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
            return
        }

        val tokenCompleto = "Bearer $tokenGuardado"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val responseKpis = RetrofitClient.trapLinkService.getResumenKpis(tokenCompleto)
                val responseDesglose = RetrofitClient.trapLinkService.getFalsosPositivos(tokenCompleto)

                withContext(Dispatchers.Main) {
                    var realesGlobales = 0
                    var pendientesGlobales = 0

                    if (responseKpis.isSuccessful && responseKpis.body() != null) {
                        val kpis = responseKpis.body()!!
                        realesGlobales = kpis.capturasReales
                        pendientesGlobales = kpis.eventosSinRevisar

                        actualizarDatosGrafica(
                            capturas = kpis.capturasReales.toFloat(),
                            falsos = kpis.falsosPositivos.toFloat(),
                            sinRevisar = kpis.eventosSinRevisar.toFloat()
                        )
                    }

                    if (responseDesglose.isSuccessful && responseDesglose.body() != null) {
                        val listaDesglose = responseDesglose.body()!!
                        if (listaDesglose.isNotEmpty()) {
                            // Pasamos los datos globales al adaptador para el desglose temporal
                            rvAnalisisTrampas.adapter = AnalisisTrampasAdapter(listaDesglose, realesGlobales, pendientesGlobales)
                        }
                    } else {
                        // El respaldo ahora coincide con la estructura original de 4 campos del DTO
                        val datosPrueba = listOf(
                            com.nvm.traplink.data.FalsosPositivosResponseDto(
                                dispositivoID = 1,
                                totalEventos = 7,
                                falsosPositivos = 0,
                                pctFalsosPositivos = 0.0
                            )
                        )
                        rvAnalisisTrampas.adapter = AnalisisTrampasAdapter(datosPrueba, realesGlobales, pendientesGlobales)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@Eventos, "Error de red al actualizar telemetría.", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
        pieChartAnalisis.invalidate()
    }
}