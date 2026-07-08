package com.nvm.traplink.data

import com.google.gson.annotations.SerializedName

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


data class ConfirmarEventoDto(
    val esCapturaReal: Boolean
)

data class EventoPendienteResponseDto(
    @SerializedName("eventoID", alternate = ["eventoId", "EventoID"])
    val eventoID: Long,

    @SerializedName("dispositivoID", alternate = ["dispositivoId", "DispositivoID"])
    val dispositivoID: Int,

    val fechaHora: String,
    val duracionPulsoMs: Double?,
    val nivelVibracion: Double?,
    val voltaje: Double?
)