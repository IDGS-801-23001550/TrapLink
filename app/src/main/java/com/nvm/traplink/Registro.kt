package com.nvm.traplink

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.nvm.traplink.data.RegistroRequestDto
import com.nvm.traplink.data.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Registro : AppCompatActivity() {

    private lateinit var etNombre: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnRegistrar: Button
    private lateinit var btnRegresarLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_registro)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etNombre = findViewById(R.id.etNombre)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnRegistrar = findViewById(R.id.btnRegistrar)

        btnRegistrar.setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (nombre.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor, complete todos los campos", Toast.LENGTH_SHORT)
                    .show()
            } else {
                registroUsuario(nombre, email, password)
            }
        }
    }

    private fun registroUsuario(nom: String, em: String, pas: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = RegistroRequestDto(nombre = nom, email = em, password = pas)
                val response = RetrofitClient.authService.registrar(request)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val mensajeServer = response.body()!!.mensaje
                        Toast.makeText(this@Registro, mensajeServer, Toast.LENGTH_SHORT).show()

                        etNombre.setText("")
                        etEmail.setText("")
                        etPassword.setText("")

                        val intent = Intent(this@Registro, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)

                        finish()
                    } else {
                        Toast.makeText(this@Registro, "Error: El correo ya está registrado u ocurrió un problema", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@Registro, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}