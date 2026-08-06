package com.nvm.traplink

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.nvm.traplink.data.RetrofitClient
import com.nvm.traplink.data.VincularRequestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

class VincularActivity : AppCompatActivity() {

    private lateinit var etNumeroSerie: EditText
    private lateinit var btnVincularManual: LinearLayout
    private lateinit var btnEscanearQR: LinearLayout
    private lateinit var loadingRow: LinearLayout
    private lateinit var successOverlay: FrameLayout
    private lateinit var successCheckCircle: FrameLayout
    private lateinit var confettiContainer: FrameLayout

    private lateinit var tabManual: TextView
    private lateinit var tabQR: TextView
    private lateinit var seccionManual: LinearLayout
    private lateinit var seccionQR: LinearLayout
    private lateinit var scanLine: View

    private lateinit var cardUltimoVinculado: LinearLayout
    private lateinit var tvUltimoVinculadoSerie: TextView
    private lateinit var tvUltimoVinculadoTiempo: TextView

    // ===== Cámara en vivo (CameraX) para el escaneo de QR dentro del recuadro =====
    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var escaneando = false // evita procesar el mismo código varias veces mientras se vincula

    private var isDarkThemeActive: Boolean = false
    private var cargando: Boolean = false
    private var scanLineAnimator: ObjectAnimator? = null

    private val prefs by lazy { getSharedPreferences("TrapLinkPrefs", MODE_PRIVATE) }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            iniciarCamaraEnVivo()
        } else {
            Toast.makeText(this, "Se requiere el permiso de cámara para escanear códigos QR", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isDarkThemeActive = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkThemeActive) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_vincular)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Solo el bottom recibe padding — el top se queda en 0
            // para que el header siga extendiéndose bajo la barra de estado
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
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

        etNumeroSerie = findViewById(R.id.etNumeroSerie)
        btnVincularManual = findViewById(R.id.btnVincularManual)
        btnEscanearQR = findViewById(R.id.btnEscanearQR)
        loadingRow = findViewById(R.id.loadingRow)
        successOverlay = findViewById(R.id.successOverlay)
        successCheckCircle = findViewById(R.id.successCheckCircle)

        tabManual = findViewById(R.id.tabManual)
        tabQR = findViewById(R.id.tabQR)
        seccionManual = findViewById(R.id.seccionManual)
        seccionQR = findViewById(R.id.seccionQR)
        scanLine = findViewById(R.id.scanLine)
        previewView = findViewById(R.id.previewView)

        cameraExecutor = Executors.newSingleThreadExecutor()

        cardUltimoVinculado = findViewById(R.id.cardUltimoVinculado)
        tvUltimoVinculadoSerie = findViewById(R.id.tvUltimoVinculadoSerie)
        tvUltimoVinculadoTiempo = findViewById(R.id.tvUltimoVinculadoTiempo)

        // Contenedor para el confeti de celebración (se agrega en tiempo de ejecución sobre el overlay)
        confettiContainer = FrameLayout(this)
        (successOverlay.parent as? androidx.constraintlayout.widget.ConstraintLayout)?.let {
            // Insertamos el contenedor de confeti como hermano del successOverlay, mismo tamaño y posición
            val params = successOverlay.layoutParams
            it.addView(confettiContainer, params)
            confettiContainer.id = View.generateViewId()
        }

        mostrarUltimoVinculadoSiExiste()

        tabManual.setOnClickListener { seleccionarTab(esManual = true) }
        tabQR.setOnClickListener { seleccionarTab(esManual = false) }

        val btnNavDispositivos = findViewById<TextView>(R.id.btnNavDispositivos)
        val btnNavEventos = findViewById<TextView>(R.id.btnNavEventos)

        btnVincularManual.setOnClickListener {
            if (cargando) return@setOnClickListener
            val serieText = etNumeroSerie.text.toString().trim()
            if (serieText.isEmpty()) {
                etNumeroSerie.error = "Ingresa un número de serie válido"
                return@setOnClickListener
            }
            ejecutarVinculacionEnAzure(serieText)
        }

        btnEscanearQR.setOnClickListener {
            if (cargando) return@setOnClickListener
            verificarPermisosYCamara()
        }

        btnNavDispositivos.setOnClickListener {
            startActivity(Intent(this, Inicio::class.java))
            overridePendingTransition(0, 0)
            finish()
        }

        btnNavEventos.setOnClickListener {
            startActivity(Intent(this, Eventos::class.java))
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

    // ===================== TABS Manual / Escanear =====================

    private fun seleccionarTab(esManual: Boolean) {
        if (esManual) {
            tabManual.setBackgroundResource(R.drawable.bg_toggle_active)
            tabManual.setTextColor(ContextCompat.getColor(this, R.color.text_title_dark))
            tabQR.background = null
            tabQR.setTextColor(ContextCompat.getColor(this, R.color.text_body_grey))

            seccionManual.visibility = View.VISIBLE
            seccionQR.visibility = View.GONE
            detenerLineaLaser()
            detenerCamara()
        } else {
            tabQR.setBackgroundResource(R.drawable.bg_toggle_active)
            tabQR.setTextColor(ContextCompat.getColor(this, R.color.text_title_dark))
            tabManual.background = null
            tabManual.setTextColor(ContextCompat.getColor(this, R.color.text_body_grey))

            seccionQR.visibility = View.VISIBLE
            seccionManual.visibility = View.GONE
            iniciarLineaLaser()
            verificarPermisosYCamara()
        }
    }

    // ===================== LÍNEA LÁSER (solo mientras el tab QR está activo) =====================

    private fun iniciarLineaLaser() {
        detenerLineaLaser()
        val frameHeight = 180 // dp, coincide con la altura del FrameLayout del visor
        val distanciaPx = frameHeight * resources.displayMetrics.density

        scanLineAnimator = ObjectAnimator.ofFloat(scanLine, "translationY", 0f, distanciaPx - 20f).apply {
            duration = 1600
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun detenerLineaLaser() {
        scanLineAnimator?.cancel()
        scanLine.translationY = 0f
    }

    override fun onDestroy() {
        super.onDestroy()
        detenerLineaLaser()
        detenerCamara()
        cameraExecutor.shutdown()
    }

    // ===================== ÚLTIMO VINCULADO =====================

    private fun mostrarUltimoVinculadoSiExiste() {
        val ultimaSerie = prefs.getString("ULTIMO_VINCULADO_SERIE", null)
        if (!ultimaSerie.isNullOrEmpty()) {
            tvUltimoVinculadoSerie.text = ultimaSerie
            tvUltimoVinculadoTiempo.text = "hace un momento"
            cardUltimoVinculado.visibility = View.VISIBLE
        }
    }

    private fun guardarUltimoVinculado(numeroSerie: String) {
        prefs.edit().putString("ULTIMO_VINCULADO_SERIE", numeroSerie).apply()
    }

    // ===================== CÁMARA EN VIVO + ESCANEO QR (CameraX + ML Kit on-device) =====================

    private fun verificarPermisosYCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            iniciarCamaraEnVivo()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * Abre la cámara trasera directamente dentro de [previewView] (el recuadro del visor)
     * y arranca el análisis de frames en vivo para detectar el QR sin salir de la pantalla.
     */
    private fun iniciarCamaraEnVivo() {
        escaneando = true
        btnEscanearQR.visibility = View.GONE // ya no hace falta: la cámara queda activa dentro del recuadro

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val opcionesEscaner = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            val scanner = BarcodeScanning.getClient(opcionesEscaner)

            val analisis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        procesarFrameDeCamara(imageProxy, scanner)
                    }
                }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analisis
                )
            } catch (e: Exception) {
                Toast.makeText(this, "No se pudo iniciar la cámara: ${e.message}", Toast.LENGTH_SHORT).show()
                btnEscanearQR.visibility = View.VISIBLE
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun procesarFrameDeCamara(imageProxy: ImageProxy, scanner: com.google.mlkit.vision.barcode.BarcodeScanner) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || !escaneando) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(inputImage)
            .addOnSuccessListener { codigosDetectados ->
                val valorDetectado = codigosDetectados.firstOrNull()?.rawValue
                if (!valorDetectado.isNullOrEmpty() && escaneando) {
                    // Evita que sigamos procesando frames y disparando la vinculación varias veces
                    escaneando = false
                    runOnUiThread {
                        etNumeroSerie.setText(valorDetectado)
                        Toast.makeText(this, "Código detectado con éxito", Toast.LENGTH_SHORT).show()
                        ejecutarVinculacionEnAzure(valorDetectado)
                    }
                }
            }
            .addOnFailureListener {
                // Un frame individual puede fallar sin problema; se sigue intentando con el siguiente
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    /** Libera la cámara. Se llama al salir del tab QR, y en onDestroy. */
    private fun detenerCamara() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        escaneando = false
        btnEscanearQR.visibility = View.VISIBLE
    }

    // ===================== VINCULACIÓN =====================

    private fun ejecutarVinculacionEnAzure(numeroSerie: String) {
        val tokenGuardado = prefs.getString("AUTH_TOKEN", "") ?: ""
        val usuarioIdGuardado = prefs.getInt("USUARIO_ID", -1)

        // Validamos tanto el usuarioId como la existencia del token
        if (usuarioIdGuardado == -1 || tokenGuardado.isEmpty()) {
            Toast.makeText(this, "Información de sesión inválida. Inicia sesión de nuevo.", Toast.LENGTH_LONG).show()
            return
        }

        mostrarEstadoCarga(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Se le agrega el prefijo Bearer igual que en desvinculación
                val tokenCompleto = "Bearer $tokenGuardado"

                val requestDto = VincularRequestDto(
                    numeroSerie = numeroSerie,
                    usuarioId = usuarioIdGuardado
                )

                // Pasamos tokenCompleto como primer parámetro
                val response = RetrofitClient.trapLinkService.vincularDispositivo(tokenCompleto, requestDto)

                withContext(Dispatchers.Main) {
                    mostrarEstadoCarga(false)

                    if (response.isSuccessful) {
                        etNumeroSerie.text.clear()
                        guardarUltimoVinculado(numeroSerie)
                        mostrarAnimacionExito()
                    } else {
                        val codigoError = response.code()
                        val mensajeError = response.errorBody()?.string() ?: "Error de servidor"
                        Toast.makeText(this@VincularActivity, "Error $codigoError: $mensajeError", Toast.LENGTH_LONG).show()
                        // Si veníamos del QR, reactivamos el escaneo para que pueda intentar de nuevo
                        escaneando = true
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mostrarEstadoCarga(false)
                    Toast.makeText(this@VincularActivity, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
                    escaneando = true
                }
            }
        }
    }

    private fun mostrarEstadoCarga(cargandoAhora: Boolean) {
        cargando = cargandoAhora
        loadingRow.visibility = if (cargandoAhora) View.VISIBLE else View.GONE
        btnVincularManual.isEnabled = !cargandoAhora
        btnEscanearQR.isEnabled = !cargandoAhora
        etNumeroSerie.isEnabled = !cargandoAhora
        btnVincularManual.alpha = if (cargandoAhora) 0.6f else 1f
        btnEscanearQR.alpha = if (cargandoAhora) 0.6f else 1f
    }

    // ===================== ANIMACIÓN DE ÉXITO + CONFETI =====================

    private fun mostrarAnimacionExito() {
        successOverlay.visibility = View.VISIBLE
        successOverlay.alpha = 0f
        successCheckCircle.scaleX = 0.3f
        successCheckCircle.scaleY = 0.3f

        successOverlay.animate()
            .alpha(1f)
            .setDuration(200)
            .start()

        successCheckCircle.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(300)
            .withEndAction {
                successCheckCircle.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .start()
                lanzarConfeti()
            }
            .start()

        successOverlay.postDelayed({
            startActivity(Intent(this, Inicio::class.java))
            finish()
        }, 1700)
    }

    /**
     * Lanza pequeños puntos de color desde el centro del check en distintas direcciones,
     * como celebración de una sola vez (no un loop) para el momento de éxito.
     */
    private fun lanzarConfeti() {
        val colores = listOf(
            R.color.accent_neon,
            R.color.status_active,
            R.color.secondary,
            R.color.status_battery_low
        )

        // successCheckCircle.x/.y son relativos a SU padre (el LinearLayout que lo centra
        // dentro de successOverlay), no a confettiContainer. Usamos getLocationOnScreen()
        // para convertir ambas vistas al mismo sistema de coordenadas y calcular el centro real.
        val circleLoc = IntArray(2)
        successCheckCircle.getLocationOnScreen(circleLoc)
        val containerLoc = IntArray(2)
        confettiContainer.getLocationOnScreen(containerLoc)

        val centerX = (circleLoc[0] - containerLoc[0] + successCheckCircle.width / 2f)
        val centerY = (circleLoc[1] - containerLoc[1] + successCheckCircle.height / 2f)

        repeat(14) { i ->
            val dot = View(this)
            val size = (10 + Random.nextInt(8)) // puntos más grandes: 10-18dp
            val sizePx = (size * resources.displayMetrics.density).toInt()

            dot.layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            dot.background = ContextCompat.getDrawable(this, R.drawable.dot_status_active)
            dot.backgroundTintList = ContextCompat.getColorStateList(this, colores[i % colores.size])
            dot.x = centerX
            dot.y = centerY

            confettiContainer.addView(dot)

            val angulo = (i * (360 / 14)) + Random.nextInt(20)
            val radianes = Math.toRadians(angulo.toDouble())
            val distancia = (180f + Random.nextInt(120)) * resources.displayMetrics.density
            // IMPORTANTE: se suma al centro (centerX/centerY), no se reemplaza,
            // porque .animate().translationX() fija el valor absoluto de la propiedad,
            // y dot.x = centerX ya dejó ese valor como base en translationX.
            val destinoX = centerX + (Math.cos(radianes) * distancia).toFloat()
            val destinoY = centerY + (Math.sin(radianes) * distancia).toFloat()

            dot.animate()
                .translationX(destinoX)
                .translationY(destinoY)
                .alpha(0f)
                .setDuration(1100)
                .setStartDelay(Random.nextInt(80).toLong())
                .withEndAction { confettiContainer.removeView(dot) }
                .start()
        }
    }
}