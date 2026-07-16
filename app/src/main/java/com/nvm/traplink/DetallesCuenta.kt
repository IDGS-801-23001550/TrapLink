package com.nvm.traplink

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText

class DetallesCuenta : AppCompatActivity() {

    private lateinit var etNombre: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnGuardarCambios: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_detalles_cuenta)

        // Inicialización de componentes respetando el ciclo de vida
        etNombre = findViewById(R.id.etNombre)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnGuardarCambios = findViewById(R.id.btnGuardarCambios)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Cargar datos actuales del almacenamiento local
        cargarDatosLocales()

        btnGuardarCambios.setOnClickListener {
            val nuevoNombre = etNombre.text.toString().trim()
            val nuevaPassword = etPassword.text.toString().trim()

            if (nuevoNombre.isEmpty()) {
                Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
            } else {
                actualizarPerfilServidor(nuevoNombre, nuevaPassword)
            }
        }
    }

    private fun cargarDatosLocales() {
        val sharedPreferences = getSharedPreferences("TrapLinkPrefs", MODE_PRIVATE)

        // Recuperamos el ID que guardaste al iniciar sesión para futuras peticiones a la API
        val usuarioId = sharedPreferences.getInt("USUARIO_ID", -1)

        // Tip: Si deseas pintar el nombre o el email actual aquí, puedes guardarlos
        // en SharedPreferences desde el Login de la misma forma que guardaste el TOKEN.
        etNombre.setText("Usuario de TrapLink")
        etEmail.setText("correo@guardado.com")
    }

    private fun actualizarPerfilServidor(nombre: String, contrasenia: String) {
        // TODO: Aquí ejecutas la corrutina (lifecycleScope.launch(Dispatchers.IO))
        // llamando al servicio de Retrofit correspondiente para actualizar los datos en Azure.

        Toast.makeText(this, "Cambios guardados con éxito", Toast.LENGTH_SHORT).show()
        finish() // Regresa a la pantalla anterior tras guardar
    }
}