package com.nvm.traplink

import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.nvm.traplink.data.ActualizarPerfilDto
import com.nvm.traplink.data.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditarPerfil : AppCompatActivity() {
    private lateinit var etNombre: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnGuardarCambios: Button

    // Instancia de SharedPreferences idéntica a la pantalla de Inicio
    private val prefs by lazy { getSharedPreferences("TrapLinkPrefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Aplicar el tema guardado ANTES de inflar vistas
        val isDark = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_editar_perfil)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Solo el bottom recibe padding — el top se queda en 0
            // para que el header siga extendiéndose bajo la barra de estado
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicialización de componentes
        etNombre = findViewById(R.id.etNombre)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnGuardarCambios = findViewById(R.id.btnGuardarCambios)

        // Configurar el icono del Toggle de Tema según el modo actual
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

        // Cargar datos actuales guardados de forma automática
        cargarDatosLocales()

        btnGuardarCambios.setOnClickListener {
            val nuevoNombre = etNombre.text.toString().trim()
            val nuevoEmail = etEmail.text.toString().trim()

            if (nuevoNombre.isEmpty()) {
                Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
            } else {
                actualizarPerfilServidor(nuevoNombre, nuevoEmail)
            }
        }

        // Vinculamos el contenedor del botón de usuario
        val btnLogout = findViewById<FrameLayout>(R.id.btnLogout)
        btnLogout.setOnClickListener { view ->
            val popup = androidx.appcompat.widget.PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.menu_usuario, popup.menu)

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_detalles -> {
                        Toast.makeText(this, "Ya te encuentras en tu perfil", Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.menu_cerrar_sesion -> {
                        CerrarSesion.cerrarSesion(this)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    /**
     * Recupera el Nombre y el Email directamente del almacenamiento persistente
     * donde se guardaron durante el inicio de sesión.
     */
    private fun cargarDatosLocales() {
        // Obtenemos los valores guardados. Si no existen, colocamos valores por defecto seguros.
        val nombreGuardado = prefs.getString("USER_NAME", "Usuario de TrapLink")
        val emailGuardado = prefs.getString("USER_EMAIL", "correo@guardado.com")

        // Los asignamos directamente a los EditTexts del XML
        etNombre.setText(nombreGuardado)
        etEmail.setText(emailGuardado)
    }

    private fun actualizarPerfilServidor(nombre: String, email: String) {
        btnGuardarCambios.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Recuperar el Token JWT almacenado en el login
                val token = prefs.getString("AUTH_TOKEN", "") ?: ""

                if (token.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        btnGuardarCambios.isEnabled = true
                        Toast.makeText(this@EditarPerfil, "Sesión no válida o expirada.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val tokenCompleto = "Bearer $token"

                // Construimos el DTO con el nombre, email y dejamos los campos de cliente opcionales como nulls
                val perfilDto = ActualizarPerfilDto(
                    nombre = nombre,
                    email = email,
                    tokenPushFCM = null,
                    empresa = null,
                    telefono = null,
                    direccion = null
                )

                // Llamar al endpoint PUT "perfil" del backend
                val response = RetrofitClient.authService.actualizarPerfil(tokenCompleto, perfilDto)

                withContext(Dispatchers.Main) {
                    btnGuardarCambios.isEnabled = true

                    if (response.isSuccessful && response.body() != null) {
                        val mensajeApi = response.body()!!.mensaje
                        Toast.makeText(this@EditarPerfil, mensajeApi, Toast.LENGTH_LONG).show()

                        // Actualizar localmente SharedPreferences para que persista el nuevo nombre en toda la app
                        prefs.edit().putString("USER_NAME", nombre).apply()

                        finish() // Regresa a la pantalla anterior tras guardar con éxito
                    } else {
                        val errorBody = response.errorBody()?.string()
                        val mensajeError = when (response.code()) {
                            400 -> "El correo electrónico ya se encuentra registrado por otro usuario o el formato es incorrecto."
                            401 -> "Sesión no autorizada."
                            else -> "No se pudo actualizar el perfil (${response.code()})"
                        }
                        Toast.makeText(this@EditarPerfil, mensajeError, Toast.LENGTH_LONG).show()
                        android.util.Log.e("API_ERROR", "Código: ${response.code()} | Body: $errorBody")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnGuardarCambios.isEnabled = true
                    Toast.makeText(this@EditarPerfil, "Error de conexión: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}