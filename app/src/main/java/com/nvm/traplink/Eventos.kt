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
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.nvm.traplink.data.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Eventos : AppCompatActivity() {

    // Declaramos nuestra gráfica
    private lateinit var pieChartAnalisis: PieChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_eventos)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Vincular componentes
        pieChartAnalisis = findViewById(R.id.pieChartAnalisis)
        val btnNavDispositivos = findViewById<TextView>(R.id.btnNavDispositivos)
        val btnNavNotificaciones = findViewById<TextView>(R.id.btnNavNotificaciones)

        // Configuración estética inicial de la gráfica
        configurarGrafica()

        // Llamada inicial a Azure
        cargarResumenAnalitico()

        // Si dan clic a la gráfica, se vuelve a actualizar
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

        btnNavNotificaciones.setOnClickListener {
            val intent = Intent(this, Notificaciones::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }
    }

    private fun configurarGrafica() {
        pieChartAnalisis.apply {
            description.isEnabled = false // Ocultar texto de descripción por defecto
            isDrawHoleEnabled = true      // Hacerla tipo "Dona" para que se vea moderna
            setHoleColor(Color.TRANSPARENT)
            setEntryLabelColor(Color.BLACK) // Color del texto de las etiquetas
            setEntryLabelTextSize(12f)
            animateY(1000) // Animación suave de entrada de 1 segundo
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
                val response = RetrofitClient.trapLinkService.getResumenKpis(tokenCompleto)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val kpis = response.body()!!

                        // 1. Mostrar las métricas en la lista inferior de texto
                        val infoReal = """
                            • Trampas Totales Activas: ${kpis.totalDispositivos}
                            • Eventos Registrados en IoT: ${kpis.totalEventos}
                            • Capturas Reales Confirmadas: ${kpis.capturasReales}
                            • Falsos Positivos Descartados: ${kpis.falsosPositivos}
                            • Alertas Críticas Sin Revisar: ${kpis.eventosSinRevisar}
                        """.trimIndent()

                        val tvInfo = findViewById<TextView>(R.id.tvMetricasDetalle)
                        tvInfo?.text = infoReal

                        // 2. Pintar los datos dinámicos en la Gráfica de Pastel
                        actualizarDatosGrafica(
                            capturas = kpis.capturasReales.toFloat(),
                            falsos = kpis.falsosPositivos.toFloat(),
                            sinRevisar = kpis.eventosSinRevisar.toFloat()
                        )

                    } else {
                        Toast.makeText(this@Eventos, "Azure denegó la consulta de KPIs (401/403)", Toast.LENGTH_SHORT).show()
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

        // Solo añadimos secciones al pastel si el valor es mayor a 0, para evitar amontonamientos
        if (capturas > 0) entries.add(PieEntry(capturas, "Reales"))
        if (falsos > 0) entries.add(PieEntry(falsos, "Falsos"))
        if (sinRevisar > 0) entries.add(PieEntry(sinRevisar, "Pendientes"))

        // Si la base de datos está completamente en ceros (por ser pruebas iniciales), agregamos un estado base vació
        if (entries.isEmpty()) {
            entries.add(PieEntry(1f, "Sin eventos registrados"))
        }

        // Crear set de datos y meterle una paleta de colores alegre/limpia
        val dataSet = PieDataSet(entries, "Historial IoT")
        dataSet.colors = ColorTemplate.COLORFUL_COLORS.toList()
        dataSet.valueTextSize = 14f
        dataSet.valueTextColor = Color.BLACK

        val data = PieData(dataSet)
        pieChartAnalisis.data = data

        // Indicarle a la librería que refresque y redibuje el componente
        pieChartAnalisis.invalidate()
    }
}