package com.nvm.traplink.data

//Dto´s para login
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

//Dto´s para registrar
//Lo que envia
data class RegistroRequestDto(
    val nombre: String,
    val email: String,
    val rol: String = "cliente",
    val password: String
)

//Lo que responde la API
data class RegistroResponseDto(
    val mensaje: String
)