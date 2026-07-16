package com.nvm.traplink

import android.content.Context
import android.content.Intent

class CerrarSesion {

    fun cerrarSesion(context: Context) {
        // Limpiar SharedPreferences usando el contexto de la pantalla que lo llama
        val sharedPreferences = context.getSharedPreferences("TrapLinkPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()

        // Redirigir al Login
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }
}