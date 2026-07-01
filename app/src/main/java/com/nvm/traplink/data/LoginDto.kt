package com.nvm.traplink.data

// Lo que le envías a la API
data class LoginRequestDto(
    val email: String,
    val password: String
)

// Lo que la API te responde (debe coincidir con las propiedades del JSON)
data class LoginResponseDto(
    val mensaje: String,
    val token: String,
    val usuario: UsuarioDto
)

data class UsuarioDto(
    val usuarioId: Int,
    val nombre: String,
    val email: String,
    val rol: String
)