package com.nvm.traplink.data

// --- DTOs para AnalisisController ---
data class ResumenKpisResponseDto(
    val totalDispositivos: Int,
    val totalEventos: Int,
    val capturasReales: Int,
    val falsosPositivos: Int,
    val eventosSinRevisar: Int
)

data class SlaTransmisionResponseDto(
    val dispositivoID: Int,
    val totalPaquetes: Int,
    val exitosos: Int,
    val pctExito: Double
)

data class FalsosPositivosResponseDto(
    val dispositivoID: Int,
    val totalEventos: Int,
    val falsosPositivos: Int,
    val pctFalsosPositivos: Double,
    val capturasReales: Int,
    val eventosSinRevisar: Int,
    val nombreDispositivo: String
)

// --- DTOs para PrediccionesController ---
data class PrediccionResponseDto(
    val prediccionID: Int,
    val modeloID: Int,
    val dispositivoID: Int,
    val eventoID: Int?,
    val fechaHora: String,
    val clasePredicha: String?,
    val probabilidad: Double?,
    val horasBateriaEstimada: Double?,
    val fechaAgotamientoEstimada: String?
)