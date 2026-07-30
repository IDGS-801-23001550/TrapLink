package com.nvm.traplink

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.nvm.traplink.data.RetrofitClient
import com.nvm.traplink.CerrarSesion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Inicio : AppCompatActivity() {

    private val CHANNEL_ID = "trap_alerts_channel"
    private lateinit var rvDispositivos: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private lateinit var tvStatTotal: TextView
    private lateinit var tvStatActivos: TextView
    private lateinit var tvStatAlertas: TextView
    private lateinit var emptyState: NestedScrollView
    //private lateinit var fabVincular: FrameLayout

    // Vistas del video preview (autoplay, silenciado, en loop)
    private lateinit var cardVideoPreview: CardView
    private lateinit var videoPreview: VideoView
    private lateinit var btnSonidoPreview: FrameLayout
    private lateinit var ivIconoSonido: ImageView
    private var mediaPlayerPreview: android.media.MediaPlayer? = null
    private var sonidoActivado = false
    private var yaSeAnimaronStats = false
    private var primeraCargaCompleta = false
    private lateinit var skeletonContainer: LinearLayout

    // SharedPreferences para guardar la preferencia del tema
    private val prefs by lazy { getSharedPreferences("TrapLinkPrefs", MODE_PRIVATE) }

    private lateinit var tvDeviceCountBadge: TextView

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
        // 1. Aplicar el tema guardado ANTES de inflar vistas o habilitar EdgeToEdge
        val isDark = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_inicio)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Solo el bottom recibe padding — el top se queda en 0
            // para que el header siga extendiéndose bajo la barra de estado
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        createNotificationChannel()
        checkNotificationPermission()

        // 2. Inicializar los componentes del Toggle de Tema
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

        // Resto de tus inicializaciones normales
        rvDispositivos = findViewById(R.id.rvDispositivos)
        rvDispositivos.layoutManager = LinearLayoutManager(this)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setColorSchemeResources(R.color.accent_neon)

        val btnNavEventos = findViewById<TextView>(R.id.btnNavEventos)
        val btnNavVincular = findViewById<TextView>(R.id.btnNavVincular)

        // Inicialización de las vistas del header/estadísticas/estado vacío
        tvStatTotal = findViewById(R.id.tvStatTotal)
        //tvDeviceCountBadge = findViewById(R.id.tvDeviceCountBadge)
        tvStatActivos = findViewById(R.id.tvStatActivos)
        tvStatAlertas = findViewById(R.id.tvStatAlertas)
        emptyState = findViewById(R.id.emptyState)
        //fabVincular = findViewById(R.id.fabVincular)

        // Skeleton loader: se muestra mientras llega la primera respuesta del API
        skeletonContainer = findViewById(R.id.skeletonContainer)
        iniciarShimmer()

        // ===== Video preview autoplay dentro del estado vacío =====
        cardVideoPreview = findViewById(R.id.cardVideoPreview)
        videoPreview = findViewById(R.id.videoPreview)
        btnSonidoPreview = findViewById(R.id.btnSonidoPreview)
        ivIconoSonido = findViewById(R.id.ivIconoSonido)

        configurarVideoPreview()

        // Tocar el video (o la tarjeta completa) abre la versión completa con sonido y controles
        cardVideoPreview.setOnClickListener {
            startActivity(Intent(this, TutorialActivity::class.java))
        }

        // Botón de bocina: activa/desactiva el sonido del preview sin salir de la pantalla
        btnSonidoPreview.setOnClickListener {
            sonidoActivado = !sonidoActivado
            val volumen = if (sonidoActivado) 1f else 0f
            mediaPlayerPreview?.setVolume(volumen, volumen)
            ivIconoSonido.setImageResource(
                if (sonidoActivado) R.drawable.ic_volume_up else R.drawable.ic_volume_off
            )
        }

        /*fabVincular.setOnClickListener {
            val intent = Intent(this, VincularActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }*/

        cargarDispositivosDesdeAzure()

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

    private fun cargarDispositivosDesdeAzure() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tokenGuardado = prefs.getString("AUTH_TOKEN", "") ?: ""

                if (tokenGuardado.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        swipeRefreshLayout.isRefreshing = false
                        Toast.makeText(this@Inicio, "Error: Sesión inválida. Vuelve a iniciar sesión.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val tokenCompleto = "Bearer $tokenGuardado"
                val response = RetrofitClient.trapLinkService.getMisDispositivos(tokenCompleto)

                withContext(Dispatchers.Main) {
                    swipeRefreshLayout.isRefreshing = false

                    // Ocultar el skeleton loader apenas llega la primera respuesta (éxito o error)
                    if (!primeraCargaCompleta) {
                        detenerShimmer()
                        skeletonContainer.visibility = View.GONE
                        swipeRefreshLayout.visibility = View.VISIBLE
                        primeraCargaCompleta = true
                    }

                    if (response.isSuccessful && response.body() != null) {
                        val listaTrampas = response.body()!!

                        // Actualizar estadísticas
                        tvStatTotal.text = listaTrampas.size.toString()
                        //tvDeviceCountBadge.text = if (listaTrampas.size == 1) "1 nodo" else "${listaTrampas.size} nodos"
                        tvStatActivos.text = listaTrampas.count { it.estado.contains("Activ", ignoreCase = true) }.toString()
                        tvStatAlertas.text = listaTrampas.count { it.estado.contains("Captura", ignoreCase = true) }.toString()

                        if (!yaSeAnimaronStats) {
                            animarEntradaStats()
                            yaSeAnimaronStats = true
                        }

                        // Mostrar estado vacío (con video preview autoplay) si no hay dispositivos
                        if (listaTrampas.isEmpty()) {
                            emptyState.visibility = View.VISIBLE
                            rvDispositivos.visibility = View.GONE
                            reanudarVideoPreview()

                            // Asegura que al mostrarse el estado vacío empiece desde arriba
                            emptyState.post {
                                emptyState.scrollTo(0, 0)
                            }
                        } else {
                            emptyState.visibility = View.GONE
                            rvDispositivos.visibility = View.VISIBLE
                            pausarVideoPreview()
                        }

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

                        // Anima la entrada de cada tarjeta de dispositivo (fade + slide escalonado)
                        rvDispositivos.scheduleLayoutAnimation()
                    } else {
                        val codigoError = response.code()
                        val mensajeError = response.errorBody()?.string() ?: "Sin mensaje"
                        Toast.makeText(this@Inicio, "Error $codigoError: $mensajeError", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    swipeRefreshLayout.isRefreshing = false
                    if (!primeraCargaCompleta) {
                        detenerShimmer()
                        skeletonContainer.visibility = View.GONE
                        swipeRefreshLayout.visibility = View.VISIBLE
                        primeraCargaCompleta = true
                    }
                    Toast.makeText(this@Inicio, "Error de conexión en Nodos: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ===================== SKELETON LOADER (primera carga) =====================

    /**
     * Aplica un efecto shimmer (pulso de opacidad en loop) al contenedor
     * skeleton mientras se espera la primera respuesta del API.
     */
    private fun iniciarShimmer() {
        skeletonContainer.animate()
            .alpha(0.4f)
            .setDuration(700)
            .withEndAction {
                if (skeletonContainer.visibility == View.VISIBLE) {
                    skeletonContainer.animate()
                        .alpha(1f)
                        .setDuration(700)
                        .withEndAction { iniciarShimmer() }
                        .start()
                }
            }
            .start()
    }

    private fun detenerShimmer() {
        skeletonContainer.animate().cancel()
        skeletonContainer.alpha = 1f
    }

    // ===================== ANIMACIÓN DE ENTRADA (tarjetas de estadísticas) =====================

    /**
     * Anima las 3 tarjetas de estadísticas (Total/Activos/Alertas) con un
     * fade + slide sutil y escalonado, para que se sientan "vivas" al cargar
     * en vez de aparecer de golpe.
     */
    private fun animarEntradaStats() {
        val statsRow = findViewById<android.widget.LinearLayout>(R.id.statsRow)
        for (i in 0 until statsRow.childCount) {
            val card = statsRow.getChildAt(i)
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

    // ===================== VIDEO PREVIEW AUTOPLAY (estado vacío) =====================

    /**
     * Configura el VideoView del estado vacío para que arranque solo, en loop y
     * silenciado apenas el video termina de prepararse (sin esperar ningún tap).
     * Requiere el archivo res/raw/tutorial_vincular.mp4
     */
    private fun configurarVideoPreview() {
        try {
            val uri = Uri.parse("android.resource://$packageName/${R.raw.tutorial_vincular}")
            videoPreview.setVideoURI(uri)

            // Evitamos que el video solicite o tome el foco automáticamente
            videoPreview.isFocusable = false
            videoPreview.isFocusableInTouchMode = false

            videoPreview.setOnPreparedListener { mp ->
                mediaPlayerPreview = mp
                mp.isLooping = true
                mp.setVolume(0f, 0f) // arranca silenciado
                videoPreview.start()

                // Forzamos al NestedScrollView a regresar arriba de inmediato
                // por si la reproducción intentó mover la pantalla
                emptyState.post {
                    emptyState.scrollTo(0, 0)
                }
            }

            videoPreview.setOnErrorListener { _, _, _ -> true }

        } catch (e: Exception) {
            // Silenciosamente ignoramos el error
        }
    }

    private fun reanudarVideoPreview() {
        if (::videoPreview.isInitialized && !videoPreview.isPlaying) {
            videoPreview.start()
        }
    }

    private fun pausarVideoPreview() {
        if (::videoPreview.isInitialized && videoPreview.isPlaying) {
            videoPreview.pause()
        }
    }

    override fun onPause() {
        super.onPause()
        pausarVideoPreview()
    }

    override fun onResume() {
        super.onResume()
        if (::emptyState.isInitialized && emptyState.visibility == View.VISIBLE) {
            reanudarVideoPreview()
        }
    }

    // ===================== NOTIFICACIONES =====================

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