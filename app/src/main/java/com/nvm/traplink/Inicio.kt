package com.nvm.traplink

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class Inicio : AppCompatActivity() {

    private val CHANNEL_ID = "trap_alerts_channel"
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    // IP local de tu computadora para pruebas por Wi-Fi (Cámbiala por la tuya actual)
    private val WS_URL = "ws://192.168.1.65:5202/ws"

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

        createNotificationChannel()
        checkNotificationPermission()

        // 1. Obtener el ID del usuario logueado enviado desde MainActivity (Ponemos 1 por defecto por si acaso)
        val usuarioId = intent.getIntExtra("EXTRA_USUARIO_ID", 1)

        // 2. Iniciar la conexión en tiempo real por WebSocket
        conectarWebSocket(usuarioId)

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
            startActivity(intent)
        }

        btnNavEventos.setOnClickListener {
            val intent = Intent(this, Eventos::class.java)
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

    private fun conectarWebSocket(usuarioId: Int) {
        val request = Request.Builder().url(WS_URL).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("TrapLinkWS", "Conectado al servidor WebSocket")

                // Registro estructurado siguiendo las especificaciones de Giovanni
                val registroJson = JSONObject().apply {
                    put("action", "register")
                    put("id", "client_$usuarioId")
                }

                webSocket.send(registroJson.toString())
                Log.d("TrapLinkWS", "Registro enviado: client_$usuarioId")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("TrapLinkWS", "Mensaje recibido: $text")
                try {
                    val json = JSONObject(text)
                    val action = json.optString("action")
                    val fromDevice = json.optString("from")
                    val message = json.optString("message")

                    // Escuchamos el evento de trampa activa en tiempo real
                    if (action == "trap_event") {
                        runOnUiThread {
                            lanzarNotificacionSistema(
                                this@Inicio,
                                "¡Captura Detectada!",
                                "La trampa $fromDevice reportó: $message"
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TrapLinkWS", "Error parseando JSON: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("TrapLinkWS", "Cerrando conexión: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("TrapLinkWS", "Error en WebSocket: ${t.message}")
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cerramos el socket al salir de la pantalla para liberar memoria
        webSocket?.close(1000, "Activity destruida")
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
            val descriptionText = "Notificaciones del sistema para capturas y batería baja"
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
        val notificationId = System.currentTimeMillis().toInt() // ID dinámico para que no se encimen

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(notificationId, builder.build())
            }
        }
    }
}