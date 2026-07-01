package com.nvm.traplink.network

// lo que mandamos (Igual a LoginRequestDto.cs)
data class LoginRequestDto(
    val Email: String,
    val Password: String
)

// lo que gio nos responde en el JSON de arriba
data class LoginResponseDto(
    val mensaje: String,
    val token: String,
    val usuario: UsuarioInfo
)

data class UsuarioInfo(
    val usuarioId: Int,
    val nombre: String,
    val email: String,
    val rol: String
)