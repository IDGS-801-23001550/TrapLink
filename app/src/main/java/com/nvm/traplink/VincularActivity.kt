package com.nvm.traplink

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.nvm.traplink.data.RetrofitClient
import com.nvm.traplink.data.VincularRequestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VincularActivity : AppCompatActivity() {

    private lateinit var etNumeroSerie: EditText
    private lateinit var btnVincularManual: Button
    private lateinit var btnEscanearQR: Button
    private var isDarkThemeActive: Boolean = false

    private val prefs by lazy { getSharedPreferences("TrapLinkPrefs", MODE_PRIVATE) }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            iniciarEscaneoQR()
        } else {
            Toast.makeText(this, "Se requiere el permiso de cámara para escanear códigos QR", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Validar e Inyectar tema activo antes de pintar la UI
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
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 2. Controladores del Toggle del Tema e Icono
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

        // Vincular componentes visuales del formulario
        etNumeroSerie = findViewById(R.id.etNumeroSerie)
        btnVincularManual = findViewById(R.id.btnVincularManual)
        btnEscanearQR = findViewById(R.id.btnEscanearQR)

        // Vincular referencias directas de la barra unificada
        val btnNavDispositivos = findViewById<TextView>(R.id.btnNavDispositivos)
        val btnNavEventos = findViewById<TextView>(R.id.btnNavEventos)

        // Acción: Botón Vincular Manual
        btnVincularManual.setOnClickListener {
            val serieText = etNumeroSerie.text.toString().trim()
            if (serieText.isEmpty()) {
                etNumeroSerie.error = "Ingresa un número de serie válido"
                return@setOnClickListener
            }
            ejecutarVinculacionEnAzure(serieText)
        }

        // Acción: Botón Escanear QR
        btnEscanearQR.setOnClickListener {
            verificarPermisosYEscandear()
        }

        // --- NAVEGACIÓN INFERIOR SIN TRANSICIÓN DE SALTO ---
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
    }

    private fun verificarPermisosYEscandear() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            iniciarEscaneoQR()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun iniciarEscaneoQR() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(this, options)

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val valorDetectado = barcode.rawValue
                if (!valorDetectado.isNullOrEmpty()) {
                    etNumeroSerie.setText(valorDetectado)
                    Toast.makeText(this, "Código detectado con éxito", Toast.LENGTH_SHORT).show()
                    ejecutarVinculacionEnAzure(valorDetectado)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Escaneo cancelado o fallido: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun ejecutarVinculacionEnAzure(numeroSerie: String) {
        val tokenGuardado = prefs.getString("AUTH_TOKEN", "") ?: ""
        val usuarioIdGuardado = prefs.getInt("USUARIO_ID", -1)

        if (usuarioIdGuardado == -1) {
            Toast.makeText(this, "ERROR LOCAL: El USUARIO_ID es -1. Reinstala la app e inicia sesión de nuevo.", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Enviando: Serie=$numeroSerie, ID=$usuarioIdGuardado", Toast.LENGTH_LONG).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val requestDto = VincularRequestDto(
                    numeroSerie = numeroSerie,
                    usuarioId = usuarioIdGuardado
                )

                val response = RetrofitClient.trapLinkService.vincularDispositivo(requestDto)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@VincularActivity, "¡Vinculado con éxito!", Toast.LENGTH_LONG).show()
                        etNumeroSerie.text.clear()
                        startActivity(Intent(this@VincularActivity, Inicio::class.java))
                        finish()
                    } else {
                        val codigoError = response.code()
                        val mensajeError = response.errorBody()?.string() ?: "Error de servidor"
                        Toast.makeText(this@VincularActivity, "Error $codigoError: $mensajeError", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VincularActivity, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}