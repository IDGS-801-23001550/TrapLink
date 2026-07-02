package com.nvm.traplink.data

data class LoginRequestDto(
    val email: String,
    val password: String
)

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

data class RegistroRequestDto(
    val nombre: String,
    val email: String,
    val rol: String = "cliente",
    val password: String
)

data class RegistroResponseDto(
    val mensaje: String
)