package com.nvm.traplink

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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

    // Lanzador nativo para solicitar permiso de cámara en tiempo de ejecución
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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_vincular)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Vincular componentes visuales
        etNumeroSerie = findViewById(R.id.etNumeroSerie)
        btnVincularManual = findViewById(R.id.btnVincularManual)
        btnEscanearQR = findViewById(R.id.btnEscanearQR)

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

        // --- NAVEGACIÓN INFERIOR ---
        btnNavDispositivos.setOnClickListener {
            val intent = Intent(this, Inicio::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }

        btnNavEventos.setOnClickListener {
            val intent = Intent(this, Eventos::class.java)
            startActivity(intent)
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
        // Configuramos el escáner de Google para que solo busque códigos QR e inicie de forma limpia
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

                    // Ejecuta la vinculación en automático al detectar el código
                    ejecutarVinculacionEnAzure(valorDetectado)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Escaneo cancelado o fallido: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun ejecutarVinculacionEnAzure(numeroSerie: String) {
        val sharedPreferences = getSharedPreferences("TrapLinkPrefs", MODE_PRIVATE)
        val tokenGuardado = sharedPreferences.getString("AUTH_TOKEN", "") ?: ""
        val usuarioIdGuardado = sharedPreferences.getInt("USUARIO_ID", -1)

        // DETECCIÓN RÁPIDA: Si te sale este Toast, el problema es que no se guardó el ID en el Login
        if (usuarioIdGuardado == -1) {
            Toast.makeText(this, "ERROR LOCAL: El USUARIO_ID es -1. Reinstala la app e inicia sesión de nuevo.", Toast.LENGTH_LONG).show()
            return
        }

        // Alerta para que veas en tu pantalla qué datos exactos van a viajar a Azure
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
                        // Esto nos leerá el mensaje interno si Ricardo puso un try/catch en Azure
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