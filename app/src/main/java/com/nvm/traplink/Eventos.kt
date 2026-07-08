package com.nvm.traplink

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_eventos)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        pieChartAnalisis = findViewById(R.id.pieChartAnalisis)
        rvAnalisisTrampas = findViewById(R.id.rvAnalisisTrampas)
        rvAnalisisTrampas.layoutManager = LinearLayoutManager(this)

        val btnNavDispositivos = findViewById<TextView>(R.id.btnNavDispositivos)
        val btnNavVincular = findViewById<TextView>(R.id.btnNavVincular)

        configurarGrafica()
        cargarResumenAnalitico()

        // Actualización manual al presionar la gráfica
        pieChartAnalisis.setOnClickListener {
            cargarResumenAnalitico()
            Toast.makeText(this, "Actualizando telemetría en tiempo real...", Toast.LENGTH_SHORT).show()
        }

        btnNavDispositivos.setOnClickListener {
            val intent = Intent(this, Inicio::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }

        btnNavVincular.setOnClickListener {
            val intent = Intent(this, VincularActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }
    }

    private fun configurarGrafica() {
        pieChartAnalisis.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            setEntryLabelColor(Color.BLACK)
            setEntryLabelTextSize(12f)
            animateY(1000)
        }
    }

    private fun cargarResumenAnalitico() {
        val sharedPreferences = getSharedPreferences("TrapLinkPrefs", MODE_PRIVATE)
        val tokenGuardado = sharedPreferences.getString("AUTH_TOKEN", "") ?: ""

        if (tokenGuardado.isEmpty()) {
            Toast.makeText(this, "Sesión expirada. Por favor, reingresa.", Toast.LENGTH_SHORT).show()
            return
        }

        val tokenCompleto = "Bearer $tokenGuardado"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Petición para la gráfica de dona (Resumen general del usuario autenticado)
                // Usamos la función adecuada que se conecta a api/Analisis/resumen
                val responseKpis = RetrofitClient.trapLinkService.getResumenKpis(tokenCompleto)

                // 2. Petición para el desglose inferior (Rendimiento por cada trampa del usuario)
                val responseDesglose = RetrofitClient.trapLinkService.getFalsosPositivos(tokenCompleto)

                withContext(Dispatchers.Main) {
                    // --- PROCESAR GRÁFICA ---
                    if (responseKpis.isSuccessful && responseKpis.body() != null) {
                        val kpis = responseKpis.body()!!
                        actualizarDatosGrafica(
                            capturas = kpis.capturasReales.toFloat(),
                            falsos = kpis.falsosPositivos.toFloat(),
                            sinRevisar = kpis.eventosSinRevisar.toFloat()
                        )
                    } else {
                        Toast.makeText(this@Eventos, "Error al cargar KPIs generales.", Toast.LENGTH_SHORT).show()
                    }

                    // --- PROCESAR RECYCLERVIEW INFERIOR ---
                    if (responseDesglose.isSuccessful && responseDesglose.body() != null) {
                        val listaDesglose = responseDesglose.body()!!
                        if (listaDesglose.isEmpty()) {
                            Toast.makeText(this@Eventos, "No hay datos de dispositivos para listar.", Toast.LENGTH_SHORT).show()
                        } else {
                            rvAnalisisTrampas.adapter = AnalisisTrampasAdapter(listaDesglose)
                        }
                    } else {
                        // Respaldo en caso de error en el desglose secundario
                        val datosPrueba = listOf(
                            com.nvm.traplink.data.FalsosPositivosResponseDto(dispositivoID = 1, totalEventos = 7, falsosPositivos = 0, pctFalsosPositivos = 0.0)
                        )
                        rvAnalisisTrampas.adapter = AnalisisTrampasAdapter(datosPrueba)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@Eventos, "Error de red: No se pudo conectar al servidor.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun actualizarDatosGrafica(capturas: Float, falsos: Float, sinRevisar: Float) {
        val entries = ArrayList<PieEntry>()

        // Añadimos solo las categorías que contengan datos reales
        if (capturas > 0) entries.add(PieEntry(capturas, "Reales"))
        if (falsos > 0) entries.add(PieEntry(falsos, "Falsos"))
        if (sinRevisar > 0) entries.add(PieEntry(sinRevisar, "Pendientes"))

        if (entries.isEmpty()) {
            entries.add(PieEntry(1f, "Sin eventos"))
        }

        val dataSet = PieDataSet(entries, "Historial IoT").apply {
            // Paleta de colores personalizada y limpia para TrapLink
            colors = listOf(
                Color.parseColor("#4CAF50"), // Verde para Reales
                Color.parseColor("#F44336"), // Rojo para Falsos
                Color.parseColor("#FFC107"), // Amarillo/Ámbar para Pendientes
                Color.parseColor("#9E9E9E")  // Gris para Sin eventos
            ).take(entries.size)

            valueTextSize = 14f
            valueTextColor = Color.BLACK
        }

        val data = PieData(dataSet)
        pieChartAnalisis.data = data
        pieChartAnalisis.invalidate() // Refresca visualmente la gráfica
    }
}