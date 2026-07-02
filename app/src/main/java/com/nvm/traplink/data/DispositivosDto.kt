package com.nvm.traplink.data

data class VincularRequestDto(
    val numeroSerie: String,
    val usuarioId: Int
)

data class MisDispositivosResponseDto(
    val dispositivoID: Int,
    val numeroSerie: String,
    val estado: String,
    val ultimoContacto: String?,
    val enLinea: Boolean
)

data class RequestComandoDto(
    val targetId: String,
    val comando: String
)