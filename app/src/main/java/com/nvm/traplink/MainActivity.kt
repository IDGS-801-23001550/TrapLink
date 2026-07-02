package com.nvm.traplink

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.nvm.traplink.data.LoginRequestDto
import com.nvm.traplink.data.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegistrar = findViewById<Button>(R.id.btnRegistrar)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                mostrarToast(this, "Por favor, complete todos los campos")
            } else {
                // Ejecutamos la petición dentro de una corrutina en segundo plano
                ejecutarLogin(email, password)
            }
        }

        btnRegistrar.setOnClickListener {
            val intent = Intent(this, Registro::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }
    }

    private fun ejecutarLogin(email: String, contrasenia: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = LoginRequestDto(email, contrasenia)
                val response = RetrofitClient.authService.login(request)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val loginResponse = response.body()!!

                        val token = loginResponse.token
                        val nombreUsuario = loginResponse.usuario.nombre

                        mostrarToast(this@MainActivity, "¡Bienvenido $nombreUsuario!")

                        // [CORRECCIÓN]: Guardamos el token en las SharedPreferences del celular de manera local
                        val sharedPreferences = getSharedPreferences("TrapLinkPrefs", MODE_PRIVATE)
                        sharedPreferences.edit().putString("AUTH_TOKEN", token).apply()

                        // Redirigimos a la pantalla de Inicio
                        val intent = Intent(this@MainActivity, Inicio::class.java).apply {
                            putExtra("EXTRA_USUARIO_ID", loginResponse.usuario.usuarioId)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        mostrarToast(this@MainActivity, "Credenciales incorrectas")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mostrarToast(this@MainActivity, "Error de red: ${e.message}")
                }
            }
        }
    }

    private fun mostrarToast(context: AppCompatActivity, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}