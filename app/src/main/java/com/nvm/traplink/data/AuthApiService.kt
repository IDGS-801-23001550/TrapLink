package com.nvm.traplink.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Header
import retrofit2.http.PUT

interface AuthApiService {
    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequestDto
    ): Response<LoginResponseDto>

    @POST("auth/registrar")
    suspend fun registrar(
        @Body request: RegistroRequestDto
    ): Response<RegistroResponseDto>


    @PUT("Auth/perfil")
    suspend fun actualizarPerfil(
        @Header("Authorization") token: String,
        @Body perfilDto: ActualizarPerfilDto
    ): Response<MensajeRespuestaDto>
}