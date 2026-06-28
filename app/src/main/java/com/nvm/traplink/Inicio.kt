package com.nvm.traplink

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat


class Inicio : AppCompatActivity() {

    private val CHANNEL_ID = "trap_alerts_channel"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted){
            Toast.makeText(this, "Permiso de alertas concedido", Toast.LENGTH_SHORT).show()
        }else{
            Toast.makeText(this, "Las alertas del sistema esta desactivadas", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_inicio)

        createNotificationChannel()

        checkNotificationPermission()

        val cardTrampa1 = findViewById<LinearLayout>(R.id.cardTrampa1)
        val cardTrampa2 = findViewById<LinearLayout>(R.id.cardTrampa2)

        val btnNavEventos = findViewById<TextView>(R.id.btnNavEventos)
        val btnNavNotificaciones = findViewById<TextView>(R.id.btnNavNotificaciones)

        cardTrampa1.setOnClickListener {
            val intent = Intent(this, Detalles::class.java).apply {
                putExtra("EXTRA_NOMBRE", "Nodo Pasillo Norte")
                putExtra("EXTRA_ESTADO", "Activa/OK")
                putExtra("EXTRA_BATERIA", "95%")
                putExtra("EXTRA_PREDICCION", "240 días estimados")
            }
            startActivity(intent)
        }

        cardTrampa2.setOnClickListener {
            val intent = Intent(this, Detalles::class.java).apply {
                putExtra("EXTRA_NOMBRE", "Nodo Almacén Central")
                putExtra("EXTRA_ESTADO", "Captura Detectada")
                putExtra("EXTRA_BATERIA", "40%")
                putExtra("EXTRA_PREDICCION", "45 días estimados")
            }
            lanzarNotificacionSistema(this, "Captura Detectada", "Nodo Almacén Central registró actividad.")
            startActivity(intent)
        }

        btnNavEventos.setOnClickListener {
            val intent = Intent(this, Eventos::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0) // Quita la animación por defecto para simular pestañas nativas
            finish()
        }

        btnNavNotificaciones.setOnClickListener {
            val intent = Intent(this, Notificaciones::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }
    }

    private fun checkNotificationPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED){
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

    }

    private  fun createNotificationChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val name = "Alertas de Trampas"
            val descriptionText = "Notificaciones del sitema para capturas y batería baja"
            val importance = NotificationManager.IMPORTANCE_HIGH

            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun lanzarNotificacionSistema(context: Context, titulo: String, mensaje: String) {
        val notificationId = 101 // ID único para controlar o actualizar esta notificación específica

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat) // icono para la notificación
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Prioridad alta para que interrumpa visualmente
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Activa sonido y vibración
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setAutoCancel(true) // Se quita cuando la presionan

        with(NotificationManagerCompat.from(context)) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(notificationId, builder.build())
            }
        }
    }

}