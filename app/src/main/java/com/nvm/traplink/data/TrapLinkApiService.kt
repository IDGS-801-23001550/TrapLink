package com.nvm.traplink.data

import retrofit2.Response
import retrofit2.http.*

interface TrapLinkApiService {

    // --- ENDPOINTS DE DISPOSITIVOS ---
    @PUT("Dispositivos/vincular")
    suspend fun vincularDispositivo(
        @Body request: VincularRequestDto
    ): Response<Map<String, String>>

    @GET("Dispositivos/mis-dispositivos")
    suspend fun getMisDispositivos(
        @Header("Authorization") token: String
    ): Response<List<MisDispositivosResponseDto>>

    @POST("Dispositivos/enviar-comando")
    suspend fun enviarComando(
        @Header("Authorization") token: String,
        @Body request: RequestComandoDto
    ): Response<Map<String, String>>

    // --- ENDPOINTS DE ANÁLISIS ---
    @GET("Analisis/resumen")
    suspend fun getResumenKpis(
        @Header("Authorization") token: String
    ): Response<ResumenKpisResponseDto>

    @GET("Analisis/sla-transmision")
    suspend fun getSlaTransmision(): Response<List<SlaTransmisionResponseDto>>

    @GET("Analisis/falsos-positivos")
    suspend fun getFalsosPositivos(): Response<List<FalsosPositivosResponseDto>>

    // --- ENDPOINTS DE PREDICCIONES ---
    @GET("Predicciones/dispositivo/{dispositivoId}")
    suspend fun getPrediccionesPorDispositivo(
        @Header("Authorization") token: String,
        @Path("dispositivoId") dispositivoId: Int
    ): Response<List<PrediccionResponseDto>>

    @GET("Predicciones/bateria/alertas")
    suspend fun getAlertasBateria(
        @Query("umbralHoras") umbralHoras: Double = 48.0
    ): Response<List<PrediccionResponseDto>>
}