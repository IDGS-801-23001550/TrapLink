package com.nvm.traplink

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.nvm.traplink.network.LoginRequestDto
import com.nvm.traplink.network.TrapLinkApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class MainActivity : AppCompatActivity() {

    private val BASE_URL = "http://192.168.1.23:5202/"

    private val apiService: TrapLinkApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TrapLinkApiService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                showToast("Por favor, complete todos los campos")
            } else {
                // se ejecutaa la petición en un hilo secundario utilizando Corrutinas
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val requestDto = LoginRequestDto(Email = email, Password = password)
                        val response = apiService.login(requestDto)

                        withContext(Dispatchers.Main) {
                            if (response.isSuccessful && response.body() != null) {
                                val loginResponse = response.body()!!
                                showToast("${loginResponse.mensaje}: Bienvenido ${loginResponse.usuario.nombre}")

                                // aqui se puede guardar el token JWT
                                val token = loginResponse.token
                                val usuarioId = loginResponse.usuario.usuarioId

                                // redirigir mandando datos clave a la siguiente vista
                                val intent = Intent(this@MainActivity, Inicio::class.java).apply {
                                    putExtra("TOKEN", token)
                                    putExtra("USUARIO_ID", usuarioId)
                                }
                                startActivity(intent)
                                finish()
                            } else {
                                showToast("Credenciales incorrectas o error en el servidor")
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            showToast("Error de conexión: No se pudo conectar con el servidor")
                        }
                    }
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}