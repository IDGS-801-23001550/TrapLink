package com.nvm.traplink

import android.content.Context
import android.content.Intent

object CerrarSesion {

    fun cerrarSesion(context: Context) {
        // 1. Limpiar SharedPreferences usando el prefijo unificado
        val sharedPreferences = context.getSharedPreferences("TrapLinkPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()

        // 2. Redirigir al Login (MainActivity) limpiando la pila
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}