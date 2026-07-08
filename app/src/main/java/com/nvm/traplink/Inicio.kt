package com.nvm.traplink

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.nvm.traplink.data.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Inicio : AppCompatActivity() {

    private val CHANNEL_ID = "trap_alerts_channel"
    private lateinit var rvDispositivos: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout // Declaramos el componente

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted){
            Toast.makeText(this, "Permiso de alertas concedido", Toast.LENGTH_SHORT).show()
        }else{
            Toast.makeText(this, "Las alertas del sistema están desactivadas", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_inicio)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        createNotificationChannel()
        checkNotificationPermission()

        rvDispositivos = findViewById(R.id.rvDispositivos)
        rvDispositivos.layoutManager = LinearLayoutManager(this)

        // Inicializamos el SwipeRefreshLayout
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        val btnNavEventos = findViewById<TextView>(R.id.btnNavEventos)
        val btnNavVincular = findViewById<TextView>(R.id.btnNavVincular)

        cargarDispositivosDesdeAzure()

        // Configuramos la acción al deslizar hacia abajo
        swipeRefreshLayout.setOnRefreshListener {
            cargarDispositivosDesdeAzure()
        }

        btnNavEventos.setOnClickListener {
            val intent = Intent(this, Eventos::class.java)
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

    private fun cargarDispositivosDesdeAzure() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sharedPreferences = getSharedPreferences("TrapLinkPrefs", MODE_PRIVATE)
                val tokenGuardado = sharedPreferences.getString("AUTH_TOKEN", "") ?: ""

                if (tokenGuardado.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        swipeRefreshLayout.isRefreshing = false // Apagar animación si falla
                        Toast.makeText(this@Inicio, "Error: Sesión inválida. Vuelve a iniciar sesión.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val tokenCompleto = "Bearer $tokenGuardado"
                val response = RetrofitClient.trapLinkService.getMisDispositivos(tokenCompleto)

                withContext(Dispatchers.Main) {
                    swipeRefreshLayout.isRefreshing = false // Apagar animación al recibir respuesta

                    if (response.isSuccessful && response.body() != null) {
                        val listaTrampas = response.body()!!

                        listaTrampas.forEach { trampa ->
                            if (trampa.estado.contains("Captura", ignoreCase = true)) {
                                lanzarNotificacionSistema(this@Inicio, "🚨 Captura Detectada", "El dispositivo ${trampa.numeroSerie} registró actividad.")
                            }
                        }

                        rvDispositivos.adapter = TrampasAdapter(listaTrampas) { trampaSeleccionada ->
                            val intent = Intent(this@Inicio, Detalles::class.java).apply {
                                putExtra("EXTRA_DISPOSITIVO_ID", trampaSeleccionada.dispositivoID)
                                putExtra("EXTRA_NOMBRE", trampaSeleccionada.numeroSerie)
                                putExtra("EXTRA_ESTADO", trampaSeleccionada.estado)
                            }
                            startActivity(intent)
                        }

                    } else {
                        val codigoError = response.code()
                        val mensajeError = response.errorBody()?.string() ?: "Sin mensaje"
                        Toast.makeText(this@Inicio, "Error $codigoError: $mensajeError", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    swipeRefreshLayout.isRefreshing = false // Apagar animación si hay excepción de red
                    Toast.makeText(this@Inicio, "Error de conexión en Nodos: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkNotificationPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED){
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun createNotificationChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val name = "Alertas de Trampas"
            val descriptionText = "VincularActivity del sistema para capturas y batería baja"
            val importance = NotificationManager.IMPORTANCE_HIGH

            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            // Dejar únicamente la llamada correcta al servicio de notificaciones
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun lanzarNotificacionSistema(context: Context, titulo: String, mensaje: String) {
        val notificationId = 101

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(notificationId, builder.build())
            }
        }
    }
}